package com.ofit.transport

import android.content.Context
import com.ofit.protocol.ConnectionState
import com.ofit.protocol.Transport
import com.ofit.protocol.Wire
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow

/**
 * The Bluetooth implementation of the protocol library's transport contract.
 *
 * The protocol library never sees an Android type; it only sees bytes and a link state.
 */
class BleTransport(private val context: Context) : Transport {

    private val client = GattClient(context)

    /** The link state. */
    override val connectionState: StateFlow<ConnectionState> = client.connectionState

    /** Frames arriving from the machine. */
    override val notifications: Flow<ByteArray> = client.notifications.map { Wire.unwrap(it) }

    /** Link errors worth showing to the user. */
    val errors: Flow<String> = client.errors

    /** Send a command frame to the machine, inside its transport header. */
    override suspend fun write(payload: ByteArray) = client.write(Wire.payload(payload))

    /** Open the link to a treadmill. */
    suspend fun connect(address: String) = client.connect(address)

    /** The transport contract's parameterless connect is not usable without an address. */
    override suspend fun connect() {
        error("use connect(address) with a scanned treadmill")
    }

    /** Close the link. */
    override suspend fun disconnect() = client.disconnect()
}
