package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HeartRateWindowTest {
    @Test
    fun `averages the readings in the window, ignoring gaps`() {
        val w = HeartRateWindow()
        listOf(null, 110, null, 120, 0, 131).forEach(w::add)
        assertEquals(120, w.take())
        assertNull(w.take(), "a new window starts empty")
    }

    @Test
    fun `no reading, no value`() {
        val w = HeartRateWindow()
        w.add(null)
        assertNull(w.take())
    }
}
