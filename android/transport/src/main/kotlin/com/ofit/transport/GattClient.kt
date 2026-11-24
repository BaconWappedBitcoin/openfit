package com.ofit.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import com.ofit.protocol.ConnectionState
import com.ofit.protocol.Framing
import com.ofit.protocol.Gatt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * The Bluetooth link to the treadmill.
 *
 * Uses the platform Bluetooth APIs directly, per constitution Article VIII: no wrapper library.
 *
 * Two behaviours from the reference hardware are handled here:
 *  - the first control write after connecting can fail with a GATT error; it is retried;
 *  - a notification may carry only part of a chunk group, so groups are reassembled here before
 *    they reach the protocol layer.
 */
class GattClient(private val context: Context) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    private val _notifications = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** The link state. */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Frames arriving from the machine. */
    val notifications: Flow<ByteArray> = _notifications.asSharedFlow()

    /** Link errors worth showing to the user. */
    val errors: Flow<String> = _errors.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var connected = CompletableDeferred<Unit>()
    private var servicesReady = CompletableDeferred<Unit>()

    /** Reassembles the chunk groups the machine sends. */
    private val chunkBuffer = ArrayDeque<ByteArray>()

    /**
     * Connect to a treadmill and wait for its console service to be ready.
     *
     * @throws SecurityException when the Bluetooth permissions have not been granted
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(address: String) {
        _connectionState.value = ConnectionState.CONNECTING
        connected = CompletableDeferred()
        servicesReady = CompletableDeferred()

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: run {
            _connectionState.value = ConnectionState.FAILED
            error("this device has no Bluetooth adapter")
        }
        val device: BluetoothDevice = adapter.getRemoteDevice(address)

        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

        try {
            connected.await()
        } catch (error: IllegalStateException) {
            // Free the client slot: Android only has a few, and a leaked one blocks later connects.
            runCatching { gatt?.close() }
            gatt = null
            _connectionState.value = ConnectionState.FAILED
            throw error
        }
        // Ready means notifications are confirmed on, not just requested: a command sent before
        // that loses its reply (seen live, 2026-11-23: an intermittent handshake timeout on
        // reconnect). The vendor application also waits about 200 ms after subscribing.
        withTimeoutOrNull(READY_TIMEOUT_MILLIS) { servicesReady.await() }
        delay(NOTIFY_SETTLE_MILLIS)
        if (_connectionState.value == ConnectionState.DISCONNECTED || writeCharacteristic == null) {
            runCatching { gatt?.close() }
            gatt = null
            _connectionState.value = ConnectionState.FAILED
            error("the treadmill's console service did not come up")
        }
        _connectionState.value = ConnectionState.CONNECTED
    }

    /** Close the link. */
    @SuppressLint("MissingPermission")
    suspend fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        gatt?.close()
        gatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Send a payload to the machine, split into 20 byte writes.
     */
    @SuppressLint("MissingPermission")
    suspend fun write(payload: ByteArray) {
        val characteristic = writeCharacteristic ?: error("the console service is not ready")
        val gatt = this.gatt ?: error("not connected")

        val chunks = Framing.encode(payload)
        Log.i(TAG, "write -> ${hex(payload)} in ${chunks.size} chunk(s), props=0x${characteristic.properties.toString(16)}")
        for (chunk in chunks) {
            var attempts = 0
            var sent = false
            while (!sent && attempts < WRITE_ATTEMPTS) {
                attempts++
                sent = writeChunk(gatt, characteristic, chunk)
                if (!sent) {
                    // The machine sometimes rejects the first write after connecting. Back off and
                    // try again rather than failing the command.
                    delay(WRITE_RETRY_DELAY_MILLIS)
                }
            }
            if (!sent) {
                _errors.tryEmit("The treadmill did not accept a write.")
                error("the treadmill did not accept a write")
            }
            delay(WRITE_SETTLE_MILLIS)
        }
    }

    /**
     * Write one chunk to the characteristic.
     *
     * Android 13 replaced the old call with one that takes the value and the write type
     * separately, and the old one is gone on newer releases. The write type is
     * `WRITE_TYPE_DEFAULT`, which is what the characteristic carried before: the third argument
     * of the new call is the write type, not the length of the data.
     *
     * The connect permission is required, and is asked for before the treadmill is connected.
     */
    @SuppressLint("MissingPermission")
    private fun writeChunk(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        chunk: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                chunk,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = chunk
            gatt.writeCharacteristic(characteristic)
        }

