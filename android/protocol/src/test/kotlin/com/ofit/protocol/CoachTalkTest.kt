package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoachTalkTest {
    private fun snap(
        elapsed: Long,
        remaining: Long,
        stepRemaining: Long = 60_000,
        speed: Double = 8.0,
        nextSpeed: Double? = 8.0,
        incline: Double = 0.0,
        nextIncline: Double? = 0.0,
        state: WorkoutEngineState = WorkoutEngineState.RUNNING,
        step: Int = 0,
    ) = WorkoutEngineSnapshot.idle().copy(
        state = state, stepIndex = step, stepCount = 10, totalElapsedMillis = elapsed, totalRemainingMillis = remaining,
        stepRemainingMillis = stepRemaining, speedKph = speed, nextSpeedKph = nextSpeed, inclinePercent = incline,
        nextInclinePercent = nextIncline,
    )

    @Test
    fun `greets once at the start`() {
        val c = CoachTalk()
        assertNotNull(c.update(snap(1_000, 600_000), 0))
        assertNull(c.update(snap(5_000, 596_000), 30_000))
    }

    @Test
    fun `says what changes, to what, and how hard, about ten seconds ahead, once per step`() {
        val c = CoachTalk(speedText = { "%.1f mph".format(java.util.Locale.ROOT, it / 1.609344) })
        c.referenceKph = 8.0
        c.update(snap(1_000, 600_000), 0)
        val line = c.update(snap(100_000, 500_000, stepRemaining = 10_000, nextSpeed = 9.656, nextIncline = 4.0), 1_000)!!
        assertTrue(line.contains("speed up to 6.0 mph"), line)
        assertTrue(line.contains("incline up to 4 percent"), line)
        assertTrue(line.contains("very hard"), line)
        assertNull(c.update(snap(101_000, 499_000, stepRemaining = 9_000, nextIncline = 4.0), 2_000))
    }

    @Test
    fun `intensity against the workout pace`() {
        val c = CoachTalk()
        c.referenceKph = 8.0
        assertEquals("an easy", c.intensity(5.0, 0.0))
        assertEquals("a steady", c.intensity(8.0, 0.0))
        assertEquals("a hard", c.intensity(8.0, 4.0))
        assertEquals("a very hard", c.intensity(11.0, 2.0))
    }

    @Test
    fun `halfway with time left, and the last minute`() {
        val c = CoachTalk()
        val half = c.update(snap(300_000, 300_000), 100_000)!!
        assertTrue(half.contains("Halfway") && half.contains("5 minutes to go"), half)
        assertTrue(c.update(snap(560_000, 40_000), 101_000)!!.contains("Last minute"), "urgent lines skip the gap")
    }

    @Test
    fun `cool-down line, nothing while paused`() {
        val c = CoachTalk()
        assertNull(c.update(snap(100_000, 100_000, state = WorkoutEngineState.PAUSED), 0))
        assertTrue(c.update(snap(100_000, 600_000, state = WorkoutEngineState.COOLING_DOWN), 0)!!.contains("Walk it out"))
    }

    @Test
    fun `check-ins take turns, with facts about the place`() {
        val c = CoachTalk()
        c.facts = listOf("The Eiffel Tower was built for the 1889 World's Fair.")
        c.update(snap(1_000, 3_600_000), 0)
        val lines = (1..600).mapNotNull { s -> c.update(snap(s * 1_000L + 60_000, 3_000_000), s * 1_000L + 60_000) }
        // Every minute over 10 minutes, half of them facts.
        assertEquals(10, lines.size, lines.toString())
        assertEquals(5, lines.count { it.startsWith("Fun fact") }, lines.toString())
        assertTrue(lines.any { it.startsWith("Fun fact: The Eiffel Tower") }, lines.toString())
        assertTrue(lines.any { it.contains("minutes done") }, lines.toString())
    }

    @Test
    fun `landmarks are announced just before they come into view, once`() {
        val c = CoachTalk()
        c.landmarks = listOf(CoachTalk.Landmark(300, "the Eiffel Tower", "It was built for the 1889 World's Fair."))
        c.update(snap(1_000, 3_600_000), 0)
        // Too early for the landmark (a check-in may be said here instead).
        assertTrue(c.update(snap(280_000, 3_000_000), 280_000)?.startsWith("Coming up") != true)
        val line = c.update(snap(295_000, 3_000_000), 295_000)!!
        assertTrue(line.startsWith("Coming up: the Eiffel Tower.") && line.contains("1889"), line)
        assertNull(c.update(snap(299_000, 3_000_000), 299_000))
    }

    @Test
    fun `nothing else is said just before a change warning`() {
        val c = CoachTalk()
        c.facts = listOf("A fact.")
        c.update(snap(1_000, 3_600_000), 0)
        // A check-in is due, but a speed change is announced in 10 s: stay quiet until then.
        assertNull(c.update(snap(200_000, 3_000_000, stepRemaining = 20_000, nextSpeed = 11.0), 200_000))
        val warning = c.update(snap(210_000, 3_000_000, stepRemaining = 10_000, nextSpeed = 11.0), 210_000)!!
        assertTrue(warning.startsWith("In 10 seconds"), warning)
    }
}
