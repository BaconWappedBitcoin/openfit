package com.ofit.app.settings

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup of everything the runner set up, as one file they keep where they like: Google Drive
 * through Android's file picker, a USB stick, another app (user request 2026-11-24: settings must
 * persist on a new device). Profiles, settings, workouts, history and the coach's look come along.
 * Left out, as in the automatic Android backup (res/xml/data_extraction_rules.xml): saved keys
 * (sealed by this tablet's Keystore), the treadmill link and "workout running" flag, WebView files.
 */
object BackupFiles {
    const val MIME = "application/zip"
    private const val MANIFEST = "openfit-backup.json"
    private val SKIP_PREFS = setOf("ofit_secrets.xml", "ofit_device.xml", "WebViewChromiumPrefs.xml", "AwOriginVisitLoggerPrefs.xml")
    private val SKIP_FILES = setOf("profileInstalled")

    fun suggestedName(): String =
        "openfit-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date()) + ".zip"

    private fun keepPref(name: String) = name.endsWith(".xml") && name !in SKIP_PREFS
    private fun keepFile(relative: String) = relative.substringBefore('/') !in SKIP_FILES

    /** Write the backup of [prefsDir] and [filesDir] to [out]. Returns how many files went in. */
    fun write(prefsDir: File, filesDir: File, out: OutputStream, appVersion: String = ""): Int {
        var count = 0
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            val version = appVersion.replace(Regex("[^0-9A-Za-z._-]"), "")
            zip.write(("{\"app\":\"OpenFit\",\"version\":1,\"appVersion\":\"$version\",\"createdAt\":${System.currentTimeMillis()}}").toByteArray())
            zip.closeEntry()
            prefsDir.listFiles()?.filter { it.isFile && keepPref(it.name) }?.sortedBy { it.name }?.forEach { f ->
                zip.putNextEntry(ZipEntry("prefs/" + f.name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry(); count++
            }
            filesDir.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { f ->
                val rel = f.relativeTo(filesDir).invariantSeparatorsPath
                if (!keepFile(rel)) return@forEach
                zip.putNextEntry(ZipEntry("files/$rel")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry(); count++
            }
        }
        return count
    }

    class NotABackup(message: String) : Exception(message)

    /**
     * Replace the runner's data in [prefsDir] and [filesDir] with the backup in [input]. The backup is
     * read and checked completely before anything is changed. Returns how many files were restored.
     */
    fun restore(input: InputStream, prefsDir: File, filesDir: File): Int {
        val entries = LinkedHashMap<String, ByteArray>()
        var manifest: String? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name
                // Nothing outside the app's own folders, whatever the file says.
                if (name.contains("..") || name.startsWith("/") || name.contains('\\')) throw NotABackup("This file has paths OpenFit won't write.")
                val bytes = zip.readBytes()
                if (bytes.size > 20 * 1024 * 1024) throw NotABackup("A file in this backup is too large.")
                when {
                    name == MANIFEST -> manifest = String(bytes)
                    name.startsWith("prefs/") && keepPref(name.removePrefix("prefs/")) && !name.removePrefix("prefs/").contains('/') -> entries[name] = bytes
                    name.startsWith("files/") && keepFile(name.removePrefix("files/")) -> entries[name] = bytes
                }
            }
        }
        if (manifest?.let { Regex(""""app"\s*:\s*"OpenFit"""").containsMatchIn(it) } != true) throw NotABackup("That file is not an OpenFit backup.")

        // Clear what a backup replaces (never the keys, the treadmill link or WebView's files).
        prefsDir.listFiles()?.filter { it.isFile && keepPref(it.name) }?.forEach { it.delete() }
        filesDir.listFiles()?.filter { keepFile(it.name) }?.forEach { it.deleteRecursively() }
        prefsDir.mkdirs()
        for ((name, bytes) in entries) {
            val target = if (name.startsWith("prefs/")) File(prefsDir, name.removePrefix("prefs/")) else File(filesDir, name.removePrefix("files/"))
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        return entries.size
    }

    fun prefsDir(context: Context) = File(context.applicationInfo.dataDir, "shared_prefs")

    /** Whether a workout is running now (a restore then waits: it restarts the app). */
    fun workoutRunning(context: Context): Boolean =
        context.getSharedPreferences("ofit_device", Context.MODE_PRIVATE).getBoolean("workout_running", false)

    /** Start OpenFit again in a moment and end this process, so the restored settings are read fresh. */
    fun restartApp(context: Context) {
        // Started while OpenFit is in front (a delayed start from an alarm is blocked by Android, seen
        // 2026-11-24); the system opens it in a fresh process once this one has ended.
        val component = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0)
    }
}
