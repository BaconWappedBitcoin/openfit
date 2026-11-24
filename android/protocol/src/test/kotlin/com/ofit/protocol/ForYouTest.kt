package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ForYouTest {
    private val day = 24 * 3_600_000L
    private val now = 100 * day
    private fun walk(name: String) = ForYou.Candidate(name, name, WorkoutProgram(name, listOf(WorkoutStep(4.8, 0.0, 1200))))
    private fun run(name: String) = ForYou.Candidate(name, name, WorkoutProgram(name, listOf(WorkoutStep(10.0, 1.0, 1200))))
    private fun ran(name: String, daysAgo: Int) = HistoryEntry(now - daysAgo * day, name, 1200, 1600.0, null, 0.0, null, null, "COMPLETED")

    @Test
    fun `workouts like the usual ones come first, nothing run in the last two days`() {
        val all = listOf(run("Fast"), walk("Stroll A"), walk("Stroll B"), walk("Stroll C"))
        val history = listOf(ran("Stroll A", 1), ran("Stroll B", 10), ran("Stroll A", 5), ran("Stroll B", 12))
        val picked = ForYou.pick(all, history, now).map { it.name }
        assertFalse("Stroll A" in picked, "run yesterday")
        assertEquals(listOf("Stroll C", "Stroll B", "Fast"), picked, "easy walks first (never run, then longest ago), then the rest")
    }

    @Test
    fun `with no history everything is offered`() {
        val all = listOf(run("Fast"), walk("Stroll"))
        assertEquals(2, ForYou.pick(all, emptyList(), now).size)
    }
}
