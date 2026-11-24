package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuickStartEstimateTest {
    @Test
    fun `distance is speed times time`() {
        val program = WorkoutProgram("x", listOf(WorkoutStep(6.0, 0.0, 600), WorkoutStep(12.0, 0.0, 300)))
        val e = QuickStart.estimate(program, BodyProfile())
        assertEquals(2000.0, e.meters, 0.5)
        assertTrue(e.kcal > 0)
    }

    @Test
    fun `hills and weight burn more`() {
        val flat = QuickStart.estimate(WorkoutProgram("f", listOf(WorkoutStep(5.0, 0.0, 600))), BodyProfile())
        val hill = QuickStart.estimate(WorkoutProgram("h", listOf(WorkoutStep(5.0, 6.0, 600))), BodyProfile())
        val heavy = QuickStart.estimate(WorkoutProgram("f", listOf(WorkoutStep(5.0, 0.0, 600))), BodyProfile(weightKg = 100.0))
        assertTrue(hill.kcal > flat.kcal)
        assertTrue(heavy.kcal > flat.kcal)
    }

    @Test
    fun `presets are named and sensible`() {
        assertEquals(5, QuickStart.PRESETS.size)
        assertTrue(QuickStart.PRESETS.all { it.speedKph in 3.0..16.0 && it.inclinePercent in 0.0..10.0 })
    }
}
