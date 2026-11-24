package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalysisProgressTest {
    @Test
    fun `grows with time and never claims to be done`() {
        assertEquals(0f, AnalysisProgress.estimate(0, 60_000))
        assertEquals(0.5f, AnalysisProgress.estimate(30_000, 60_000), 0.001f)
        val late = AnalysisProgress.estimate(600_000, 60_000)
        assertTrue(late > 0.8f && late <= 0.95f)
        assertTrue(AnalysisProgress.estimate(70_000, 60_000) < AnalysisProgress.estimate(90_000, 60_000))
    }

    @Test
    fun stages() {
        assertEquals("Fetching the video", AnalysisProgress.stage(0.05f))
        assertEquals("Building the steps", AnalysisProgress.stage(0.9f))
    }
}
