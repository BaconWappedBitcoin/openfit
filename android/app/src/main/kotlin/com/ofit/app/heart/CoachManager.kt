package com.ofit.app.heart

import android.os.SystemClock
import com.ofit.protocol.HeartRateCoach
import com.ofit.protocol.HeartRateProfile
import com.ofit.protocol.HeartRateZones
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.WorkoutEngineSnapshot
import com.ofit.protocol.WorkoutProgram
import com.ofit.transport.HeartRateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What the HUD shows about the coach. */
data class CoachStatus(val zone: Int, val band: IntRange, val note: String?, val level: String = "Normal")

/**
 * Runs [HeartRateCoach] during a workout (spec 003). Off unless the runner switches it on for this
 * workout; switches itself off when the workout stops, when the runner changes speed by hand (S4),
 * or when heart rate goes very high (S2).
 */
class CoachManager(
    private val scope: CoroutineScope,
    private val heart: HeartRateManager,
    private val workout: StateFlow<WorkoutEngineSnapshot>,
    private val program: StateFlow<WorkoutProgram?>,
    private val limits: () -> SafetyLimits,
    private val setSpeed: (Double) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val _status = MutableStateFlow<CoachStatus?>(null)
    val status: StateFlow<CoachStatus?> = _status.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /** Why it last switched itself off (or would not start), shown on the workout screen for a few seconds. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var coach: HeartRateCoach? = null
    private var lastStep = -1
    /** The speed the belt should be at if nobody else touched it. */
    private var expectedKph: Double? = null

    init {
        scope.launch {
            while (true) {
                runCatching { tick() }
                delay(1_000)
            }
        }
    }

    /** Switch on for [zone] (1-5), or explain why not (spec 003 P1-P3). */
    fun start(zone: Int) {
        val zones = HeartRateZones.of(heart.maxHr.value, heart.age.value)
        val reading = heart.monitor.reading.value
        val fresh = reading != null &&
            SystemClock.elapsedRealtime() - reading.atMillis <= HeartRateProfile.STALE_AFTER_MILLIS
        val reason = when {
            zones == null -> "Set your age or max heart rate first (Heart-rate monitor screen)."
            heart.monitor.link.value != HeartRateMonitor.LinkState.CONNECTED || !fresh ->
                "Hold zone needs a heart-rate strap with a live reading."
            !workout.value.isRunning -> "Start the workout first."
            else -> null
        }
        if (reason != null) {
            onMessage(reason)
            _notice.value = reason
            scope.launch {
                delay(8_000)
                if (_notice.value == reason) _notice.value = null
            }
            return
        }
        val level = heart.coachLevel.value
        val created = HeartRateCoach(zones!!, zone, level.config)
        created.onStepChanged(SystemClock.elapsedRealtime())
        coach = created
        lastStep = workout.value.stepIndex
        expectedKph = workout.value.speedKph
        _status.value = CoachStatus(zone, created.band, null, level.label)
    }

    fun stop(reason: String? = null) {
        coach = null
        expectedKph = null
        _status.value = null
        if (reason != null) {
            onMessage(reason)
            _notice.value = reason
            scope.launch {
                delay(8_000)
                if (_notice.value == reason) _notice.value = null
            }
        }
    }

    private fun tick() {
        val coach = coach ?: return
        val snapshot = workout.value
        if (!snapshot.isRunning) {
            stop(if (snapshot.isActive) null else "Hold zone is off: the workout ended.")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (snapshot.stepIndex != lastStep) {
            lastStep = snapshot.stepIndex
            coach.onStepChanged(now)
            expectedKph = snapshot.speedKph
        } else {
            val expected = expectedKph
            // S4: someone else (the runner, the console) changed the speed: they are in charge.
            if (expected != null && abs(snapshot.speedKph - expected) > 0.05) {
                stop("Hold zone is off: you changed the speed.")
                return
            }
        }
        val stepKph = program.value?.steps?.getOrNull(snapshot.stepIndex)?.speedKph ?: snapshot.speedKph
        val reading = heart.monitor.reading.value
        when (
            val decision = coach.decide(
                nowMillis = now,
                bpm = reading?.measurement?.bpm,
                bpmAtMillis = reading?.atMillis,
                currentKph = snapshot.speedKph,
                stepKph = stepKph,
                machineMinKph = limits().machineMinSpeedKph ?: 0.8,
            )
        ) {
            is HeartRateCoach.Decision.Hold -> _status.value = _status.value?.copy(note = decision.note)
            is HeartRateCoach.Decision.SetSpeed -> {
                expectedKph = decision.kph
                _status.value = _status.value?.copy(note = null)
                setSpeed(decision.kph)
            }
            is HeartRateCoach.Decision.TurnOff -> {
                decision.kph?.let { kph ->
                    expectedKph = kph
                    setSpeed(kph)
                }
                stop("Hold zone is off: ${decision.reason.lowercase()}.")
            }
        }
    }
}
