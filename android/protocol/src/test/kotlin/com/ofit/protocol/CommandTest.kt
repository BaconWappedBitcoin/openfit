package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FitPro command frame.
 *
 * Layout: `[device][len][cmd][content...][checksum]`
 *  - `device` identifies the equipment family (0x04 treadmill)
 *  - `len`    is the total frame length, header and checksum included
 *  - `cmd`    is the command id
 *  - `checksum` is the low byte of the sum of every preceding byte
 *
 * Evidence: research.md section 4; corroborated by the MIT-licensed decoder in
 * `DavisGoglin/treadmill` operating on the vendor application's own byte log.
 */
class CommandTest {

    @Test
    fun `frame encodes device length command content and checksum`() {
        val content = byteArrayOf(0x00, 0x00)

        val frame = Command.encode(0x04, CommandId.READ_WRITE_DATA, content)

        assertEquals(0x04, frame[0].toInt() and 0xFF, "device")
        assertEquals(frame.size, frame[1].toInt() and 0xFF, "length covers the whole frame")
        assertEquals(CommandId.READ_WRITE_DATA, frame[2].toInt() and 0xFF, "command")
        assertArrayEquals(content, frame.copyOfRange(3, frame.size - 1), "content")
    }

    @Test
    fun `checksum is the low byte of the sum of all preceding bytes`() {
        // device=04 len=04 cmd=02 content=00 00 -> sum = 0x0A
        val frame = Command.encode(0x04, CommandId.READ_WRITE_DATA, byteArrayOf(0x00, 0x00))

        val expected = frame.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF

        assertEquals(expected, frame.last().toInt() and 0xFF)
        // frame is 04 06 02 00 00 <cs>: sum = 4 + 6 + 2 = 12 = 0x0C
        assertEquals(0x0C, frame.last().toInt() and 0xFF, "hand computed checksum for this frame")
    }

    @Test
    fun `checksum of an empty-content frame still covers the header`() {
        val frame = Command.encode(0x04, CommandId.DEVICE_INFO, byteArrayOf())

        // 04 + 04 + 81 = 0x89
        assertEquals(0x89, frame.last().toInt() and 0xFF)
        assertEquals(4, frame.size)
    }

    @Test
    fun `decode recovers the fields of an encoded frame`() {
        val content = byteArrayOf(0x01, 0x01, 0x00, 0xE8.toByte(), 0x03)

        val decoded = Command.decode(Command.encode(0x04, CommandId.READ_WRITE_DATA, content))

        assertEquals(0x04, decoded.device)
        assertEquals(CommandId.READ_WRITE_DATA, decoded.command)
        assertArrayEquals(content, decoded.content)
        assertTrue(decoded.checksumValid)
    }

    @Test
    fun `decode rejects a frame whose checksum does not match`() {
        val frame = Command.encode(0x04, CommandId.READ_WRITE_DATA, byteArrayOf(0x00, 0x00))
        frame[frame.size - 1] = (frame[frame.size - 1].toInt() xor 0xFF).toByte()

        val decoded = Command.decode(frame)

        assertEquals(false, decoded.checksumValid)
    }

    @Test
    fun `decode rejects a frame whose length byte disagrees with its size`() {
        val frame = Command.encode(0x04, CommandId.DEVICE_INFO, byteArrayOf())
        frame[1] = 0x7F

        val error = runCatching { Command.decode(frame) }.exceptionOrNull()

        assertTrue(error != null, "a length mismatch must be rejected")
    }

    @Test
    fun `command ids match the recovered command table`() {
        assertEquals(0x02, CommandId.READ_WRITE_DATA)
        assertEquals(0x80, CommandId.SUPPORTED_DEVICES)
        assertEquals(0x81, CommandId.DEVICE_INFO)
        assertEquals(0x82, CommandId.SYSTEM_INFO)
        assertEquals(0x84, CommandId.VERSION_INFO)
        assertEquals(0x88, CommandId.SUPPORTED_COMMANDS)
        assertEquals(0x90, CommandId.VERIFY_SECURITY)
        assertEquals(0x95, CommandId.SERIAL_NUMBER)
    }
}
