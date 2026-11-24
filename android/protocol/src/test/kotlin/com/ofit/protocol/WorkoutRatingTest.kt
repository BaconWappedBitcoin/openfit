package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkoutRatingTest {
    private fun p(vararg steps: WorkoutStep) = WorkoutProgram("x", steps.toList())

    @Test
    fun `a flat stroll is an easy walk`() {
        val r = WorkoutRating.of(p(WorkoutStep(4.8, 0.0, 20 * 60)))
        assertEquals(WorkoutRating.Level.EASY, r.level)
        assertEquals(WorkoutRating.Kind.WALK, r.kind)
        assertEquals("Easy walk", r.label)
    }

    @Test
    fun `a steep brisk walk is medium`() {
        val r = WorkoutRating.of(p(WorkoutStep(5.6, 8.0, 20 * 60)))
        assertEquals(WorkoutRating.Level.MEDIUM, r.level)
        assertEquals(WorkoutRating.Kind.WALK, r.kind)
    }

    @Test
    fun `a run is hard`() {
        val r = WorkoutRating.of(p(WorkoutStep(10.0, 1.0, 20 * 60)))
        assertEquals(WorkoutRating.Level.HARD, r.level)
        assertEquals(WorkoutRating.Kind.RUN, r.kind)
    }

    @Test
    fun `half walking half running is a run-walk`() {
        val r = WorkoutRating.of(p(WorkoutStep(5.0, 0.0, 600), WorkoutStep(9.0, 0.0, 600)))
        assertEquals(WorkoutRating.Kind.RUN_WALK, r.kind)
    }

    @Test
    fun `a long workout counts one level harder`() {
        assertEquals(WorkoutRating.Level.MEDIUM, WorkoutRating.of(p(WorkoutStep(4.8, 0.0, 50 * 60))).level)
    }
}
