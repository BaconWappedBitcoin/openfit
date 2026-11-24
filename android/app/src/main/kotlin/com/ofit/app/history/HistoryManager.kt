package com.ofit.app.history

import android.content.Context
import com.ofit.app.heart.HeartRateSummary
import com.ofit.protocol.HistoryCodec
import com.ofit.protocol.HistoryEntry
import com.ofit.protocol.TreadmillState
import com.ofit.protocol.WorkoutEngineSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Keeps a record of every finished workout (spec 002 "Stats history across workouts").
 *
 * It watches the workout engine and the console once a second, and when a workout ends after at
 * least a minute, writes one entry to `history.json` in the app's private files. Previews are not
 * recorded: no belt moved.
 */
class HistoryManager(
    context: Context,
    private val scope: CoroutineScope,
    private val consoleState: StateFlow<TreadmillState>,
    private val workout: StateFlow<WorkoutEngineSnapshot>,
    private val heartSummary: StateFlow<HeartRateSummary>,
    private val preview: StateFlow<Boolean>,
    fileName: String = "history.json",
) {
    private val dir = context.applicationContext.filesDir
    private var file = File(dir, fileName)

    /** Show another profile's history. A workout in progress is credited to the profile it ends in. */
    fun switchProfile(fileName: String) {
        file = File(dir, fileName)
        _entries.value = load()
    }

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    // The workout being watched.
    private var active = false
    private var startedAt = 0L
    private var name = ""
    private var distance = 0.0
    private var calories: Double? = null
    private var vertical = 0.0
    private var lastDistance: Long? = null
    private var lastElapsed = 0L

    init {
        scope.launch {
            while (true) {
                runCatching { tick() }
                delay(TICK_MILLIS)
            }
        }
    }

    private fun tick() {
        val snapshot = workout.value
        val state = consoleState.value
        if (snapshot.isActive && !preview.value) {
            if (!active || snapshot.totalElapsedMillis + 2_000 < lastElapsed) begin(snapshot)
            lastElapsed = snapshot.totalElapsedMillis
            // The console zeroes distance and calories on its results screen, so the last good values
            // are kept.
            state.distanceMeters?.takeIf { it > 0 }?.let { now ->
                val before = lastDistance
                val incline = state.displayedInclinePercent ?: 0.0
                if (before != null && now > before && incline > 0) vertical += (now - before) * incline / 100.0
                lastDistance = now
                distance = now.toDouble()
            }
            state.calories?.takeIf { it > 0 }?.let { calories = it }
        } else if (active) {
            active = false
            if (snapshot.isFinished) finish(snapshot)
        }
    }

    private fun begin(snapshot: WorkoutEngineSnapshot) {
        active = true
        startedAt = System.currentTimeMillis() - snapshot.totalElapsedMillis
        name = snapshot.programName
        distance = 0.0
        calories = null
        vertical = 0.0
        lastDistance = null
    }

    private fun finish(snapshot: WorkoutEngineSnapshot) {
        val seconds = snapshot.totalElapsedMillis / 1000
        if (seconds < HistoryCodec.MIN_DURATION_SECONDS) return
        val hr = heartSummary.value
        val entry = HistoryEntry(
            startedAtMillis = startedAt,
            name = name,
            durationSeconds = seconds,
            distanceMeters = distance,
            calories = calories,
            verticalGainMeters = vertical,
            averageHeartRate = hr.averageBpm,
            maxHeartRate = hr.maxBpm,
            outcome = snapshot.state.name,
        )
        save(listOf(entry) + _entries.value)
    }

    /** Delete one entry. */
    fun delete(entry: HistoryEntry) {
        save(_entries.value.filterNot { it.startedAtMillis == entry.startedAtMillis })
    }

    private fun save(list: List<HistoryEntry>) {
        _entries.value = list.sortedByDescending { it.startedAtMillis }
        scope.launch {
            runCatching {
                val target = file
                val tmp = File(dir, target.name + ".tmp")
                tmp.writeText(HistoryCodec.encode(list.sortedByDescending { it.startedAtMillis }))
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
            }.onFailure { android.util.Log.w("ofit", "could not save the history", it) }
        }
    }

    private fun load(): List<HistoryEntry> =
        runCatching { if (file.exists()) HistoryCodec.decode(file.readText()) else emptyList() }.getOrDefault(emptyList())

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
