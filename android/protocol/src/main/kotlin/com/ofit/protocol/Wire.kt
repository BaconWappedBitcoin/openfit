package com.ofit.protocol

/**
 * The wire payload: what actually goes into a BLE write.
 *
 * The vendor application's byte log records the FitPro command layer. On the wire, each command is
 * additionally wrapped in a transport header before it is split into BLE chunks.
 *
 * ```text
 * command layer : [device][len][cmd][content...][checksum]
 * wire payload  : 02 04 02 <len> <command layer>
 * ```
 *
 * The transport header is described by the MIT-licensed decoder in `DavisGoglin/treadmill`. It is
 * the one part of the framing that has not been confirmed against a capture from the user's own
 * machine, so it lives in one place and is called out here.
 *
 * Evidence: research.md sections 4 and 5.
 */
object Wire {

    /** The transport header that precedes a command frame. */
    val HEADER: ByteArray = byteArrayOf(0x02, 0x04, 0x02)

    /** Wrap a command frame into its wire payload. */
    fun payload(commandFrame: ByteArray): ByteArray =
        HEADER + byteArrayOf(commandFrame.size.toByte()) + commandFrame

    /** The transport header on what the machine sends back. */
    val INBOUND_HEADER: ByteArray = byteArrayOf(0x01, 0x04, 0x02)

    /**
     * Strip the transport header from an inbound payload, leaving the command frame. A payload
     * without it (such as the machine's 4-byte refusal `01 00 00 00`) is returned unchanged.
     */
    fun unwrap(wirePayload: ByteArray): ByteArray {
        if (wirePayload.size < 4) return wirePayload
        val header = wirePayload.copyOfRange(0, 3)
        if (!header.contentEquals(INBOUND_HEADER) && !header.contentEquals(HEADER)) return wirePayload
        return wirePayload.copyOfRange(4, wirePayload.size)
    }

    /** Split a wire payload into BLE-sized chunks. */
    fun chunks(wirePayload: ByteArray): List<ByteArray> = Framing.encode(wirePayload)

    /** Build the full wire payload for a command, and its chunks. */
    fun build(command: Int, content: ByteArray, device: Int = EquipmentId.TREADMILL): Pair<ByteArray, List<ByteArray>> {
        val frame = Command.encode(device, command, content)
        val payload = payload(frame)
        return payload to chunks(payload)
    }
}
