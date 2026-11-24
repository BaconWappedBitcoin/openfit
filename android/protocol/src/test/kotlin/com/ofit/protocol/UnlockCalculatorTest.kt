package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The computed unlock must equal what the vendor application sent, from the same console answers.
 * All frames are from a Bluetooth capture on the user's NordicTrack T 6.5 (2026-11-23).
 */
class UnlockCalculatorTest {

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val deviceInfo = hex("04 1d 81 02 51 01 03 00 00 00 00 00 0f db fe fb db fc fb fb 0c e7 1f 38 c0 d3 18 18 b6")
    private val systemInfo = hex("04 21 82 02 64 00 01 ed ae 0a 00 d6 35 06 00 77 00 33 e8 03 00 24 f4 00 f4 01 00 00 01 02 00 00 69")
    private val versionInfo = hex("04 1c 84 02 51 d2 00 30 2e 31 2e 30 36 31 32 32 30 31 37 2e 30 39 30 38 01 2a 03 46")

    @Test
    fun `reproduces the unlock the vendor application sent`() {
        val expected = "0701cec4b0aaa2a8949696aca8a2bad0dccefe14003a52786486a6fc18324aa088020000"
        val computed = UnlockCalculator.compute(deviceInfo, systemInfo, versionInfo)
        assertEquals(expected, computed?.let { Framing.toHex(it) })
    }

    @Test
    fun `too short a reply gives no unlock rather than a wrong one`() {
        assertNull(UnlockCalculator.compute(deviceInfo.copyOf(8), systemInfo, versionInfo))
        assertNull(UnlockCalculator.compute(deviceInfo, systemInfo.copyOf(12), versionInfo))
    }
}
