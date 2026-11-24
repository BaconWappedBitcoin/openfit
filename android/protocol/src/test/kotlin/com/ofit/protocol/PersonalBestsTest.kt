package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class PersonalBestsTest {

    private val zone = ZoneOffset.UTC

    private fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 12, 0).toInstant(zone)

    private fun entry(y: Int, m: Int, d: Int, seconds: Long = 600, meters: Double = 1000.0, climb: Double = 0.0, cal: Double? = null) =
        HistoryEntry(at(y, m, d).toEpochMilli(), "Run", seconds, meters, cal, climb, null, null, "COMPLETED")

    @Test
    fun `bests pick the biggest of each`() {
        val a = entry(2026, 11, 2, seconds = 1800, meters = 3000.0, climb = 10.0, cal = 200.0)
        val b = entry(2026, 11, 9, seconds = 2400, meters = 2500.0, climb = 40.0, cal = 150.0)
        val bests = PersonalBests.of(listOf(a, b), at(2026, 11, 10), zone)
        assertEquals(b, bests.longest)
        assertEquals(a, bests.farthest)
        assertEquals(b, bests.mostClimb)
        assertEquals(a, bests.mostCalories)
    }

    @Test
    fun `nothing yet`() {
        val bests = PersonalBests.of(emptyList(), at(2026, 11, 24), zone)
        assertNull(bests.longest)
        assertEquals(0, bests.weekStreak)
        assertEquals(0, bests.bestWeekStreak)
    }

    @Test
    fun `weeks in a row, counting this week or last`() {
        // 2026-11-23 is a Monday. Workouts in the weeks of Nov 2, Nov 9, Nov 16; none yet this week.
        val list = listOf(entry(2026, 11, 3), entry(2026, 11, 12), entry(2026, 11, 22), entry(2026, 10, 12))
        val bests = PersonalBests.of(list, at(2026, 11, 24), zone)
        assertEquals(3, bests.weekStreak, "alive: last week had one")
        assertEquals(3, bests.bestWeekStreak)
        // Two weeks with nothing: the streak is over.
        assertEquals(0, PersonalBests.of(list, at(2026, 12, 2), zone).weekStreak)
        // Working out this week extends it.
        assertEquals(4, PersonalBests.of(list + entry(2026, 11, 24), at(2026, 11, 25), zone).weekStreak)
    }
}
