package com.ofit.app.workout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoachPersonaTest {
    @Test
    fun `a typed name is tidied`() {
        assertEquals("Maya", CoachPersona.clean("  Maya  "))
        assertEquals("Coach Leo", CoachPersona.clean("Coach   Leo"))
        assertEquals(CoachPersona.DEFAULT_NAME, CoachPersona.clean("   "))
        assertEquals(CoachPersona.MAX_NAME, CoachPersona.clean("x".repeat(50)).length)
    }

    @Test
    fun `long lines stay up longer, within limits`() {
        assertEquals(6_000L, bubbleMillis("Nice and steady."))
        val long = (1..40).joinToString(" ") { "word" }
        assertTrue(bubbleMillis(long) > 6_000L)
        assertEquals(16_000L, bubbleMillis((1..200).joinToString(" ") { "word" }))
    }
}
