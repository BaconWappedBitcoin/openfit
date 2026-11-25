package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoBoundsTest {
    private fun reading(i: Int, part: String, speed: Double = 9.0) =
        VideoProgramBuilder.WindowReading(i, i * 30, 30, speed, 0.0, 0.5, "flat", "", part)

    private val parts = listOf("intro", "intro", "running", "running", "running", "running", "running", "running", "outro", "outro")

    @Test
    fun `intro and outro windows become a suggested start and end`() {
        val readings = parts.mapIndexed { i, p -> reading(i, p, if (p == "running") 9.0 else 1.0) }
        assertEquals(60 to 240, VideoBounds.fromReadings(readings))
        val program = VideoProgramBuilder.build(readings, "Run")
        assertEquals(60, program.introEndSeconds)
        assertEquals(240, program.outroStartSeconds)
        // The pace leaves the intro and outro out, and nothing is cut from the steps.
        assertEquals(9.0, program.videoPaceKph)
        assertEquals(300, program.totalDurationSeconds)
    }

    @Test
    fun `unbelievable bounds are ignored`() {
        assertEquals(null to null, VideoBounds.fromReadings((0 until 6).map { reading(it, "intro") }))
        assertEquals(null to null, VideoBounds.fromReadings((0 until 6).map { reading(it, "running") }))
        assertEquals(null to null, VideoBounds.clean(300, 320, 600))
        assertEquals(30 to null, VideoBounds.clean(30, 700, 600))
    }

    @Test
    fun `bounds survive saving and loading`() {
        val program = WorkoutProgram("Run", listOf(WorkoutStep(8.0, 0.0, 600))).withBounds(45, 540)
        val back = WorkoutCodec.decode(WorkoutCodec.encode(program))
        assertEquals(45, back.introEndSeconds)
        assertEquals(540, back.outroStartSeconds)
        assertNull(WorkoutCodec.decode(WorkoutCodec.encode(WorkoutProgram("Run", listOf(WorkoutStep(8.0, 0.0, 600))))).introEndSeconds)
    }

    @Test
    fun `skipping starts at the intro's end and ends at the outro`() {
        val program = WorkoutProgram("Run", listOf(WorkoutStep(8.0, 0.0, 600))).withBounds(45, 540)
        assertEquals(45, VideoBounds.startSeconds(program, skip = true))
        assertNull(VideoBounds.startSeconds(program, skip = false))
        assertTrue(VideoBounds.reachedOutro(program, skip = true, elapsedSeconds = 540))
        assertFalse(VideoBounds.reachedOutro(program, skip = true, elapsedSeconds = 539))
        assertFalse(VideoBounds.reachedOutro(program, skip = false, elapsedSeconds = 590))
    }
}
