package com.ofit.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Writes down the last crash.
 *
 * Without this, a crash on a phone that is not attached to a computer leaves nothing behind: the
 * stack trace goes to a buffer that the next launch overwrites. Writing it to the app's own
 * storage means the reason can be read on the device, and shown in the app, which is the only
 * practical way to diagnose a crash when there is no debugger attached.
 *
 * The file is written before the process dies, and the previous handler is still called, so the
 * system still shows its own crash dialog.
 */
object CrashLog {

    private const val FILE_NAME = "last-crash.txt"

    /** Install the handler. Call this first, before anything else is set up. */
    fun install(context: Context) {
        val directory = context.applicationContext.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { writer ->
                    error.printStackTrace(PrintWriter(writer))
                }.toString()
                File(directory, FILE_NAME).writeText("${thread.name}\n\n$trace")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash, or nothing when there has not been one. */
    fun read(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return text.takeIf { it.isNotBlank() }
    }

    /** Forget the last crash. */
    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }
}
