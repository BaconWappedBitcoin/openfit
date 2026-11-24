package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class IntensityTest {
    private val program = WorkoutProgram(
        name = "Paris",
        steps = listOf(WorkoutStep(10.0, 1.0, 60), WorkoutStep(8.0, 0.0, 60), WorkoutStep(6.0, -2.0, 60)),
        videoPaceKph = 9.0,
    )

    @Test
    fun `levels scale speed and nudge incline, keeping the name and video pace`() {
        assertSame(program, Intensity.apply(program, Intensity.Level.STANDARD))
        val hard = Intensity.apply(program, Intensity.Level.HARD)
        assertEquals("Paris", hard.name)
        assertEquals(9.0, hard.videoPaceKph)
        assertEquals(listOf(11.0, 8.8, 6.6), hard.steps.map { it.speedKph })
        assertEquals(listOf(2.0, 1.0, -2.0), hard.steps.map { it.inclinePercent })
        val gentle = Intensity.apply(program, Intensity.Level.GENTLE)
        assertEquals(listOf(8.0, 6.4, 4.8), gentle.steps.map { it.speedKph })
        // Never below flat, but a downhill keeps its slope.
        assertEquals(listOf(0.0, 0.0, -2.0), gentle.steps.map { it.inclinePercent })
        assertEquals(program.totalDurationSeconds, gentle.totalDurationSeconds)
    }

    @Test
    fun `the last rating suggests a step up or down`() {
        fun e(at: Long, feel: String?) = HistoryEntry(at, "Paris", 600, 1000.0, null, 0.0, null, null, "COMPLETED", feel = feel)
        val std = Intensity.Level.STANDARD
        assertEquals(Intensity.Level.HARD, Intensity.suggest(listOf(e(1, "hard"), e(2, "easy")), "Paris", std)!!.level)
        assertEquals(Intensity.Level.EASY, Intensity.suggest(listOf(e(1, "easy"), e(2, "hard"), e(3, null)), "Paris", std)!!.level)
        assertNull(Intensity.suggest(listOf(e(1, "right")), "Paris", std))
        assertNull(Intensity.suggest(listOf(e(1, "easy")), "Dallas", std))
        assertNull(Intensity.suggest(listOf(e(1, "easy")), "Paris", Intensity.Level.HARDEST))
        assertEquals(Intensity.Level.HARD, Intensity.Level.of("HARD"))
        assertEquals(std, Intensity.Level.of(null))
    }
}
