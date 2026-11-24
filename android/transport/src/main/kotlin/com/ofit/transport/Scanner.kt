package com.ofit.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.ofit.protocol.Gatt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Finds the treadmill.
 *
 * ### Why this scans twice, and without a filter
 *
 * The first version of this scanner filtered the Bluetooth Low Energy scan on the console service
 * UUID, and it never found the treadmill. Two things fixed that:
 *
 *  - **it filters by name, not by service UUID.** A device is an iFit console when its uppercased
 *    name matches a list of console names (Gatt.looksLikeConsole). A console that does not put its service UUID in the advertisement
 *    packet is invisible to a UUID filter, and a filtered scan never even sees its name.
 *  - **it also does classic Bluetooth discovery** (`startDiscovery()`), which finds the console
 *    as well as the Low Energy scan does.
 *
 * So the scan here is unfiltered, both kinds of discovery run at once, and every device that is
 * seen is reported with its name. The name decides whether a device is *marked* as a console, not
 * whether it is *shown*: the user can still pick a machine whose name is not in the list, because
 * a wrong list is a far better failure than an invisible treadmill.
 *
 * ### No pairing
 *
 * The app never pairs (bonds) with the console. An HCI capture of the vendor application on the
 * user's I_TL console (2026-11-23) shows it simply connects and talks; asking Android to pair made
 * the console hang up. The code the console shows (`32F6`) is not needed.
 */
class Scanner(private val context: Context) {

    /** A treadmill seen during a scan. */
    data class Device(
        val name: String,
        val address: String,
        val rssi: Int,
        /** Whether the name looks like an iFit console. */
        val isConsole: Boolean = false,
    )

    /** Whether the device has the Bluetooth permissions it needs. */
    fun hasPermission(): Boolean {
        val granted = context.packageManager.checkPermission(
            android.Manifest.permission.BLUETOOTH_SCAN,
            context.packageName,
        ) == PackageManager.PERMISSION_GRANTED
        return granted
    }

    /** Whether Bluetooth is switched on and available. */
    fun isBluetoothAvailable(): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    /**
     * Scan for the treadmill.
     *
     * The flow stays open until the collector stops, which cancels the scan and stops the classic
     * discovery.
     */
    fun scan(): Flow<Device> {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return flowOf()
        if (!adapter.isEnabled) return flowOf()

        val scanner = adapter.bluetoothLeScanner

        // No filter on purpose. A filter on the console service UUID hides any console that does
        // not put that UUID in its advertisement, and its name is the only thing that identifies it.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return callbackFlow {
            val callback = object : ScanCallback() {
                // The scan itself is only started once the permission has been granted, and both
                // name readings are guarded, so a refused permission cannot take the app down.
                @SuppressLint("MissingPermission")
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    // Reading the name needs the connect permission, which the user may have
                    // refused while allowing the scan. A refused permission must not take the
                    // app down.
                    val name = runCatching { device.name }.getOrNull()
                        ?: runCatching { result.scanRecord?.deviceName }.getOrNull()
                        ?: UNNAMED
                    trySend(deviceOf(name, device.address, result.rssi))
                }
            }

            try {
                scanner?.startScan(null, settings, callback)
            } catch (error: SecurityException) {
                close(error)
                return@callbackFlow
            }

            // The console is also discoverable the classic way, and other apps use
            // that route as well as the Low Energy scan.
            val foundReceiver = object : BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != BluetoothDevice.ACTION_FOUND) return
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    if (device == null) return
                    val name = runCatching { device.name }.getOrNull()
                        ?: intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        ?: UNNAMED
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
                    trySend(deviceOf(name, device.address, rssi))
                }
            }
            val foundFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    context.registerReceiver(
                        foundReceiver,
                        foundFilter,
                        Context.RECEIVER_NOT_EXPORTED,
                    )
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(foundReceiver, foundFilter)
                }
                adapter.startDiscovery()
            } catch (error: SecurityException) {
                Log.w("ofit", "could not start the classic discovery", error)
            }

            awaitClose {
                try {
                    scanner?.stopScan(callback)
                } catch (error: SecurityException) {
                    Log.w("ofit", "could not stop the scan", error)
                }
                try {
                    adapter.cancelDiscovery()
                } catch (error: SecurityException) {
                    Log.w("ofit", "could not stop the classic discovery", error)
                }
                try {
                    context.unregisterReceiver(foundReceiver)
                } catch (error: IllegalArgumentException) {
                    Log.w("ofit", "the receiver was already gone", error)
                }
            }
        }
    }

    private fun deviceOf(name: String, address: String, rssi: Int) = Device(
        name = name,
        address = address,
        rssi = rssi,
        isConsole = Gatt.looksLikeConsole(name),
    )

    /**
     * The adapter's own name, for diagnostics.
     *
     * Reading the name needs the connect permission on Android 12 and above, so a refused
     * permission must not take the app down.
     */
    fun adapterName(): String =
        try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.name ?: "unknown"
        } catch (error: SecurityException) {
            "unknown"
        }

    companion object {
        /**
         * What a device is called when it advertises no name.
         *
         * It must never be the treadmill's own name: most devices in a room advertise no name, and
         * calling every one of them "I_TL" would offer a stranger's phone as an iFit console.
         */
        const val UNNAMED: String = "(no name)"
    }
}
