package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Field addressing and value encoding.
 *
 * A field is addressed by its numeric id. A request carries a bitmap of the ids it wants; the
 * values follow in ascending id order, each sized by its converter. All multi-byte integers are
 * little-endian.
 *
 * Evidence: research.md section 7.
 */
class FieldsTest {

    @Test
    fun `bitmap sets the bits of the requested ids`() {
        // ids 0, 1, 4 -> byte 0 bits 0,1,4
        val bitmap = FieldCodec.encodeBitmap(listOf(0, 1, 4))

        assertArrayEquals(byteArrayOf(0x13.toByte()), bitmap)
    }

    @Test
    fun `bitmap spans multiple bytes for ids above seven`() {
        // ids 0, 1, 4, 10
        val bitmap = FieldCodec.encodeBitmap(listOf(0, 1, 4, 10))

        assertArrayEquals(byteArrayOf(0x13.toByte(), 0x04), bitmap)
    }

    @Test
    fun `bitmap of an empty id set is empty`() {
        assertArrayEquals(byteArrayOf(), FieldCodec.encodeBitmap(emptyList()))
    }

    @Test
    fun `bitmap decodes back to the ids it was built from`() {
        val ids = listOf(0, 1, 3, 4, 10, 12, 15, 16, 20, 21, 46, 52, 54, 71, 75, 76, 96)

        assertEquals(ids, FieldCodec.decodeBitmap(FieldCodec.encodeBitmap(ids)))
    }

    @Test
    fun `the telemetry poll mask reproduces the captured request prefix`() {
        // Captured from the vendor application's byte log: content prefix 00 0d 1b 94 31 ...
        // Write mask of zero bytes, then a thirteen byte read mask.
        val ids = listOf(0, 1, 3, 4, 10, 12, 15, 16, 20, 21, 46, 52, 54, 71, 75, 76, 96)

        val bitmap = FieldCodec.encodeBitmap(ids)

        assertEquals(0x0D, bitmap.size, "the captured mask spans thirteen bytes")
        assertEquals(0x1B, bitmap[0].toInt() and 0xFF, "ids 0, 1, 3, 4")
        assertEquals(0x94, bitmap[1].toInt() and 0xFF, "ids 10, 12, 15")
        assertEquals(0x31, bitmap[2].toInt() and 0xFF, "ids 16, 20, 21")
    }

    @Test
    fun `speed encodes as an unsigned sixteen bit value scaled by one hundred`() {
        val encoded = FieldCodec.encodeValue(FieldValue.Speed(10.0))

        assertArrayEquals(byteArrayOf(0xE8.toByte(), 0x03), encoded)
    }

    @Test
    fun `incline encodes as a signed sixteen bit value scaled by one hundred`() {
        assertArrayEquals(
            byteArrayOf(0x2C, 0x01),
            FieldCodec.encodeValue(FieldValue.Incline(3.0)),
            "3.0 percent is 300",
        )
    }

    @Test
    fun `negative incline encodes with the sign bit set`() {
        val encoded = FieldCodec.encodeValue(FieldValue.Incline(-1.5))

        // -1.5 percent is -150, which as an unsigned 16 bit value is 0xFF6A
        assertEquals(0x6A, encoded[0].toInt() and 0xFF, "low byte of -150")
        assertEquals(0xFF, encoded[1].toInt() and 0xFF, "high byte of -150")
    }

    @Test
    fun `four byte fields are little-endian`() {
        val encoded = FieldCodec.encodeValue(FieldValue.UInt(1_234_567))

        assertArrayEquals(byteArrayOf(0x87.toByte(), 0xD6.toByte(), 0x12, 0x00), encoded)
    }

    @Test
    fun `values are ordered by ascending field id`() {
        val values = listOf(
            FieldId.WORKOUT_MODE to FieldValue.WorkoutMode(WorkoutMode.RUNNING),
            FieldId.GRADE to FieldValue.Incline(2.0),
            FieldId.KPH to FieldValue.Speed(6.0),
        )

        val encoded = FieldCodec.encodeWriteSection(values)

        // Write section: mask byte count, mask bytes, then values in ascending id order.
        assertEquals(0x02, encoded[0].toInt() and 0xFF, "two mask bytes cover ids 0..15")
        assertEquals(0x03, encoded[1].toInt() and 0xFF, "ids 0 and 1 set")
        assertEquals(0x10, encoded[2].toInt() and 0xFF, "id 12 is bit 4 of the second byte")
        assertEquals(0x58, encoded[3].toInt() and 0xFF, "6.0 km/h is 600, low byte")
        assertEquals(0x02, encoded[4].toInt() and 0xFF, "6.0 km/h is 600, high byte")
        assertEquals(0xC8, encoded[5].toInt() and 0xFF, "2.0 percent is 200, low byte")
        assertEquals(0x00, encoded[6].toInt() and 0xFF, "2.0 percent is 200, high byte")
        assertEquals(WorkoutMode.RUNNING.code, encoded[7].toInt(), "workout mode")
    }

    @Test
    fun `decoding a value yields the original value`() {
        assertEquals(FieldValue.Speed(10.0), FieldCodec.decodeValue(FieldId.KPH, byteArrayOf(0xE8.toByte(), 0x03)))
        assertEquals(FieldValue.Incline(-1.5), FieldCodec.decodeValue(FieldId.GRADE, byteArrayOf(0x6A, 0xFF.toByte())))
        assertEquals(FieldValue.UInt(1_234_567), FieldCodec.decodeValue(FieldId.CURRENT_DISTANCE, byteArrayOf(0x87.toByte(), 0xD6.toByte(), 0x12, 0x00)))
    }

    @Test
    fun `a truncated value decodes to absent rather than zero`() {
        val value = FieldCodec.decodeValue(FieldId.CURRENT_DISTANCE, byteArrayOf(0x01, 0x02))

        assertNull(value, "a short buffer must not silently decode as zero")
    }

    @Test
    fun `field sizes match the recovered field table`() {
        assertEquals(2, FieldId.KPH.size)
        assertEquals(2, FieldId.GRADE.size)
        assertEquals(4, FieldId.CURRENT_DISTANCE.size)
        assertEquals(1, FieldId.WORKOUT_MODE.size)
        assertEquals(1, FieldId.IDLE_MODE_LOCKOUT.size)
        assertEquals(1, FieldId.START_REQUESTED.size)
        assertEquals(1, FieldId.REQUIRE_START_REQUESTED.size)
        assertEquals(1, FieldId.ACTIVATION_LOCK.size)
        assertEquals(4, FieldId.PULSE.size)
    }

    @Test
    fun `pulse decodes into its four byte composite`() {
        val value = FieldCodec.decodeValue(
            FieldId.PULSE,
            byteArrayOf(120.toByte(), 80.toByte(), 10, 0x04),
        )

        assertTrue(value is FieldValue.Pulse)
        val pulse = value as FieldValue.Pulse
        assertEquals(120, pulse.currentBpm)
        assertEquals(80, pulse.averageBpm)
        assertEquals(10, pulse.count)
        assertEquals(PulseSource.BLE_HEART_RATE_MONITOR, pulse.source)
    }
}
