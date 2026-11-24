package com.ofit.app.data

import android.content.Context
import com.ofit.protocol.WorkoutCodec
import com.ofit.protocol.WorkoutProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * A workout program saved on the device.
 */
data class SavedWorkout(
    /** The file name safe identifier of the program. */
    val id: String,
    /** The program itself. */
    val program: WorkoutProgram,
    /** When the program was last written. */
    val updatedAtMillis: Long,
)

/**
 * Stores workout programs as JSON files in the app's own storage.
 *
 * A program is data, not media: a list of speed, incline, and duration steps. It needs no
 * network, no account, and no protected content, which is what makes a workout usable offline.
 *
 * The files are ordinary text, so a program can be read, edited, or copied out of the app's
 * private directory.
 */
class WorkoutRepository(context: Context) {

    private val directory = File(context.filesDir, "workouts").apply { mkdirs() }

    /** Every saved program, most recently changed first. */
    suspend fun list(): List<SavedWorkout> = withContext(Dispatchers.IO) {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?: return@withContext emptyList()
        files.mapNotNull { file -> read(file) }
            .sortedByDescending { it.updatedAtMillis }
    }

    /**
     * Save a program, replacing any program with the same name and the same content.
     *
     * Two different workouts can share a title (the same route in two series, with different
     * programs), so a name that is already taken by
     * something else gets a number on the end. The user still sees the title; the file behind it
     * is not quietly overwritten.
     */
    suspend fun save(program: WorkoutProgram): SavedWorkout = withContext(Dispatchers.IO) {
        val encoded = WorkoutCodec.encode(program)
        val base = idFor(program.name)
        var id = base
        var file = File(directory, "$id.json")
        var suffix = 2
        while (file.isFile && file.readText() != encoded) {
            id = "$base-$suffix"
            file = File(directory, "$id.json")
            suffix++
        }
        file.writeText(encoded)
        SavedWorkout(id = id, program = program, updatedAtMillis = file.lastModified())
    }

    /** Load one program, or nothing when it is not there. */
    suspend fun load(id: String): SavedWorkout? = withContext(Dispatchers.IO) {
        read(File(directory, "$id.json"))
    }

    /** Remove a program. Returns whether there was anything to remove. */
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(directory, "$id.json")
        if (file.isFile) {
            file.delete()
        } else {
            false
        }
    }

    private fun read(file: File): SavedWorkout? {
        if (!file.isFile) return null
        val program = runCatching { WorkoutCodec.decode(file.readText()) }.getOrNull() ?: return null
        return SavedWorkout(
            id = file.nameWithoutExtension,
            program = program,
            updatedAtMillis = file.lastModified(),
        )
    }

    companion object {
        /** A file name safe identifier for a program name. */
        fun idFor(name: String): String {
            val slug = name.trim().lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
            return slug.ifEmpty { "workout" }
        }
    }
}
