package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppUpdateTest {
    @Test
    fun `newer versions are recognised`() {
        assertTrue(AppUpdate.isNewer("v0.2.2", "0.2.1"))
        assertTrue(AppUpdate.isNewer("v0.2.10", "0.2.9"))
        assertTrue(AppUpdate.isNewer("v1.0", "0.9.9"))
        assertFalse(AppUpdate.isNewer("v0.2.1", "0.2.1"))
        assertFalse(AppUpdate.isNewer("v0.2.0", "0.2.1"))
        assertFalse(AppUpdate.isNewer("0.2.1.0", "0.2.1"))
        assertTrue(AppUpdate.isNewer("v0.3.0-beta", "0.2.1"))
        assertFalse(AppUpdate.isNewer("nightly", "0.2.1"))
    }

    @Test
    fun `the checksum is read from the release notes`() {
        val sha = "ebe0a03dadacaa2cada677f9e2b7b3f31448d8dd5f005f6b5608335ccaa18ab7"
        assertEquals(sha, AppUpdate.sha256From("Some notes.\n\nSHA-256: `$sha`\n\nMore."))
        assertEquals(sha, AppUpdate.sha256From("SHA256 ${sha.uppercase()}"))
        assertNull(AppUpdate.sha256From("No checksum here."))
    }

    @Test
    fun `the apk is picked from the release files`() {
        assertEquals("OpenFit-0.2.2.apk", AppUpdate.apkName(listOf("notes.txt", "OpenFit-0.2.2.apk")))
        assertNull(AppUpdate.apkName(listOf("source.zip")))
    }
}
