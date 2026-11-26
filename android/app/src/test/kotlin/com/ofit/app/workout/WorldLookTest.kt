package com.ofit.app.workout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class WorldLookTest {
    @Test
    fun `chosen settings are kept and surprises are real choices`() {
        val picked = WorldLook("moor", "night", "rain")
        assertEquals(picked, WorldLook.resolve(picked, Random(1)))
        val surprise = WorldLook(WorldLook.SURPRISE, WorldLook.SURPRISE, WorldLook.SURPRISE)
        repeat(50) { i ->
            val look = WorldLook.resolve(surprise, Random(i))
            assertTrue(WorldLook.LANDS.any { it.first == look.land }, look.toString())
            assertTrue(WorldLook.TIMES.any { it.first == look.time }, look.toString())
            assertTrue(WorldLook.WEATHERS.any { it.first == look.weather }, look.toString())
        }
    }

    @Test
    fun `the same draw gives the same surprise`() {
        val surprise = WorldLook(WorldLook.SURPRISE, "day", WorldLook.SURPRISE)
        assertEquals(WorldLook.resolve(surprise, Random(42)), WorldLook.resolve(surprise, Random(42)))
    }
}
