package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FtmsTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `the service and characteristic ids are the assigned numbers`() {
        assertEquals("00001826-0000-1000-8000-00805f9b34fb", Ftms.SERVICE)
        assertEquals("00002ad2-0000-1000-8000-00805f9b34fb", Ftms.INDOOR_BIKE_DATA)
        assertEquals("00002ad9-0000-1000-8000-00805f9b34fb", Ftms.CONTROL_POINT)
    }

    @Test
    fun `a bike reading with speed, cadence and power`() {
        // flags 0x0044: bit 2 (cadence) and bit 6 (power), speed present (bit 0 clear).
        // speed 25.50 km/h = 2550 = 0x09F6; cadence 90.5 rpm = 181 = 0x00B5; power 215 W = 0x00D7.
        val d = Ftms.parseBikeData(bytes(0x44, 0x00, 0xF6, 0x09, 0xB5, 0x00, 0xD7, 0x00))!!
        assertEquals(25.5, d.speedKph!!, 1e-9)
        assertEquals(90.5, d.cadenceRpm!!, 1e-9)
        assertEquals(215, d.powerWatts)
        assertNull(d.resistanceLevel)
        assertNull(d.heartRateBpm)
    }

    @Test
    fun `the bit that means more data hides the speed, and every optional field is read in order`() {
        // flags = bit0 (no speed) + bit4 distance + bit5 resistance + bit8 energy + bit9 heart + bit11 elapsed
        //       = 0x0001 + 0x0010 + 0x0020 + 0x0100 + 0x0200 + 0x0800 = 0x0B31
        val d = Ftms.parseBikeData(
            bytes(
                0x31, 0x0B,
                0xE8, 0x03, 0x00,       // distance 1000 m
                0x0C, 0x00,             // resistance level 12
                0x2A, 0x00, 0x90, 0x01, 0x07, // 42 kcal total, 400 per hour, 7 per minute
                0x8C,                   // heart rate 140
                0x2C, 0x01,             // elapsed 300 s
            ),
        )!!
        assertNull(d.speedKph)
        assertEquals(1000, d.distanceMeters)
        assertEquals(12, d.resistanceLevel)
        assertEquals(42, d.totalKcal)
        assertEquals(140, d.heartRateBpm)
        assertEquals(300, d.elapsedSeconds)
    }

    @Test
    fun `negative power is read as signed and a short packet is refused`() {
        // power -5 W = 0xFFFB
        val d = Ftms.parseBikeData(bytes(0x40, 0x00, 0x00, 0x00, 0xFB, 0xFF))!!
        assertEquals(-5, d.powerWatts)
        assertNull(Ftms.parseBikeData(bytes(0x44, 0x00, 0xF6, 0x09)), "flags promise more than was sent")
        assertNull(Ftms.parseBikeData(bytes(0x44)))
    }

    @Test
    fun `features say what can be read and set`() {
        // machine: cadence (bit 1) + power (bit 14); targets: resistance (bit 2) + power (bit 3) + simulation (bit 13)
        val machine = (1 shl 1) or (1 shl 14)
        val targets = (1 shl 2) or (1 shl 3) or (1 shl 13)
        val f = Ftms.parseFeatures(
            bytes(machine and 0xFF, (machine shr 8) and 0xFF, 0, 0, targets and 0xFF, (targets shr 8) and 0xFF, 0, 0),
        )!!
        assertTrue(f.cadence && f.power && f.resistanceTarget && f.powerTarget && f.simulation)
        assertFalse(f.inclinationTarget || f.speedTarget || f.heartRate)
        assertNull(Ftms.parseFeatures(bytes(1, 2, 3)))
    }

    @Test
    fun `ranges come with their resolution`() {
        // resistance 1.0 to 24.0, step 1.0: 10, 240, 10 (0.1 units)
        val r = Ftms.parseResistanceRange(bytes(0x0A, 0x00, 0xF0, 0x00, 0x0A, 0x00))!!
        assertEquals(Ftms.Range(1.0, 24.0, 1.0), r)
        // power 0 to 2000 W, step 1 W
        assertEquals(Ftms.Range(0.0, 2000.0, 1.0), Ftms.parsePowerRange(bytes(0x00, 0x00, 0xD0, 0x07, 0x01, 0x00)))
        // inclination -10.0 % to 20.0 %, step 0.5: -100, 200, 5
        val inc = Ftms.parseInclinationRange(bytes(0x9C, 0xFF, 0xC8, 0x00, 0x05, 0x00))!!
        assertEquals(-10.0, inc.min, 1e-9); assertEquals(20.0, inc.max, 1e-9); assertEquals(0.5, inc.step, 1e-9)
    }

    @Test
    fun `commands are built as the specification lays them out`() {
        assertArrayEquals(bytes(0x00), Ftms.requestControl())
        assertArrayEquals(bytes(0x07), Ftms.start())
        assertArrayEquals(bytes(0x08, 0x01), Ftms.stop())
        assertArrayEquals(bytes(0x08, 0x02), Ftms.pause())
        // 8.50 km/h = 850 = 0x0352
        assertArrayEquals(bytes(0x02, 0x52, 0x03), Ftms.setTargetSpeed(8.5))
        // -2.5 % = -25 = 0xFFE7
        assertArrayEquals(bytes(0x03, 0xE7, 0xFF), Ftms.setTargetInclination(-2.5))
        // resistance level 12.5 = 125
        assertArrayEquals(bytes(0x04, 0x7D), Ftms.setTargetResistance(12.5))
        // 180 W = 0x00B4
        assertArrayEquals(bytes(0x05, 0xB4, 0x00), Ftms.setTargetPower(180))
        // simulation: wind 0, grade 5.25 % = 525 = 0x020D, crr 0.004 = 40, cw 0.51 = 51
        assertArrayEquals(bytes(0x11, 0x00, 0x00, 0x0D, 0x02, 0x28, 0x33), Ftms.setSimulation(gradePercent = 5.25))
    }

    @Test
    fun `the machine's answer says whether a command was taken`() {
        val ok = Ftms.parseResponse(bytes(0x80, 0x05, 0x01))!!
        assertEquals(5, ok.command); assertTrue(ok.accepted)
        val bad = Ftms.parseResponse(bytes(0x80, 0x04, 0x03))!!
        assertEquals(Ftms.Response.Result.INVALID_PARAMETER, bad.result); assertFalse(bad.accepted)
        assertNull(Ftms.parseResponse(bytes(0x00, 0x05, 0x01)))
        assertNull(Ftms.parseResponse(bytes(0x80, 0x05)))
    }
}
