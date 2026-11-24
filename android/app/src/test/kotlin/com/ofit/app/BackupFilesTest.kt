package com.ofit.app

import com.ofit.app.settings.BackupFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupFilesTest {
    @TempDir lateinit var tmp: File

    private fun tablet(name: String): Pair<File, File> {
        val prefs = File(tmp, "$name/shared_prefs").apply { mkdirs() }
        val files = File(tmp, "$name/files").apply { mkdirs() }
        return prefs to files
    }

    @Test
    fun `a backup moves settings, workouts and history but never keys or the treadmill link`() {
        val (prefsA, filesA) = tablet("old")
        File(prefsA, "ofit_profiles.xml").writeText("<map>profiles</map>")
        File(prefsA, "ofit_secrets.xml").writeText("<map>sealed key</map>")
        File(prefsA, "ofit_device.xml").writeText("<map>workout_running</map>")
        File(filesA, "history.json").writeText("[1,2,3]")
        File(filesA, "workouts").mkdirs(); File(filesA, "workouts/paris.json").writeText("{}")
        File(filesA, "profileInstalled").writeText("x")
        val out = ByteArrayOutputStream()
        assertEquals(3, BackupFiles.write(prefsA, filesA, out))

        val (prefsB, filesB) = tablet("new")
        File(prefsB, "ofit_secrets.xml").writeText("<map>this tablet's key</map>")
        File(filesB, "workouts").mkdirs(); File(filesB, "workouts/old.json").writeText("{}")
        assertEquals(3, BackupFiles.restore(ByteArrayInputStream(out.toByteArray()), prefsB, filesB))

        assertEquals("<map>profiles</map>", File(prefsB, "ofit_profiles.xml").readText())
        assertEquals("<map>this tablet's key</map>", File(prefsB, "ofit_secrets.xml").readText())
        assertFalse(File(prefsB, "ofit_device.xml").exists())
        assertEquals("[1,2,3]", File(filesB, "history.json").readText())
        assertTrue(File(filesB, "workouts/paris.json").exists())
        assertFalse(File(filesB, "workouts/old.json").exists(), "a restore replaces, not merges")
    }

    @Test
    fun `a file that is not a backup, or reaches outside, changes nothing`() {
        val (prefs, files) = tablet("t")
        File(files, "history.json").writeText("keep")
        assertThrows<BackupFiles.NotABackup> { BackupFiles.restore(ByteArrayInputStream("not a zip".toByteArray()), prefs, files) }
        val evil = ByteArrayOutputStream()
        ZipOutputStream(evil).use { z ->
            z.putNextEntry(ZipEntry("openfit-backup.json")); z.write("""{"app":"OpenFit"}""".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("files/../../escape.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        assertThrows<BackupFiles.NotABackup> { BackupFiles.restore(ByteArrayInputStream(evil.toByteArray()), prefs, files) }
        assertEquals("keep", File(files, "history.json").readText())
    }
}
