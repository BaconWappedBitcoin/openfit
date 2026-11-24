package com.ofit.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.ofit.protocol.HeartRateMeasurement
import com.ofit.protocol.HeartRateProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * A Bluetooth heart-rate strap or watch (spec 002, section 3b).
 *
 * It is a second, fully separate GATT link: its own [BluetoothGatt], its own callback thread and no
 * shared queue, so strap traffic can never delay a treadmill command (H4). It only ever reads, it
 * never pairs, and losing it never touches the workout.
 */
class HeartRateMonitor(private val context: Context) {

    /** A device seen during a scan; [isHeartRate] when it advertises the service or is named like a strap. */
    data class Device(val name: String, val address: String, val rssi: Int, val isHeartRate: Boolean = true)

    /** The link to the chosen strap. */
    enum class LinkState { IDLE, CONNECTING, CONNECTED, LOST }

    /** The latest reading, with when it arrived (elapsedRealtime). */
    data class Reading(val measurement: HeartRateMeasurement, val atMillis: Long)

    private val _link = MutableStateFlow(LinkState.IDLE)
    val link: StateFlow<LinkState> = _link.asStateFlow()

    private val _reading = MutableStateFlow<Reading?>(null)
    val reading: StateFlow<Reading?> = _reading.asStateFlow()

    private var gatt: BluetoothGatt? = null

    private val adapter get() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Scan for devices that advertise the Heart Rate service only (H1). */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<Device> {
        val adapter = adapter ?: return flowOf()
        if (!adapter.isEnabled) return flowOf()
        val scanner = adapter.bluetoothLeScanner ?: return flowOf()
        // No hardware filter: a strap seen live (2026-11-23) never showed up with a service-UUID
        // filter, because many straps and watches leave the UUID out of the advertisement or put it
        // only in the scan response. Every named device is reported; heart-rate ones are marked.
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val hrService = ParcelUuid(UUID.fromString(HeartRateProfile.SERVICE))
        return callbackFlow {
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val advertisesHr = result.scanRecord?.serviceUuids?.contains(hrService) == true
                    val name = runCatching { result.device.name }.getOrNull()
                        ?: result.scanRecord?.deviceName
                    if (name == null && !advertisesHr) return
                    val shown = name ?: "Heart-rate monitor"
                    trySend(Device(shown, result.device.address, result.rssi, advertisesHr || looksLikeStrap(shown)))
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "strap scan failed: $errorCode")
                    close()
                }
            }
            try {
                scanner.startScan(null, settings, callback)
            } catch (error: SecurityException) {
                close(error)
                return@callbackFlow
            }
            awaitClose { runCatching { scanner.stopScan(callback) } }
        }
    }

    /**
     * Connect to a strap and keep the link. autoConnect = true lets Android reconnect by itself when
     * the strap comes back into range or gets skin contact again, with no polling from us.
     */
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        disconnect()
        val device: BluetoothDevice = adapter?.getRemoteDevice(address) ?: return
        _link.value = LinkState.CONNECTING
        gatt = try {
            device.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (error: SecurityException) {
            Log.w(TAG, "no permission to connect the strap", error)
            _link.value = LinkState.IDLE
            null
        }
    }

    /** Drop the link. */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        _link.value = LinkState.IDLE
        _reading.value = null
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "strap connected, discovering")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "strap lost (status $status); Android reconnects when it is back")
                    if (this@HeartRateMonitor.gatt === gatt) _link.value = LinkState.LOST
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt.getService(UUID.fromString(HeartRateProfile.SERVICE))
                ?.getCharacteristic(UUID.fromString(HeartRateProfile.MEASUREMENT))
            if (characteristic == null) {
                Log.w(TAG, "the device has no heart-rate measurement")
                return
            }
            gatt.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(UUID.fromString(HeartRateProfile.CCCD)) ?: return
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(cccd, enable)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = enable
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) _link.value = LinkState.CONNECTED
        }

        // Android 13 and newer.
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = handle(value)

        // Android 12 and older.
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION")
                handle(characteristic.value ?: return)
            }
        }
    }

    private fun handle(value: ByteArray) {
        val measurement = HeartRateMeasurement.parse(value) ?: return
        _link.value = LinkState.CONNECTED
        if (!measurement.usable) return
        _reading.value = Reading(measurement, SystemClock.elapsedRealtime())
    }

    private companion object {
        const val TAG = "ofit-hr"

        private val STRAP_NAMES = Regex(
            "polar|h10|h9|h7|oh1|verity|hrm|tickr|wahoo|garmin|coros|suunto|magene|heart|\\bhr\\b|cardio|scosche|rhythm|amazfit|forerunner|fenix|apple watch|galaxy watch|whoop",
            RegexOption.IGNORE_CASE,
        )

        fun looksLikeStrap(name: String): Boolean = STRAP_NAMES.containsMatchIn(name)
    }
}
