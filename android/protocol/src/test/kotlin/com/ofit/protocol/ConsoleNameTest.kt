package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * How a Bluetooth device is told apart from an iFit console.
 *
 * The rule: the uppercased name of the device is compared with a list of
 * console names. What is checked here is that the matching is a prefix match, so that a console
 * which appends anything to its name is still recognised.
 */
class ConsoleNameTest {

    @Test
    fun `the treadmill name is a console`() {
        assertTrue(Gatt.looksLikeConsole("I_TL"))
    }

    @Test
    fun `a console name is matched whatever its case`() {
        assertTrue(Gatt.looksLikeConsole("i_tl"))
        assertTrue(Gatt.looksLikeConsole("I_tl"))
    }

    @Test
    fun `a console that adds a suffix is still a console`() {
        assertTrue(Gatt.looksLikeConsole("I_TL_32F6"))
        assertTrue(Gatt.looksLikeConsole("I_TL-1234"))
        assertTrue(Gatt.looksLikeConsole("I_TL 32F6"))
    }

    @Test
    fun `the other console families are consoles too`() {
        assertTrue(Gatt.looksLikeConsole("ELLIP"))
        assertTrue(Gatt.looksLikeConsole("TREAD"))
        assertTrue(Gatt.looksLikeConsole("BIKE"))
        assertTrue(Gatt.looksLikeConsole("STRIDE"))
        assertTrue(Gatt.looksLikeConsole("FITPRO SUPPORTED"))
    }

    @Test
    fun `a headphone is not a console`() {
        assertFalse(Gatt.looksLikeConsole("Bose QC45"))
        assertFalse(Gatt.looksLikeConsole("Galaxy Buds"))
    }

    @Test
    fun `a phone is not a console`() {
        assertFalse(Gatt.looksLikeConsole("Pixel 8"))
    }

    @Test
    fun `a name that is not there is not a console`() {
        assertFalse(Gatt.looksLikeConsole(null))
        assertFalse(Gatt.looksLikeConsole(""))
        assertFalse(Gatt.looksLikeConsole("   "))
    }

    @Test
    fun `the console service is the one the vendor uses`() {
        assertTrue(Gatt.CONSOLE_SERVICE == "00001533-1412-efde-1523-785feabcd123")
        assertTrue(Gatt.HOST_WRITE == "00001534-1412-efde-1523-785feabcd123")
        assertTrue(Gatt.MACHINE_NOTIFY == "00001535-1412-efde-1523-785feabcd123")
    }
}
