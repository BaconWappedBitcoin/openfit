package com.ofit.app.workout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoachLookTest {
    private val brown = 0x6D4C41

    @Test
    fun `clothes close to the skin are swapped for a contrasting colour`() {
        val darkSkin = CoachLook.SKIN_TONES[4]
        assertTrue(CoachLook.tooCloseToSkin(brown, darkSkin))
        assertEquals(0x24272E, CoachLook.awayFromSkin(brown, darkSkin))
        // The darkest skin: charcoal is too close too, so navy.
        assertEquals(0x1E3A8A, CoachLook.awayFromSkin(brown, CoachLook.SKIN_TONES[5]))
        // A clear contrast is left alone.
        assertFalse(CoachLook.tooCloseToSkin(0x1E88E5, darkSkin))
        assertEquals(0x1E88E5, CoachLook.awayFromSkin(0x1E88E5, darkSkin))
    }

    @Test
    fun `the coach page never gets shorts in the skin colour`() {
        val look = CoachLook(style = CoachLook.Style.CLASSIC, bottom = brown, skin = CoachLook.SKIN_TONES[3])
        val q = look.query(0x6750A4)
        assertTrue(q.contains("&bottom=24272e"), q)
    }
}
