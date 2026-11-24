package com.ofit.app

import com.ofit.app.profile.ProfileManager
import com.ofit.app.workout.HudStat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProfileAndHudTest {

    @Test
    fun `profile 1 keeps the old storage names, others get their own`() {
        assertEquals("ofit_settings", ProfileManager.scopedPrefsName("ofit_settings", 1))
        assertEquals("ofit_settings_p2", ProfileManager.scopedPrefsName("ofit_settings", 2))
        assertEquals("history.json", ProfileManager.scopedFileName("history", "json", 1))
        assertEquals("history_p3.json", ProfileManager.scopedFileName("history", "json", 3))
    }

    @Test
    fun `saved HUD tiles round-trip and bad input falls back to the default`() {
        val tiles = listOf(HudStat.HEART_RATE, HudStat.SPEED, HudStat.ELAPSED)
        assertEquals(tiles, HudStat.parse(HudStat.format(tiles)))
        assertEquals(HudStat.DEFAULT, HudStat.parse(null))
        assertEquals(HudStat.DEFAULT, HudStat.parse("SPEED,NOPE"))
        // Unknown names are dropped, duplicates removed, and at most 8 kept.
        assertEquals(listOf(HudStat.SPEED, HudStat.INCLINE, HudStat.DISTANCE), HudStat.parse("SPEED,GONE,INCLINE,SPEED,DISTANCE"))
        assertEquals(8, HudStat.parse(HudStat.entries.joinToString(",") { it.name }).size)
    }
}
