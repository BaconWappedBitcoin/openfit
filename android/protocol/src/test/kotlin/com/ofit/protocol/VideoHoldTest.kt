package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoHoldTest {

    @Test
    fun `a video that plays normally never holds`() {
        val hold = VideoHold()
        for (i in 0..20) assertFalse(hold.update(i * 500L, i * 0.5, shouldPlay = true))
    }

    @Test
    fun `a stuck video (an ad) holds after 2 s and lets go when it moves`() {
        val hold = VideoHold()
        hold.update(0, 10.0, true)
        assertFalse(hold.update(1_500, 10.0, true))
        assertTrue(hold.update(2_000, 10.0, true))
        assertTrue(hold.update(30_000, 10.0, true))
        assertFalse(hold.update(30_500, 10.5, true), "the video moved again")
    }

    @Test
    fun `pausing on purpose is not a hold`() {
        val hold = VideoHold()
        hold.update(0, 10.0, true)
        assertFalse(hold.update(5_000, 10.0, shouldPlay = false))
        assertFalse(hold.update(6_000, 10.0, shouldPlay = true), "clock restarts on resume")
        assertTrue(hold.update(8_000, 10.0, true))
    }

    @Test
    fun `a hold gives up after 2 minutes so the workout cannot freeze for ever`() {
        val hold = VideoHold()
        hold.update(0, 10.0, true)
        assertTrue(hold.update(2_000, 10.0, true))
        assertTrue(hold.update(100_000, 10.0, true))
        assertFalse(hold.update(122_000, 10.0, true))
        assertFalse(hold.update(200_000, 10.0, true), "stays released until the video moves")
        assertFalse(hold.update(200_500, 10.6, true))
        assertTrue(hold.update(203_000, 10.6, true), "a later stall can hold again")
    }

    @Test
    fun `a seek backwards counts as movement`() {
        val hold = VideoHold()
        hold.update(0, 100.0, true)
        assertFalse(hold.update(1_900, 40.0, true))
    }
}
