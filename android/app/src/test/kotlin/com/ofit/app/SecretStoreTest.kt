package com.ofit.app

import com.ofit.app.settings.SecretStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecretStoreTest {
    private val good = "AIza" + "Ab9_-".repeat(7)

    @Test
    fun `accepts the Google key shape only (same rule as the backend)`() {
        assertEquals(39, good.length)
        assertTrue(SecretStore.looksLikeGeminiKey(good))
        assertTrue(SecretStore.looksLikeGeminiKey("  $good "))
        assertFalse(SecretStore.looksLikeGeminiKey(good.dropLast(1)))
        assertFalse(SecretStore.looksLikeGeminiKey("sk-" + "a".repeat(36)))
        // Google's newer keys (user report 2026-11-24).
        assertTrue(SecretStore.looksLikeGeminiKey("AQ.Ab8RN6" + "x_Y-z.".repeat(8)))
        assertFalse(SecretStore.looksLikeGeminiKey("AQ.short"))
        assertFalse(SecretStore.looksLikeGeminiKey("AQ.has space" + "a".repeat(30)))
        assertFalse(SecretStore.looksLikeGeminiKey(""))
    }

    @Test
    fun `masking shows only the ends`() {
        val masked = SecretStore.masked(good)
        assertTrue(masked.startsWith("AIza") && masked.endsWith(good.takeLast(4)))
        assertFalse(masked.contains(good.substring(4, good.length - 4)))
    }
}
