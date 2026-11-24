package com.ofit.protocol

import com.ofit.protocol.ProgramEdit.Lane
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgramEditTest {

    private val limits = SafetyLimits(
        maxSpeedKph = 12.0, maxInclinePercent = 10.0,
        machineMaxSpeedKph = 16.1, machineMinSpeedKph = 0.8,
        machineMaxInclinePercent = 10.0, machineMinInclinePercent = 0.0,
    )
    private val three = listOf(WorkoutStep(4.0, 0.0, 60), WorkoutStep(6.0, 2.0, 120), WorkoutStep(4.0, 0.0, 60))
    private fun total(s: List<WorkoutStep>) = s.sumOf { it.durationSeconds }

    @Test
    fun `a new video workout is one step the length of the video`() {
        assertEquals(listOf(WorkoutStep(4.8, 0.0, 2400)), ProgramEdit.forVideo(2400, 4.8, 0.0))
    }

    @Test
    fun `step lookup and start times`() {
        assertEquals(0, ProgramEdit.stepAt(three, 0.0))
        assertEquals(1, ProgramEdit.stepAt(three, 60.0))
        assertEquals(2, ProgramEdit.stepAt(three, 999.0))
        assertEquals(180, ProgramEdit.startOf(three, 2))
    }

    @Test
    fun `levels are clamped to the limits and snapped`() {
        assertEquals(12.0, ProgramEdit.setLevel(three, 1, Lane.SPEED, 30.0, limits)[1].speedKph)
        assertEquals(0.8, ProgramEdit.setLevel(three, 1, Lane.SPEED, 0.0, limits)[1].speedKph)
        assertEquals(6.3, ProgramEdit.setLevel(three, 1, Lane.SPEED, 6.27, limits)[1].speedKph)
        assertEquals(3.5, ProgramEdit.setLevel(three, 1, Lane.INCLINE, 3.6, limits)[1].inclinePercent)
        assertEquals(0.0, ProgramEdit.setLevel(three, 1, Lane.INCLINE, -2.0, limits)[1].inclinePercent)
    }

    @Test
    fun `moving a boundary keeps the total and a minimum step`() {
        val moved = ProgramEdit.moveBoundary(three, 1, 90)
        assertEquals(listOf(90, 90, 60), moved.map { it.durationSeconds })
        assertEquals(total(three), total(moved))
        val squeezed = ProgramEdit.moveBoundary(three, 1, 500)
        assertEquals(listOf(175, 5, 60), squeezed.map { it.durationSeconds })
        assertEquals(three, ProgramEdit.moveBoundary(three, 0, 10)) // no boundary before the first step
    }

    @Test
    fun `split and delete keep the total`() {
        val split = ProgramEdit.split(three, 100)
        assertEquals(listOf(60, 40, 80, 60), split.map { it.durationSeconds })
        assertEquals(split[1].speedKph, split[2].speedKph)
        assertEquals(three, ProgramEdit.split(three, 62)) // too close to the step's start
        val deleted = ProgramEdit.delete(split, 2)
        assertEquals(listOf(60, 120, 60), deleted.map { it.durationSeconds })
        assertEquals(listOf(180, 60), ProgramEdit.delete(three, 0).map { it.durationSeconds }.let { listOf(it[0], it[1]) })
        assertEquals(total(three), total(ProgramEdit.delete(three, 0)))
        assertEquals(1, ProgramEdit.delete(listOf(three[0]), 0).size)
    }
}
