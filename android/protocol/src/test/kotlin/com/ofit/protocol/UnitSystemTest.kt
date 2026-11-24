package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnitSystemTest {

    @Test
    fun `metric leaves values alone`() {
        assertEquals(8.0, UnitSystem.METRIC.speedFromKph(8.0), 1e-9)
        assertEquals("8.0 km/h", UnitSystem.METRIC.speedText(8.0))
        assertEquals("5.00 km", UnitSystem.METRIC.distanceText(5000.0))
    }

    @Test
    fun `imperial converts speed and distance`() {
        assertEquals(5.0, UnitSystem.IMPERIAL.speedFromKph(8.04672), 1e-6)
        assertEquals("5.0 mph", UnitSystem.IMPERIAL.speedText(8.04672))
        assertEquals("3.11 mi", UnitSystem.IMPERIAL.distanceText(5000.0))
    }

    @Test
    fun `a speed typed in either system round-trips to the same km per hour`() {
        for (system in UnitSystem.entries) {
            for (kph in listOf(1.6, 4.0, 9.8, 12.0)) {
                assertEquals(kph, system.speedToKph(system.speedFromKph(kph)), 1e-9)
            }
        }
    }

    @Test
    fun `pace is per kilometre or per mile`() {
        assertEquals("6:00", UnitSystem.METRIC.paceText(10.0))
        assertEquals("9:39", UnitSystem.IMPERIAL.paceText(10.0))
        assertEquals("--:--", UnitSystem.METRIC.paceText(null))
        assertEquals("--:--", UnitSystem.IMPERIAL.paceText(0.0))
    }

    @Test
    fun `an unknown stored name falls back to metric`() {
        assertEquals(UnitSystem.METRIC, UnitSystem.fromName(null))
        assertEquals(UnitSystem.METRIC, UnitSystem.fromName("furlongs"))
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.fromName("IMPERIAL"))
    }
}
