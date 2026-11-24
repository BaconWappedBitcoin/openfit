package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * BLE chunk framing.
 *
 * A command is split across 20-byte ATT writes. The first chunk carries a header; the payload
 * follows in numbered chunks, the last of which is marked with the end marker.
 *
 * Evidence: research.md sections 3.2 and 5.
 */
class FramingTest {

    @Test
    fun `single chunk message is a header chunk plus one end chunk`() {
        // The vendor application's first message, as captured from the user's console.
        val payload = byteArrayOf(0x02, 0x04, 0x02, 0x04, 0x02, 0x04, 0x81.toByte(), 0x87.toByte())

        val chunks = Framing.encode(payload)

        assertEquals(2, chunks.size)
        assertArrayEquals(
            byteArrayOf(0xFE.toByte(), 0x02, 0x08, 0x02),
            chunks[0],
            "header chunk: FE marker, total length, chunk count including the header",
        )
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0x08, 0x02, 0x04, 0x02, 0x04, 0x02, 0x04, 0x81.toByte(), 0x87.toByte())
                .copyOf(20),
            chunks[1],
            "only chunk carries the end marker and the whole payload, zero-padded to 20 bytes",
        )
    }

    @Test
    fun `multi chunk message numbers the chunks and ends with the end marker`() {
        val payload = ByteArray(40) { it.toByte() }

        val chunks = Framing.encode(payload)

        // 40 bytes at 18 per chunk -> 3 data chunks.
        assertEquals(4, chunks.size)
        assertArrayEquals(
            byteArrayOf(0xFE.toByte(), 0x02, 0x28, 0x04),
            chunks[0],
            "header: total length 40, three data chunks plus the header",
        )
        assertEquals(0x00.toByte(), chunks[1][0], "first payload chunk is index 0")
        assertEquals(0x01.toByte(), chunks[2][0], "second payload chunk is index 1")
        assertEquals(0xFF.toByte(), chunks[3][0], "last payload chunk carries the end marker")
        assertEquals(0x12, chunks[1][1].toInt(), "18 bytes of payload in a full chunk")
        assertEquals(40 - 36, chunks[3][1].toInt(), "remaining payload in the final chunk")
    }

    @Test
    fun `no chunk exceeds the 20 byte transfer unit`() {
        val payload = ByteArray(200) { it.toByte() }

        Framing.encode(payload).forEach { chunk ->
            assertEquals(true, chunk.size <= 20, "chunk of ${chunk.size} bytes exceeds the MTU")
        }
    }

    @Test
    fun `encode then decode round trips`() {
        val payload = ByteArray(55) { (it * 7).toByte() }

        assertArrayEquals(payload, Framing.decode(Framing.encode(payload)))
    }

    @Test
    fun `decode of a single chunk group restores the payload`() {
        val chunks = listOf(
            byteArrayOf(0xFE.toByte(), 0x02, 0x05, 0x01),
            byteArrayOf(0xFF.toByte(), 0x05, 0x04, 0x04, 0x02, 0x00, 0x00),
        )

        assertArrayEquals(byteArrayOf(0x04, 0x04, 0x02, 0x00, 0x00), Framing.decode(chunks))
    }

    @Test
    fun `decode rejects a group that never reaches the end marker`() {
        val chunks = listOf(
            byteArrayOf(0xFE.toByte(), 0x02, 0x28, 0x03),
            byteArrayOf(0x00, 0x12) + ByteArray(18),
        )

        val error = runCatching { Framing.decode(chunks) }.exceptionOrNull()

        assertEquals(true, error != null, "an incomplete group must not decode")
    }

    @Test
    fun `decode rejects a payload shorter than the header declares`() {
        val chunks = listOf(
            byteArrayOf(0xFE.toByte(), 0x02, 0x0A, 0x01),
            byteArrayOf(0xFF.toByte(), 0x02, 0x04, 0x04),
        )

        val error = runCatching { Framing.decode(chunks) }.exceptionOrNull()

        assertEquals(true, error != null, "a truncated payload must not decode")
    }
}
