package com.ofit.protocol

import com.ofit.protocol.VideoProgramBuilder.Row
import com.ofit.protocol.VideoProgramBuilder.WindowReading
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The same cases as the backend's test/build.test.ts, so tablet and server build alike. */
class VideoProgramBuilderTest {
    private fun reading(i: Int, speed: Double?, incline: Double?) =
        WindowReading(i, i * 30, 30, speed, incline, 0.5)

    @Test
    fun `agreeing windows merge into one step`() {
        val p = VideoProgramBuilder.build((0..3).map { reading(it, 8.0, 1.0) }, "x")
        assertEquals(1, p.steps.size)
        assertEquals(120, p.steps[0].durationSeconds)
    }

    @Test
    fun `output is held inside the limits`() {
        val p = VideoProgramBuilder.build((0..2).map { reading(it, 30.0, 40.0) }, "x", smooth = 1)
        assertEquals(12.0, p.steps[0].speedKph)
        assertEquals(10.0, p.steps[0].inclinePercent)
    }

    @Test
    fun `a still moment is raised to the minimum, a missing reading is not zero`() {
        val p = VideoProgramBuilder.build(listOf(reading(0, 0.0, 0.0), reading(1, null, null), reading(2, 8.0, 0.0)), "x", smooth = 1)
        assertEquals(1.6, p.steps[0].speedKph)
        assertTrue(p.steps.all { it.speedKph >= 1.6 })
    }

    @Test
    fun `a lone outlier is replaced by its neighbours`() {
        val p = VideoProgramBuilder.build(listOf(reading(0, 8.0, 0.0), reading(1, 12.0, 9.0), reading(2, 8.0, 0.0)), "x", smooth = 1)
        assertEquals(1, p.steps.size)
        assertEquals(8.0, p.steps[0].speedKph)
    }

    @Test
    fun `no readings at all is an error`() {
        assertThrows<IllegalArgumentException> { VideoProgramBuilder.build(listOf(reading(0, null, null)), "x") }
    }

    @Test
    fun `a long title is cut at a word with no trailing dash`() {
        assertEquals(
            "Virtual Run 4k - Interlaken - Scenery Lake Switzerland",
            VideoProgramBuilder.tidyTitle("Virtual Run 4k - Interlaken - Scenery Lake Switzerland - Virtual Running Video for Treadmill"),
        )
        assertEquals("Short run", VideoProgramBuilder.tidyTitle("Short run"))
        assertTrue(VideoProgramBuilder.tidyTitle("x".repeat(200)).length <= 60)
    }

    @Test
    fun `the video's own pace is the time-weighted average of what was read`() {
        val p = VideoProgramBuilder.build(listOf(reading(0, 4.0, 0.0), reading(1, 6.0, 0.0), reading(2, null, 0.0)), "x", smooth = 1)
        assertEquals(5.0, p.videoPaceKph)
    }

    @Test
    fun `speed ramps gradually between readings instead of jumping`() {
        val p = VideoProgramBuilder.build(listOf(reading(0, 6.0, 0.0), reading(1, 9.0, 0.0)), "x", smooth = 1)
        val speeds = p.steps.map { it.speedKph }
        assertTrue(p.steps.size >= 4, "expected a ramp, got ${p.steps.size}")
        assertTrue(speeds.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(6.0, speeds.first())
        assertEquals(9.0, speeds.last())
        assertEquals(60, p.steps.sumOf { it.durationSeconds })
    }

    @Test
    fun `interpolate fills in on a straight line between window middles`() {
        val out = VideoProgramBuilder.interpolate(listOf(Row(30.0, 6.0, 0.0), Row(30.0, 9.0, 3.0)), 10.0)
        assertEquals(listOf(6.0, 6.0, 7.0, 8.0, 9.0, 9.0), out.map { it.speed })
    }

    @Test
    fun `a long video stays inside the step limit`() {
        val readings = (0 until 240).map { reading(it, 5.0 + (it % 2) * 2, (it % 3) * 2.0) }
        assertTrue(VideoProgramBuilder.build(readings, "x", smooth = 1).steps.size <= 300)
    }

    @Test
    fun `windows cover the video in 30 second pieces`() {
        assertEquals(listOf(Triple(0, 0, 30), Triple(1, 30, 60), Triple(2, 60, 75)), VideoProgramBuilder.windows(75))
    }
}
