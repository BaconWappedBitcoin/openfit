package com.ofit.app.heart

import android.content.Context
import android.os.SystemClock
import com.ofit.protocol.HeartRateRecord
import com.ofit.protocol.HeartRateSelector
import com.ofit.protocol.HeartRateSource
import com.ofit.protocol.HeartRateZones
import com.ofit.protocol.TreadmillState
import com.ofit.protocol.WorkoutEngineSnapshot
import com.ofit.transport.HeartRateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The heart rate to show, and where it came from (spec 002, H3/H5). */
data class ShownHeartRate(
    val bpm: Int?,
    val source: HeartRateSource,
    /** 0-5, or null when the user has given no age or max heart rate. */
    val zone: Int?,
)

/** Average and max over the current (or last) workout (H6). */
data class HeartRateSummary(val averageBpm: Int?, val maxBpm: Int?)

/**
 * The heart-rate strap: choosing it, remembering it, reconnecting to it, and blending its reading
 * with the console's own pulse. Losing the strap never affects the treadmill or the workout (H4).
 */
class HeartRateManager(
    context: Context,
    private val scope: CoroutineScope,
    private val consoleState: StateFlow<TreadmillState>,
    private val workout: StateFlow<WorkoutEngineSnapshot>,
    prefsName: String = PREFS,
) {
    private val appContext = context.applicationContext
    private var prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** Load another profile's strap and zones (each person has their own strap). */
    fun switchProfile(prefsName: String) {
        monitor.disconnect()
        stopScan()
        prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        _savedName.value = prefs.getString(KEY_NAME, null)
        _maxHr.value = prefs.getInt(KEY_MAX_HR, 0).takeIf { it > 0 }
        _age.value = prefs.getInt(KEY_AGE, 0).takeIf { it > 0 }
        _coachLevel.value = com.ofit.protocol.HeartRateCoach.Aggressiveness.fromName(prefs.getString(KEY_COACH_LEVEL, null))
        record.clear()
        lastRecordedElapsed = -1
        _summary.value = HeartRateSummary(null, null)
        runCatching { connectToSaved() }
    }

    val monitor = HeartRateMonitor(context.applicationContext)

    private val _devices = MutableStateFlow<List<HeartRateMonitor.Device>>(emptyList())
    val devices: StateFlow<List<HeartRateMonitor.Device>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _savedName = MutableStateFlow(prefs.getString(KEY_NAME, null))
    /** The remembered strap's name, or null. */
    val savedName: StateFlow<String?> = _savedName.asStateFlow()

    private val _maxHr = MutableStateFlow(prefs.getInt(KEY_MAX_HR, 0).takeIf { it > 0 })
    val maxHr: StateFlow<Int?> = _maxHr.asStateFlow()

    private val _age = MutableStateFlow(prefs.getInt(KEY_AGE, 0).takeIf { it > 0 })
    val age: StateFlow<Int?> = _age.asStateFlow()

    private val _coachLevel = MutableStateFlow(
        com.ofit.protocol.HeartRateCoach.Aggressiveness.fromName(prefs.getString(KEY_COACH_LEVEL, null)),
    )

    /** How hard Hold zone pushes (Gentle / Normal / Strong), per profile. */
    val coachLevel: StateFlow<com.ofit.protocol.HeartRateCoach.Aggressiveness> = _coachLevel.asStateFlow()

    fun setCoachLevel(level: com.ofit.protocol.HeartRateCoach.Aggressiveness) {
        prefs.edit().putString(KEY_COACH_LEVEL, level.name).apply()
        _coachLevel.value = level
    }

    private val _shown = MutableStateFlow(ShownHeartRate(null, HeartRateSource.NONE, null))
    val shown: StateFlow<ShownHeartRate> = _shown.asStateFlow()

    private val _summary = MutableStateFlow(HeartRateSummary(null, null))
    val summary: StateFlow<HeartRateSummary> = _summary.asStateFlow()

    private val record = HeartRateRecord()
    private var lastRecordedElapsed = -1L
    private var scanJob: Job? = null

    init {
        scope.launch {
            // Once a second: pick the source, colour by zone, and record while a workout runs.
            while (true) {
                refresh()
                delay(TICK_MILLIS)
            }
        }
    }

    private fun refresh() {
        val reading = monitor.reading.value
        val (bpm, source) = HeartRateSelector.select(
            strapBpm = reading?.measurement?.bpm,
            strapAtMillis = reading?.atMillis,
            consoleBpm = consoleState.value.heartRateBpm,
            nowMillis = SystemClock.elapsedRealtime(),
        )
        val zones = HeartRateZones.of(_maxHr.value, _age.value)
        _shown.value = ShownHeartRate(bpm, source, if (bpm != null) zones?.zoneOf(bpm) else null)

        val snapshot = workout.value
        if (snapshot.totalElapsedMillis < lastRecordedElapsed) {
            // A new workout started.
            record.clear()
        }
        if (snapshot.isRunning && bpm != null) {
            record.add(snapshot.totalElapsedMillis, bpm)
            lastRecordedElapsed = snapshot.totalElapsedMillis
            _summary.value = HeartRateSummary(record.average, record.max)
        } else if (!snapshot.isActive && !snapshot.isFinished && record.count > 0 &&
            snapshot.totalElapsedMillis == 0L
        ) {
            record.clear()
            lastRecordedElapsed = -1
            _summary.value = HeartRateSummary(null, null)
        }
    }

    /** Scan for straps for up to 20 seconds. */
    fun startScan() {
        scanJob?.cancel()
        _devices.value = emptyList()
        _scanning.value = true
        scanJob = scope.launch {
            try {
                withTimeoutOrNull(SCAN_MILLIS) {
                    monitor.scan().collect { device ->
                        val current = _devices.value
                        val known = current.firstOrNull { it.address == device.address }
                        // A later packet (scan response) may carry the heart-rate UUID the first lacked.
                        if (known == null || (device.isHeartRate && !known.isHeartRate)) {
                            _devices.value = (current.filterNot { it.address == device.address } + device)
                                .sortedWith(compareByDescending<HeartRateMonitor.Device> { it.isHeartRate }.thenByDescending { it.rssi })
                        }
                    }
                }
            } catch (_: SecurityException) {
                // No permission: the list stays empty and the screen says so.
            } finally {
                _scanning.value = false
            }
        }
        // Backstop: the screen once kept saying "Looking…" past the window (seen 2026-11-23).
        val job = scanJob
        scope.launch {
            kotlinx.coroutines.delay(SCAN_MILLIS + 2_000)
            if (scanJob === job) stopScan()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _scanning.value = false
    }

    /** Use this strap from now on, and remember it. */
    fun choose(device: HeartRateMonitor.Device) {
        stopScan()
        prefs.edit().putString(KEY_ADDRESS, device.address).putString(KEY_NAME, device.name).apply()
        _savedName.value = device.name
        monitor.connect(device.address)
    }

    /** Reconnect to the remembered strap, if there is one (called at start-up). */
    fun connectToSaved() {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return
        if (monitor.link.value != HeartRateMonitor.LinkState.IDLE) return
        monitor.connect(address)
    }

    /** Forget the strap. */
    fun forget() {
        monitor.disconnect()
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_NAME).apply()
        _savedName.value = null
    }

    fun setMaxHr(value: Int?) {
        val v = value?.takeIf { it in 100..230 }
        prefs.edit().putInt(KEY_MAX_HR, v ?: 0).apply()
        _maxHr.value = v
    }

    fun setAge(value: Int?) {
        val v = value?.takeIf { it in 10..100 }
        prefs.edit().putInt(KEY_AGE, v ?: 0).apply()
        _age.value = v
    }

    companion object {
        const val PREFS = "ofit_hr"
        private const val KEY_ADDRESS = "address"
        private const val KEY_NAME = "name"
        private const val KEY_MAX_HR = "max_hr"
        private const val KEY_AGE = "age"
        private const val KEY_COACH_LEVEL = "coach_level"
        private const val TICK_MILLIS = 1_000L
        private const val SCAN_MILLIS = 20_000L
    }
}
