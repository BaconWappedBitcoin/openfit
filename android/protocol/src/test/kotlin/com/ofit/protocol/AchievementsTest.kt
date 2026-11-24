package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class AchievementsTest {
    private fun run(at: Instant, minutes: Long, km: Double, climb: Double = 0.0) =
        HistoryEntry(at.toEpochMilli(), "x", minutes * 60, km * 1000, null, climb, null, null, "COMPLETED")

    @Test
    fun `badges are earned from history, with progress towards the rest`() {
        val t = Instant.parse("2026-11-20T10:00:00Z")
        val entries = List(10) { run(t, 35, 4.5, climb = 60.0) }
        val b = Achievements.badges(entries, bestWeekStreak = 3).associateBy { it.id }
        assertTrue(b.getValue("first").earned)
        assertTrue(b.getValue("w10").earned)
        assertFalse(b.getValue("w25").earned)
        assertEquals(0.4f, b.getValue("w25").progress, 1e-6f)
        assertTrue(b.getValue("m30").earned)
        assertFalse(b.getValue("m60").earned)
        assertTrue(b.getValue("k42").earned, "45 km in all")
        assertTrue(b.getValue("s2").earned)
        assertFalse(b.getValue("s4").earned)
        assertTrue(b.getValue("c500").earned, "600 m climbed")
    }

    @Test
    fun `the monthly challenge counts only this month`() {
        val now = Instant.parse("2026-11-24T12:00:00Z")
        val entries = listOf(
            run(Instant.parse("2026-11-02T08:00:00Z"), 40, 6.0),
            run(Instant.parse("2026-11-20T08:00:00Z"), 40, 4.0),
            run(Instant.parse("2026-10-30T08:00:00Z"), 40, 9.0),
        )
        val c = Achievements.monthly(entries, now, ZoneOffset.UTC)
        assertEquals(10_000.0, c.doneMeters)
        assertEquals(0.25f, c.progress, 1e-6f)
        assertEquals(6, c.daysLeft)
        assertFalse(c.complete)
        val miles = Achievements.monthly(entries, now, ZoneOffset.UTC, Achievements.MONTHLY_GOAL_METERS_IMPERIAL)
        assertEquals(25 * 1609.344, miles.goalMeters, 1e-9)
    }
}
