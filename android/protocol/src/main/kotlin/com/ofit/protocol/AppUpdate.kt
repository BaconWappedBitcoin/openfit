package com.ofit.protocol

/**
 * Updates from the project's GitHub releases (user request 2026-11-24, as Nuvio does): which
 * release is newer than the app, and the APK's SHA-256 from the release notes, so a download is
 * checked before it is installed.
 */
object AppUpdate {
    /** "v0.2.1" or "0.2.1" as numbers; anything after the numbers ("-beta") is ignored. Null if unreadable. */
    fun parse(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").removePrefix("V").takeWhile { it.isDigit() || it == '.' }
        if (core.isEmpty()) return null
        val parts = core.split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
        return parts.ifEmpty { null }
    }

    /** True when [candidate] is a later version than [current] ("0.2.10" is after "0.2.9"). */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private val SHA = Regex("SHA-?256:?\\s*`?([0-9a-fA-F]{64})`?")

    /** The SHA-256 the release notes give for the APK, lower case, or null. */
    fun sha256From(notes: String): String? = SHA.find(notes)?.groupValues?.get(1)?.lowercase()

    /** The APK among a release's files: the first whose name ends in .apk. */
    fun apkName(names: List<String>): String? = names.firstOrNull { it.endsWith(".apk", ignoreCase = true) }
}
