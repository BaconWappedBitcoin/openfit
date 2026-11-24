package com.ofit.protocol

/**
 * BLE chunk framing.
 *
 * A command is split across 20-byte ATT writes. The first chunk carries a header; the payload
 * follows in numbered chunks and the last of them carries the end marker.
 *
 * ```text
 * header chunk : FE 02 LL NN      LL = total payload length, NN = number of payload chunks
 * payload chunk: II LL <data>     II = chunk index, or FF on the final chunk
 * ```
 *
 * Evidence: research.md sections 3.2 and 5. The exact byte layout of the header chunk is the one
 * part of the framing that has not yet been confirmed against a capture from the user's own
 * machine; see `docs/protocol-notes.md` for how to confirm it.
 */
object Framing {

    /** The ATT transfer unit the machine negotiates. */
    const val MTU: Int = 20

    /** Payload bytes carried by one chunk, after the two framing bytes. */
    const val PAYLOAD_PER_CHUNK: Int = MTU - 2

    /** Header chunk marker. */
    const val HEADER_MARKER: Byte = 0xFE.toByte()

    /** Final chunk marker. */
    const val END_MARKER: Byte = 0xFF.toByte()

    /** Fixed byte in the header chunk. */
    private const val HEADER_VERSION: Byte = 0x02

    /**
     * Split a command payload into BLE-sized chunks.
     */
    fun encode(payload: ByteArray): List<ByteArray> {
        val chunkCount = if (payload.isEmpty()) 1 else (payload.size + PAYLOAD_PER_CHUNK - 1) / PAYLOAD_PER_CHUNK
        val chunks = ArrayList<ByteArray>(chunkCount + 1)

        // The count includes the header chunk itself, and every payload chunk is zero-padded to
        // the full MTU. Both confirmed byte for byte against an HCI capture of the vendor
        // application on the user's console (2026-11-23); with either one different, the console
        // answers every command with the refusal `01 00 00 00`.
        chunks += byteArrayOf(HEADER_MARKER, HEADER_VERSION, payload.size.toByte(), (chunkCount + 1).toByte())

        if (payload.isEmpty()) {
            chunks += byteArrayOf(END_MARKER, 0).copyOf(MTU)
            return chunks
        }

        var offset = 0
        var index = 0
        while (offset < payload.size) {
            val size = minOf(PAYLOAD_PER_CHUNK, payload.size - offset)
            val isLast = offset + size >= payload.size
            val marker = if (isLast) END_MARKER else index.toByte()
            chunks += (byteArrayOf(marker, size.toByte()) + payload.copyOfRange(offset, offset + size)).copyOf(MTU)
            offset += size
            index++
        }
        return chunks
    }

    /**
     * Reassemble a chunk group back into the command payload.
     *
     * @throws IllegalArgumentException when the group is malformed, truncated, or never reaches
     *   the end marker.
     */
    fun decode(chunks: List<ByteArray>): ByteArray {
        require(chunks.isNotEmpty()) { "empty chunk group" }
        val header = chunks.first()
        require(header.size >= 4 && header[0] == HEADER_MARKER) { "missing header chunk" }

        val total = header[2].toInt() and 0xFF
        val out = ByteArray(total)
        var written = 0
        var sawEnd = false

        for (i in 1 until chunks.size) {
            val chunk = chunks[i]
            require(chunk.size >= 2) { "chunk $i is too short" }
            val marker = chunk[0]
            val size = chunk[1].toInt() and 0xFF
            require(chunk.size >= 2 + size) { "chunk $i is shorter than it declares" }
            require(written + size <= total) { "payload is longer than the header declares" }

            chunk.copyOfRange(2, 2 + size).copyInto(out, written)
            written += size

            if (marker == END_MARKER) {
                sawEnd = true
                break
            }
        }

        require(sawEnd) { "chunk group never reached the end marker" }
        require(written == total) { "payload is shorter than the header declares" }
        return out
    }

    /** Render bytes as lowercase hex, for logs and the CLI. */
    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