private fun hex(b: ByteArray?): String = b?.joinToString("") { "%02x".format(it) } ?: "null"

    private val callback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "connection state: status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!connected.isCompleted) connected.complete(Unit)
                    g.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    // A link that drops before it ever came up is a failed connect, not a connect
                    // (status 147 = timeout, seen live 2026-11-23 with the console asleep).
                    if (!connected.isCompleted) {
                        connected.completeExceptionally(
                            IllegalStateException("the treadmill did not answer (Bluetooth status $status). Is it switched on?"),
                        )
                    }
                    if (!servicesReady.isCompleted) servicesReady.complete(Unit)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.i(TAG, "services discovered: status=$status")
            g.services.forEach { svc ->
                Log.i(TAG, "  service ${svc.uuid}")
                svc.characteristics.forEach { ch ->
                    Log.i(TAG, "    char ${ch.uuid} props=0x${ch.properties.toString(16)}")
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _errors.tryEmit("The treadmill's Bluetooth services could not be read.")
                if (!servicesReady.isCompleted) servicesReady.complete(Unit)
                return
            }

            val service = g.getService(UUID.fromString(Gatt.CONSOLE_SERVICE))
            if (service == null) {
                _errors.tryEmit("This device does not look like an iFit treadmill.")
                if (!servicesReady.isCompleted) servicesReady.complete(Unit)
                return
            }

            writeCharacteristic = service.getCharacteristic(UUID.fromString(Gatt.HOST_WRITE))
            notifyCharacteristic = service.getCharacteristic(UUID.fromString(Gatt.MACHINE_NOTIFY))

            if (writeCharacteristic == null) {
                _errors.tryEmit("The treadmill is missing its command characteristic.")
                if (!servicesReady.isCompleted) servicesReady.complete(Unit)
                return
            }

            val notify = notifyCharacteristic
            if (notify != null) {
                // Ready once the subscription is confirmed, in onDescriptorWrite.
                enableNotifications(g, notify)
            } else if (!servicesReady.isCompleted) {
                servicesReady.complete(Unit)
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            Log.i(TAG, "descriptor write: ${descriptor.uuid} status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _errors.tryEmit("Subscribed to the treadmill's notifications.")
            }
            if (!servicesReady.isCompleted) servicesReady.complete(Unit)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.i(TAG, "characteristic write result: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _errors.tryEmit("The treadmill rejected a write ($status).")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            Log.i(TAG, "notify <- ${hex(value)}")
            deliver(value)
        }

        @Deprecated("Superseded by the value based callback on API 33 and above")
        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            characteristic.value?.let {
                Log.i(TAG, "notify <- ${hex(it)}")
                deliver(it)
            }
        }
    }

    /** Turn on notifications for the machine's state characteristic. */
    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!g.setCharacteristicNotification(characteristic, true)) return
        } else {
            @Suppress("DEPRECATION")
            g.setCharacteristicNotification(characteristic, true)
        }

        val cccd = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID)) ?: run {
            if (!servicesReady.isCompleted) servicesReady.complete(Unit)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    /**
     * Deliver one notification, reassembling chunk groups.
     */
    private fun deliver(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (bytes[0] == Framing.HEADER_MARKER) {
            // A new reply starts; anything half-collected before it is abandoned.
            chunkBuffer.clear()
            chunkBuffer.addLast(bytes)
            return
        }
        if (chunkBuffer.isEmpty()) return // a stray chunk with no header
        chunkBuffer.addLast(bytes)
        // Replies span several notifications: wait for the end marker before decoding.
        if (bytes[0] != Framing.END_MARKER) return

        val group = ArrayList(chunkBuffer)
        chunkBuffer.clear()
        val payload = runCatching { Framing.decode(group) }
            .onFailure { Log.i(TAG, "could not reassemble a reply: ${it.message}") }
            .getOrNull() ?: return
        _notifications.tryEmit(payload)
    }

    private companion object {
        const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        const val WRITE_ATTEMPTS = 3
        const val WRITE_RETRY_DELAY_MILLIS = 120L
        const val WRITE_SETTLE_MILLIS = 40L
        const val READY_TIMEOUT_MILLIS = 5_000L
        const val NOTIFY_SETTLE_MILLIS = 200L
    }
}

private const val TAG = "ofit-ble"
