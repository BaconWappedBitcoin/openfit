package com.ofit.protocol

import com.ofit.protocol.QuickStart.Goal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuickStartTest {

    private val limits = SafetyLimits(
        maxSpeedKph = 16.1, maxInclinePercent = 10.0,
        machineMaxSpeedKph = 16.1, machineMinSpeedKph = 0.8,
        machineMaxInclinePercent = 10.0, machineMinInclinePercent = 0.0,
    )

    @Test
    fun `time goal with warm-up and cool-down`() {
        val p = QuickStart.build(6.0, 2.0, Goal.Time(30), warmUp = true, coolDown = true, limits = limits)
        assertEquals(3, p.steps.size)
        assertEquals(WorkoutStep(3.6, 0.0, 180), p.steps[0])
        assertEquals(WorkoutStep(6.0, 2.0, 1800), p.steps[1])
        assertEquals(WorkoutStep(3.6, 0.0, 180), p.steps[2])
        assertEquals("Quick start · 30 min", p.name)
        assertTrue(p.validate(limits).isValid)
    }

    @Test
    fun `distance goal sets the main step's length from the speed`() {
        val p = QuickStart.build(6.0, 0.0, Goal.Distance(1000.0), warmUp = false, coolDown = false, limits = limits)
        assertEquals(1, p.steps.size)
        assertEquals(600, p.steps[0].durationSeconds) // 1 km at 6 km/h = 10 min
    }

    @Test
    fun `values are held inside the limits and an open run still ends`() {
        val p = QuickStart.build(30.0, 15.0, Goal.Open, warmUp = false, coolDown = false, limits = limits.copy(maxSpeedKph = 8.0))
        assertEquals(8.0, p.steps[0].speedKph)
        assertEquals(10.0, p.steps[0].inclinePercent)
        assertEquals(QuickStart.OPEN_MINUTES * 60, p.steps[0].durationSeconds)
        val slow = QuickStart.build(1.0, 0.0, Goal.Time(5), warmUp = true, coolDown = false, limits = limits)
        assertEquals(0.8, slow.steps[0].speedKph) // warm-up never below the machine minimum
    }
}
