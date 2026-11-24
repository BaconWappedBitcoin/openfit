package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZoneTimelineTest {
    @Test
    fun `zone changes become coloured stretches up to now, gaps without a reading left out`() {
        val t = ZoneTimeline()
        t.record(0f, 1)
        t.record(5f, 1) // no change: no new point
        t.record(30f, 2)
        t.record(60f, null) // strap dropped
        t.record(70f, 3)
        assertEquals(
            listOf(
                ZoneTimeline.Segment(0f, 30f, 1),
                ZoneTimeline.Segment(30f, 60f, 2),
                ZoneTimeline.Segment(70f, 90f, 3),
            ),
            t.segments(90f),
        )
    }

    @Test
    fun `a jump back starts over, and no reading means nothing to draw`() {
        val t = ZoneTimeline()
        assertTrue(t.isEmpty)
        t.record(100f, null)
        assertTrue(t.isEmpty)
        t.record(120f, 2)
        t.record(10f, 1)
        assertEquals(listOf(ZoneTimeline.Segment(10f, 20f, 1)), t.segments(20f))
    }
}
