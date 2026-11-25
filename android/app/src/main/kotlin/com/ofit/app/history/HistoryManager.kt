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
    /** The demo treadmill is on: its workouts are recorded as no-treadmill ones (nothing real moved). */
    private val demo: StateFlow<Boolean> = MutableStateFlow(false),
    fileName: String = "history.json",
    /** The program running, for working out distance in a no-treadmill workout. */
    private val program: StateFlow<com.ofit.protocol.WorkoutProgram?> = MutableStateFlow(null),
    /** OpenFit's own calorie estimate (age, sex, weight, heart rate); preferred over the console's. */
    private val estimatedCalories: StateFlow<Double?> = MutableStateFlow(null),
    /** The heart rate shown now, for the workout's chart. */
    private val currentHeartRate: () -> Int? = { null },
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
    private var noTreadmill = false
    private var lastWall = 0L
    /** Time actually spent running (wall clock, pauses excluded), so seeking does not add minutes. */
    private var activeMillis = 0L
    private var lastActiveWall = 0L
    private val samples = mutableListOf<com.ofit.protocol.HistorySample>()
    private val heartWindow = com.ofit.protocol.HeartRateWindow()

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
        // A no-treadmill workout is recorded too, from the program's own numbers.
        val running = program.value
        val state = if (preview.value && running != null) {
            com.ofit.app.workout.previewMachineState(running, snapshot).copy(
                actualInclinePercent = snapshot.inclinePercent,
                targetInclinePercent = snapshot.inclinePercent,
            )
        } else {
            consoleState.value
        }
        if (snapshot.isActive) {
            // A new record only when a workout starts: a seek back (into the intro, or -10 s) is the
            // same workout and must not start the record again (user report 2026-11-24).
            if (!active) begin(snapshot)
            noTreadmill = preview.value || demo.value
            val wall = System.currentTimeMillis()
            if (snapshot.isRunning && lastActiveWall > 0) activeMillis += (wall - lastActiveWall).coerceIn(0, 5_000)
            lastActiveWall = wall
            lastElapsed = snapshot.totalElapsedMillis
            // The console zeroes distance and calories on its results screen, so the last good values
            // are kept.
            if (preview.value) {
                // No treadmill: distance is built up from the suggested speed over the time actually
                // spent (wall clock while running), so seeking or fast-forwarding does not add miles.
                val now = System.currentTimeMillis()
                if (snapshot.isRunning && lastWall > 0) {
                    val dt = ((now - lastWall).coerceIn(0, 5_000)) / 1000.0
                    val step = snapshot.speedKph / 3.6 * dt
                    distance += step
                    if (snapshot.inclinePercent > 0) vertical += step * snapshot.inclinePercent / 100.0
                }
                lastWall = now
            } else state.distanceMeters?.takeIf { it > 0 }?.let { now ->
                val before = lastDistance
                val incline = state.displayedInclinePercent ?: 0.0
                if (before != null && now > before && incline > 0) vertical += (now - before) * incline / 100.0
                lastDistance = now
                distance = now.toDouble()
            }
            (estimatedCalories.value ?: state.calories)?.takeIf { it > 0 }?.let { calories = it }
            // A sample for the charts every 10 s of workout time (pauses add none).
            val seconds = (snapshot.totalElapsedMillis / 1000).toInt()
            if (snapshot.isRunning) heartWindow.add(currentHeartRate())
            val due = samples.lastOrNull()?.let { seconds >= it.seconds + HistoryCodec.SAMPLE_SECONDS } ?: true
            if (snapshot.isRunning && due && samples.size < MAX_SAMPLES) {
                samples += com.ofit.protocol.HistorySample(
                    seconds = seconds,
                    speedKph = state.displayedSpeedKph ?: snapshot.speedKph,
                    inclinePercent = state.displayedInclinePercent ?: snapshot.inclinePercent,
                    heartRate = heartWindow.take(),
                )
            }
        } else if (active) {
            active = false
            if (snapshot.isFinished) finish(snapshot)
        }
    }

    private fun begin(snapshot: WorkoutEngineSnapshot) {
        active = true
        // The wall clock when it began: the workout clock can already be ahead (an intro skipped at
        // the start), which made the summary miss the entry (user report 2026-11-24).
        startedAt = System.currentTimeMillis() - snapshot.totalElapsedMillis.coerceAtMost(2_000)
        name = snapshot.programName
        distance = 0.0
        calories = null
        vertical = 0.0
        lastDistance = null
        samples.clear()
        lastWall = 0L
        activeMillis = 0L
        lastActiveWall = 0L
    }

    private fun finish(snapshot: WorkoutEngineSnapshot) {
        // Time actually spent; the program position would count skipped or fast-forwarded time.
        val seconds = (if (activeMillis > 0) activeMillis else snapshot.totalElapsedMillis) / 1000
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
            noTreadmill = noTreadmill,
            zoneSeconds = hr.zoneSeconds,
            samples = samples.toList(),
        )
        // Under a minute: not kept unless the runner taps "Save anyway" on the summary.
        if (seconds < HistoryCodec.MIN_DURATION_SECONDS) {
            _unsaved.value = entry
            return
        }
        _unsaved.value = null
        save(listOf(entry) + _entries.value)
    }

    private val _unsaved = MutableStateFlow<HistoryEntry?>(null)

    /** The last workout, when it ran under a minute and so was not saved (user request 2026-11-24). */
    val unsaved: StateFlow<HistoryEntry?> = _unsaved.asStateFlow()

    /** Keep the short workout after all. */
    fun saveUnsaved() {
        val entry = _unsaved.value ?: return
        _unsaved.value = null
        save(listOf(entry) + _entries.value)
    }

    /** The runner's rating of a workout after it (P15): stars 1-5 and how it felt. */
    fun rate(startedAtMillis: Long, stars: Int?, feel: String?) {
        save(_entries.value.map { if (it.startedAtMillis == startedAtMillis) it.copy(stars = stars, feel = feel) else it })
    }

    /** Told when an entry is deleted, so what was sent elsewhere (Health Connect) goes too. */
    var onDeleted: ((HistoryEntry) -> Unit)? = null

    /** Delete one entry. */
    fun delete(entry: HistoryEntry) {
        save(_entries.value.filterNot { it.startedAtMillis == entry.startedAtMillis })
        onDeleted?.invoke(entry)
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

        /** Ten hours at one sample per 10 s: enough for any workout, small enough for the file. */
        const val MAX_SAMPLES = 3_600
    }
}
