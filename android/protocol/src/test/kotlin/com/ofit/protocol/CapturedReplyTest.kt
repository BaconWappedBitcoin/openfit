package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Replies captured from the user's own I_TL console (HCI snoop log, 2026-11-23), run through the
 * same path the app uses: chunk reassembly, transport header, command frame, field values.
 */
class CapturedReplyTest {

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `telemetry poll reply decodes into every polled field`() {
        // The console's answer to ofit's telemetry poll, as four 20-byte notifications.
        val chunks = listOf(
            hex("fe0235040000005802000000000000000000c12c"),
            hex("0012010402310431020200000000000000000000"),
            hex("011200000000010000000000000000000000002c"),
            hex("ff1101000000005802000000000000000000c12c"),
        )
        val frame = Command.decode(Wire.unwrap(Framing.decode(chunks)))

        assertTrue(frame.checksumValid)
        assertEquals(CommandId.READ_WRITE_DATA, frame.command)
        assertEquals(CommandStatus.DONE, frame.content[0].toInt())

        val decoded = decodeAll(frame.content, POLL_AT_CAPTURE)
        assertEquals(0.0, (decoded[FieldId.KPH] as FieldValue.Speed).kmh)
        assertEquals(0.0, (decoded[FieldId.ACTUAL_KPH] as FieldValue.Speed).kmh)
    }

    @Test
    fun `running reply shows the target when the console reports no actual speed`() {
        // Belt running at 1.6 km/h, incline raised to 1.5 % (12:40:20).
        val frame = Command.decode(
            Wire.unwrap(
                hex(
                    "01040231043102 02 a000 9600 7f00 07000000 00000000 02 1100 0000 11000000 10610100" +
                        " 2c01 4f00 7400 b400 58020000 58020000 00 e3",
                ),
            ),
        )
        assertTrue(frame.checksumValid)
        val decoded = decodeAll(frame.content, POLL_AT_CAPTURE)
        assertEquals(1.6, (decoded[FieldId.KPH] as FieldValue.Speed).kmh, 1e-9)
        assertEquals(1.5, (decoded[FieldId.GRADE] as FieldValue.Incline).percent, 1e-9)
        assertEquals(WorkoutMode.RUNNING, (decoded[FieldId.WORKOUT_MODE] as FieldValue.WorkoutMode).mode)
        assertEquals(0.0, (decoded[FieldId.ACTUAL_KPH] as FieldValue.Speed).kmh)

        val state = TreadmillState(targetSpeedKph = 1.6, actualSpeedKph = 0.0, targetInclinePercent = 1.5)
        assertEquals(1.6, state.displayedSpeedKph)
        assertEquals(1.5, state.displayedInclinePercent)
        assertEquals(0.0, TreadmillState(targetSpeedKph = 0.0, actualSpeedKph = 0.0).displayedSpeedKph)
        assertEquals(2.0, TreadmillState(targetSpeedKph = 0.0, actualSpeedKph = 2.0).displayedSpeedKph)
    }

    /** Decode a ReadWriteData reply (status byte, then values in ascending id order). */
    private fun decodeAll(content: ByteArray, ids: List<Int>): Map<FieldId, FieldValue> {
        assertEquals(CommandStatus.DONE, content[0].toInt())
        val values = content.copyOfRange(1, content.size)
        var offset = 0
        val decoded = mutableMapOf<FieldId, FieldValue>()
        for (id in ids) {
            val field = FieldId.of(id)!!
            decoded[field] = FieldCodec.decodeValue(field, values.copyOfRange(offset, offset + field.size))!!
            offset += field.size
        }
        assertEquals(values.size, offset, "the reply carries exactly the polled fields")
        return decoded
    }

    private companion object {
        /** The telemetry poll as it was when these replies were captured (before field 17). */
        val POLL_AT_CAPTURE: List<Int> = Polls.TELEMETRY - FieldId.ACTUAL_INCLINE.id
    }

    @Test
    fun `arming writes match the vendor application byte for byte`() {
        fun armingFrame(field: FieldId, on: Boolean): String = Framing.toHex(
            Command.encode(
                EquipmentId.TREADMILL,
                CommandId.READ_WRITE_DATA,
                FieldCodec.encodeWriteSection(listOf(field to FieldValue.Flag(on))) +
                    FieldCodec.encodeReadSection(emptyList()),
            ),
        )
        assertEquals(
            "0415020e" + "00".repeat(13) + "10" + "01" + "00" + "3a",
            armingFrame(FieldId.REQUIRE_START_REQUESTED, true),
        )
        assertEquals(
            "0413020c" + "00".repeat(11) + "80" + "00" + "00" + "a5",
            armingFrame(FieldId.IDLE_MODE_LOCKOUT, false),
        )
    }

    @Test
    fun `wire header is stripped from replies and added to requests`() {
        val request = Command.encode(EquipmentId.GENERAL, 0x81, ByteArray(0))
        assertEquals("0204020402048187", Framing.toHex(Wire.payload(request)))
        assertEquals("0204", Framing.toHex(Wire.unwrap(hex("0104020402040000")).copyOf(2)))
    }
}
