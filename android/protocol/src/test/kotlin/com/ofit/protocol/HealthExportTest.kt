package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthExportTest {
    private fun entry(samples: List<HistorySample>, seconds: Long = 600, outcome: String = "COMPLETED", calories: Double? = 80.0) =
        HistoryEntry(1_000_000L, "Paris", seconds, 1500.0, calories, 12.0, 120, 150, outcome, samples = samples)

    @Test
    fun `a run becomes a running session with its heart rate and speed inside it`() {
        val samples = listOf(
            HistorySample(0, 5.0, 0.0, null),
            HistorySample(10, 9.0, 1.0, 130),
            HistorySample(20, 9.0, 1.0, 0),
            HistorySample(30, 9.0, 1.0, 140),
            HistorySample(700, 9.0, 1.0, 140), // past the end: dropped
        )
        val s = HealthExport.of(entry(samples))!!
        assertEquals("openfit-1000000", s.clientId)
        assertEquals(1_000_000L + 600_000, s.endMillis)
        assertTrue(s.running)
        assertEquals(listOf(1_010_000L to 130, 1_030_000L to 140), s.heartRate)
        assertEquals(4, s.speed.size)
        assertEquals(2.5, s.speed[1].second, 1e-9)
        assertEquals(80.0, s.kcal)
        assertEquals(12.0, s.climbMeters)
    }

    @Test
    fun `walks, short or failed workouts, and no samples`() {
        val walk = HealthExport.of(entry(List(10) { HistorySample(it * 10, 5.5, 3.0, null) }))!!
        assertFalse(walk.running)
        assertTrue(walk.heartRate.isEmpty())
        assertNull(HealthExport.of(entry(emptyList(), seconds = 59)))
        assertNull(HealthExport.of(entry(emptyList(), outcome = "FAILED")))
        // No samples: decided by the average (1.5 km in 10 min is 9 km/h).
        assertTrue(HealthExport.of(entry(emptyList(), calories = 0.0))!!.running)
        assertNull(HealthExport.of(entry(emptyList(), calories = 0.0))!!.kcal)
    }
}
