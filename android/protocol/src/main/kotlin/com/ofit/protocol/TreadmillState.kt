package com.ofit.protocol

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the user interface needs to know about the machine.
 *
 * Each metric is nullable so that a value the machine did not report is never shown as zero.
 * Evidence: data-model.md section 2.
 */
data class TreadmillState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val readiness: MachineReadiness = MachineReadiness.UNKNOWN,

    val targetSpeedKph: Double? = null,
    val targetInclinePercent: Double? = null,
    val actualSpeedKph: Double? = null,
    val actualInclinePercent: Double? = null,

    val distanceMeters: Long? = null,
    val elapsedSeconds: Long? = null,
    val calories: Double? = null,
    val heartRateBpm: Int? = null,
    val heartRateSource: PulseSource? = null,

    val maxSpeedKph: Double? = null,
    val minSpeedKph: Double? = null,
    val maxInclinePercent: Double? = null,
    val minInclinePercent: Double? = null,

    val workoutMode: WorkoutMode? = null,
    val lastKeyPress: KeyPress? = null,
    val driveMotorErrorCode: Int? = null,

    val deviceInfo: DeviceInfo? = null,
    val unlockDescription: String? = null,
    val lastError: String? = null,
) {
    /** True only when the machine is genuinely running a workout we are driving. */
    val isRunning: Boolean
        get() = connectionState == ConnectionState.CONNECTED &&
            readiness == MachineReadiness.ARMED &&
            workoutMode == WorkoutMode.RUNNING

    /**
     * The speed to show: the observed value when the machine reports one, else the target.
     * The I_TL console answers the actual-speed field with 0 even with the belt running (captured
     * 2026-11-23), so a 0 against a non-zero target counts as "not reported".
     */
    val displayedSpeedKph: Double?
        get() = observedOrTarget(actualSpeedKph, targetSpeedKph)

    /** The incline to show: the observed value when the machine reports one, else the target. */
    val displayedInclinePercent: Double?
        get() = observedOrTarget(actualInclinePercent, targetInclinePercent)

    private fun observedOrTarget(observed: Double?, target: Double?): Double? =
        if (observed == null || (observed == 0.0 && target != null && target != 0.0)) target else observed
}

/** A console key press reported by the machine. */
data class KeyPress(val code: Int, val name: String) {
    companion object {
        private val NAMES = mapOf(
            0 to "none",
            1 to "stop",
            2 to "start",
            3 to "speed up",
            4 to "speed down",
            5 to "incline up",
            6 to "incline down",
        )

        fun of(code: Int): KeyPress = KeyPress(code, NAMES[code] ?: "key $code")
    }
}

/**
 * Holds the current machine state and publishes changes.
 *
 * Kept separate from [Session] so that the user interface can observe the state without holding a
 * session reference.
 */
class TreadmillStateHolder {

    private val _state = MutableStateFlow(TreadmillState())

    /** The current state. */
    val state: StateFlow<TreadmillState> = _state.asStateFlow()

    /** Apply a change to the state. */
    fun update(transform: (TreadmillState) -> TreadmillState) {
        _state.value = transform(_state.value)
    }

    /** Apply a change to the state and return the result. */
    fun updateAndGet(transform: (TreadmillState) -> TreadmillState): TreadmillState {
        _state.value = transform(_state.value)
        return _state.value
    }

    /** Replace the state wholesale. */
    fun set(newState: TreadmillState) {
        _state.value = newState
    }
}
