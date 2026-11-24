package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SummaryLookupTest {
    private fun entry(start: Long, name: String = "Paris", seconds: Long = 97) =
        HistoryEntry(start, name, seconds, 128.0, null, 0.0, null, null, "COMPLETED")

    @Test
    fun `a run at a slowed video rate is found by its real start`() {
        // Started at t=1_000_000 by the wall clock; 97 s real, only ~64 s on the program clock.
        val saved = entry(1_000_000)
        val startedWall = 1_000_400L
        assertEquals(saved, SummaryLookup.find(listOf(saved), "Paris", startedWall))
        // The old estimate (end - program time - 10 s) started later than the run and missed it.
        val oldSince = 1_000_000 + 97_000 - 64_000 - 10_000L
        assertNull(listOf(saved).firstOrNull { it.startedAtMillis >= oldSince })
    }

    @Test
    fun `an earlier run of the same workout, or another workout, is not picked`() {
        val earlier = entry(500_000)
        val other = entry(1_000_200, name = "Dallas")
        assertNull(SummaryLookup.find(listOf(other, earlier), "Paris", 1_000_000))
        val now = entry(1_000_100)
        assertEquals(now, SummaryLookup.find(listOf(now, earlier), "Paris", 1_000_000))
    }
}
