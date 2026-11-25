package com.ofit.protocol

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * A simulated treadmill ("Demo treadmill"): answers the workout engine like a real machine, ramps
 * speed and incline the way a belt does, and counts distance, time and calories, with nothing real
 * moving. It lets OpenFit be tried, demonstrated and filmed with the full workout screen and no
 * machine in the room. It is never connected to anything: no Bluetooth, no transport.
 *
 * Pause and stop follow the real sink ([SessionWorkoutSink]): speed 0 from the engine is a pause,
 * and the next non-zero step after a pause is a start.
 */
class SimulatedTreadmill(
    private val minSpeedKph: Double = 0.8,
    private val maxSpeedKph: Double = 16.1,
    private val minInclinePercent: Double = 0.0,
    private val maxInclinePercent: Double = 10.0,
) : WorkoutCommandSink {

    private var targetSpeed = 0.0
    private var targetIncline = 0.0
    private var actualSpeed = 0.0
    private var actualIncline = 0.0
    private var mode = WorkoutMode.IDLE
    private var paused = false
    private var distanceMeters = 0.0
    private var elapsedMillis = 0L
    private var calories = 0.0

    private val _state = MutableStateFlow(snapshot())

    /** What the machine reports, like a real one's polled state. */
    val state: StateFlow<TreadmillState> = _state.asStateFlow()

    override suspend fun startWorkout(speedKph: Double, inclinePercent: Double): Boolean {
        if (mode != WorkoutMode.RUNNING && mode != WorkoutMode.PAUSE) {
            distanceMeters = 0.0
            elapsedMillis = 0
            calories = 0.0
        }
        mode = WorkoutMode.RUNNING
        paused = false
        setTargets(speedKph, inclinePercent)
        publish()
        return true
    }

    override suspend fun applyStep(speedKph: Double, inclinePercent: Double): Boolean {
        if (speedKph == 0.0) {
            mode = WorkoutMode.PAUSE
            paused = true
            publish()
            return true
        }
        if (paused) return startWorkout(speedKph, inclinePercent)
        if (mode != WorkoutMode.RUNNING) return false
        setTargets(speedKph, inclinePercent)
        publish()
        return true
    }

    override suspend fun stopWorkout(): Boolean {
        mode = WorkoutMode.IDLE
        paused = false
        targetSpeed = 0.0
        publish()
        return true
    }

    /** Back to a machine that has just been switched on. */
    fun reset() {
        targetSpeed = 0.0
        targetIncline = 0.0
        actualSpeed = 0.0
        actualIncline = 0.0
        mode = WorkoutMode.IDLE
        paused = false
        distanceMeters = 0.0
        elapsedMillis = 0
        calories = 0.0
        publish()
    }

    /** Time passes: the belt and the deck move towards their targets, and the counters run. */
    fun tick(dtMillis: Long) {
        val dt = dtMillis / 1000.0
        val wantSpeed = if (mode == WorkoutMode.RUNNING) targetSpeed else 0.0
        actualSpeed = approach(actualSpeed, wantSpeed, SPEED_RAMP_KPH_PER_SECOND * dt)
        actualIncline = approach(actualIncline, targetIncline, INCLINE_RAMP_PERCENT_PER_SECOND * dt)
        if (mode == WorkoutMode.RUNNING) elapsedMillis += dtMillis
        if (actualSpeed > 0) {
            val meters = actualSpeed / 3.6 * dt
            distanceMeters += meters
            calories += KCAL_PER_KM * meters / 1000.0
        }
        publish()
    }

    private fun setTargets(speedKph: Double, inclinePercent: Double) {
        targetSpeed = speedKph.coerceIn(minSpeedKph, maxSpeedKph)
        targetIncline = inclinePercent.coerceIn(minInclinePercent, maxInclinePercent)
    }

    private fun approach(current: Double, target: Double, maxStep: Double): Double =
        if (abs(target - current) <= maxStep) target else current + if (target > current) maxStep else -maxStep

    private fun publish() {
        _state.value = snapshot()
    }

    private fun snapshot() = TreadmillState(
        connectionState = ConnectionState.CONNECTED,
        readiness = MachineReadiness.ARMED,
        targetSpeedKph = targetSpeed,
        targetInclinePercent = targetIncline,
        actualSpeedKph = round1(actualSpeed),
        actualInclinePercent = round1(actualIncline),
        distanceMeters = distanceMeters.toLong(),
        elapsedSeconds = elapsedMillis / 1000,
        calories = calories,
        maxSpeedKph = maxSpeedKph,
        minSpeedKph = minSpeedKph,
        maxInclinePercent = maxInclinePercent,
        minInclinePercent = minInclinePercent,
        workoutMode = mode,
        deviceInfo = DEVICE,
    )

    private fun round1(v: Double) = Math.round(v * 10) / 10.0

    companion object {
        /** About a real belt: 1 mph takes a little over 3 seconds. */
        const val SPEED_RAMP_KPH_PER_SECOND = 0.5
        const val INCLINE_RAMP_PERCENT_PER_SECOND = 0.5
        /** A rough walking/running figure for a 70 kg person; the app's own estimate is used when it can. */
        const val KCAL_PER_KM = 65.0
        /** A made-up machine, so nothing in a log can be mistaken for a real console. */
        val DEVICE = DeviceInfo(model = 0, firmwareVersion = "demo")
    }
}
