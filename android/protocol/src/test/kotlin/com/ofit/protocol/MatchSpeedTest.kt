package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MatchSpeedTest {
    @Test
    fun `on plan plays at normal speed`() {
        assertEquals(1f, MatchSpeed.rate(8.0, 8.0))
        assertEquals(1f, MatchSpeed.rate(9.25, 9.17), "a belt reading a hair off the plan stays at 1x")
    }

    @Test
    fun `faster or slower than the plan shows, in 0_05 steps`() {
        assertEquals(1.25f, MatchSpeed.rate(10.0, 8.0))
        assertEquals(1.2f, MatchSpeed.rate(9.6, 8.0))
        assertEquals(0.75f, MatchSpeed.rate(6.0, 8.0))
        // One +0.5 mph press at 5.7 mph (found live: quarter steps hid it).
        assertEquals(1.1f, MatchSpeed.rate(6.2 * 1.609344, 5.7 * 1.609344))
    }

    @Test
    fun `kept to half and double speed`() {
        assertEquals(2f, MatchSpeed.rate(20.0, 5.0))
        assertEquals(0.5f, MatchSpeed.rate(1.0, 8.0))
        assertEquals(1f, MatchSpeed.rate(0.0, 8.0))
    }

    @Test
    fun `remaining time follows the playback speed`() {
        assertEquals(600_000L, MatchSpeed.realMillis(600_000, 1f))
        assertEquals(480_000L, MatchSpeed.realMillis(600_000, 1.25f))
        assertEquals(1_200_000L, MatchSpeed.realMillis(600_000, 0.5f))
        assertEquals(600_000L, MatchSpeed.realMillis(600_000, 0f))
    }

    @Test
    fun `the reference is the video's pace, else the workout's average`() {
        val steps = listOf(WorkoutStep(4.0, 0.0, 600), WorkoutStep(12.0, 0.0, 200))
        assertEquals(6.0, MatchSpeed.referenceKph(WorkoutProgram("x", steps))!!, 1e-9)
        assertEquals(5.0, MatchSpeed.referenceKph(WorkoutProgram("x", steps, videoPaceKph = 5.0))!!, 1e-9)
        // A fast step on a slow workout plays the video fast.
        assertEquals(2f, MatchSpeed.rate(12.0, MatchSpeed.referenceKph(WorkoutProgram("x", steps))!!))
    }

    @Test
    fun `the video pace is saved with the workout`() {
        val p = WorkoutProgram("x", listOf(WorkoutStep(5.0, 0.0, 60)), videoPaceKph = 4.8)
        assertEquals(4.8, WorkoutCodec.decode(WorkoutCodec.encode(p)).videoPaceKph)
        assertEquals(null, WorkoutCodec.decode(WorkoutCodec.encode(p.copy(videoPaceKph = null))).videoPaceKph)
    }
}
