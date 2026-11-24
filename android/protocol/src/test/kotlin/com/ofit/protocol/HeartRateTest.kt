package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeartRateTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `8-bit heart rate with no contact support`() {
        val m = HeartRateMeasurement.parse(bytes(0x00, 72))!!
        assertEquals(72, m.bpm)
        assertNull(m.skinContact)
        assertTrue(m.usable)
    }

    @Test
    fun `16-bit heart rate`() {
        val m = HeartRateMeasurement.parse(bytes(0x01, 0x2C, 0x01))!!
        assertEquals(300, m.bpm)
        assertFalse(m.usable) // out of the plausible range
    }

    @Test
    fun `contact supported but not detected is not usable`() {
        val m = HeartRateMeasurement.parse(bytes(0x04, 90))!!
        assertEquals(false, m.skinContact)
        assertFalse(m.usable)
        val ok = HeartRateMeasurement.parse(bytes(0x06, 90))!!
        assertEquals(true, ok.skinContact)
        assertTrue(ok.usable)
    }

    @Test
    fun `energy and RR intervals (Polar H10 shape)`() {
        // flags 0x16: contact detected + RR present; bpm 128; RR 0x01A0 (416/1024 s) and 0x0200 (512/1024 s).
        val m = HeartRateMeasurement.parse(bytes(0x16, 128, 0xA0, 0x01, 0x00, 0x02))!!
        assertEquals(128, m.bpm)
        assertEquals(listOf(406, 500), m.rrIntervalsMillis)
        val withEnergy = HeartRateMeasurement.parse(bytes(0x08, 100, 0x10, 0x00))!!
        assertEquals(16, withEnergy.energyExpendedKj)
    }

    @Test
    fun `malformed values are rejected`() {
        assertNull(HeartRateMeasurement.parse(ByteArray(0)))
        assertNull(HeartRateMeasurement.parse(bytes(0x01, 0x50)))
    }

    @Test
    fun `zones from age or max`() {
        val zones = HeartRateZones.of(userMaxHr = null, age = 40)!!
        assertEquals(180, zones.maxHr)
        assertEquals(0, zones.zoneOf(80))
        assertEquals(1, zones.zoneOf(95))
        assertEquals(3, zones.zoneOf(140))
        assertEquals(5, zones.zoneOf(175))
        assertEquals(190, HeartRateZones.of(190, 40)!!.maxHr)
        assertNull(HeartRateZones.of(null, null))
    }

    @Test
    fun `a fresh strap wins, a stale one falls back to the console`() {
        assertEquals(140 to HeartRateSource.STRAP, HeartRateSelector.select(140, 1_000, 120, 4_000))
        assertEquals(120 to HeartRateSource.CONSOLE, HeartRateSelector.select(140, 1_000, 120, 7_000))
        assertEquals(null to HeartRateSource.NONE, HeartRateSelector.select(140, 1_000, 0, 7_000))
    }

    @Test
    fun `the record keeps one sample a second`() {
        val r = HeartRateRecord()
        r.add(0, 100); r.add(500, 200); r.add(1_000, 120); r.add(2_100, 140)
        assertEquals(3, r.count)
        assertEquals(120, r.average)
        assertEquals(140, r.max)
    }
}
