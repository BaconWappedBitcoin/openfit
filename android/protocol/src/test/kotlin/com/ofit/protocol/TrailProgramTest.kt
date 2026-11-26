package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrailProgramTest {
    @Test
    fun `a trail has the asked length, a warm-up and an easy finish`() {
        val p = TrailProgram.program(TrailProgram.trail("forest"), 30, TrailProgram.Effort.JOG)
        assertEquals(30 * 60, p.totalDurationSeconds)
        assertTrue(p.steps.first().speedKph < 8.0 && p.steps.first().inclinePercent == 0.0)
        assertTrue(p.steps.last().speedKph < 8.0 && p.steps.last().inclinePercent == 0.0)
        assertTrue(p.name.startsWith("Forest trail"))
    }

    @Test
    fun `hills suit the landscape and stay within it`() {
        fun maxIncline(key: String) = TrailProgram.program(TrailProgram.trail(key), 45, TrailProgram.Effort.RUN).steps.maxOf { it.inclinePercent }
        assertTrue(maxIncline("mountains") > maxIncline("meadow"))
        assertTrue(maxIncline("town") <= 3.0)
        assertEquals(10, TrailProgram.TRAILS.size)
        TrailProgram.TRAILS.forEach { t ->
            val steps = TrailProgram.program(t, 40, TrailProgram.Effort.WALK).steps
            assertTrue(steps.all { it.inclinePercent in 0.0..t.maxIncline }, t.key)
            assertTrue(steps.all { it.speedKph > 0 }, t.key)
        }
    }

    @Test
    fun `climbs are a little slower and the same choice gives the same workout`() {
        val t = TrailProgram.trail("mountains")
        val p = TrailProgram.program(t, 40, TrailProgram.Effort.RUN)
        val steep = p.steps.filter { it.inclinePercent >= 6 }
        assertTrue(steep.isNotEmpty() && steep.all { it.speedKph < 10.0 })
        assertEquals(p, TrailProgram.program(t, 40, TrailProgram.Effort.RUN))
    }
}
