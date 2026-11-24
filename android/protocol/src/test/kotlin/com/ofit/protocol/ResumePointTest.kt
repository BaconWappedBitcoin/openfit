package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResumePointTest {
    private val now = 1_000_000_000L
    private fun point(elapsed: Long, total: Long = 1_560_000, savedAgo: Long = 60_000) =
        ResumePoint("paris", "Paris", elapsed, total, now - savedAgo)

    @Test
    fun `a recent workout part way through is offered`() {
        assertTrue(point(750_000).worthOffering(now))
    }

    @Test
    fun `not offered when barely started, nearly done, or long ago`() {
        assertFalse(point(30_000).worthOffering(now), "first minute")
        assertFalse(point(1_530_000).worthOffering(now), "under a minute left")
        assertFalse(point(750_000, savedAgo = 13 * 3_600_000L).worthOffering(now), "older than 12 h")
        assertFalse(point(750_000, savedAgo = -5_000).worthOffering(now), "from the future")
    }
}
