package com.ofit.protocol

/**
 * The poll masks.
 *
 * The vendor application polls three different field sets. The main set carries the telemetry
 * that the metrics screen shows; the other two add the console key press, volume, and the run,
 * goal, and paused timers.
 *
 * The main mask was recovered from the vendor application's own byte log, where the read section
 * begins `0d 1b 94 31` for a thirteen byte mask.
 *
 * Evidence: research.md section 9.
 */
object Polls {

    /** The fields the metrics screen needs. */
    val TELEMETRY: List<Int> = listOf(
        FieldId.KPH.id,
        FieldId.GRADE.id,
        FieldId.WATTS.id,
        FieldId.CURRENT_DISTANCE.id,
        FieldId.PULSE.id,
        FieldId.WORKOUT_MODE.id,
        FieldId.LAP_TIME.id,
        FieldId.ACTUAL_KPH.id,
        // The vendor application reads 17 alongside 16, so the console supports it.
        FieldId.ACTUAL_INCLINE.id,
        FieldId.CURRENT_TIME.id,
        FieldId.CURRENT_CALORIES.id,
        FieldId.WARMUP_TIMEOUT.id,
        FieldId.AVERAGE_GRADE.id,
        FieldId.AVERAGE_WATTS.id,
        FieldId.COOLDOWN_TIMEOUT.id,
        FieldId.VERTICAL_METER_NET.id,
        FieldId.VERTICAL_METER_GAIN.id,
        FieldId.START_REQUESTED.id,
    )

    /** The secondary set, which adds the key press and the timers. */
    val EXTENDED: List<Int> = listOf(
        FieldId.GRADE.id,
        FieldId.CURRENT_DISTANCE.id,
        FieldId.KEY_OBJECT.id,
        FieldId.VOLUME.id,
        FieldId.RUNNING_TIME.id,
        FieldId.WORKOUT_MODE.id,
        FieldId.LAP_TIME.id,
        FieldId.CURRENT_TIME.id,
        FieldId.CURRENT_CALORIES.id,
        FieldId.GOAL_TIME.id,
        FieldId.PAUSED_TIME.id,
    )

    /** The button-watch set: the key press and the timers, and nothing else. */
    val BUTTON_WATCH: List<Int> = listOf(
        FieldId.KEY_OBJECT.id,
        FieldId.VOLUME.id,
        FieldId.RUNNING_TIME.id,
        FieldId.GOAL_TIME.id,
        FieldId.PAUSED_TIME.id,
    )

    /**
     * The fields read once at startup, to learn the machine's limits.
     *
     * Evidence: research.md section 8, startup read.
     */
    val STARTUP: List<Int> = listOf(
        FieldId.WORKOUT_MODE.id,
        FieldId.MAX_GRADE.id,
        FieldId.MIN_GRADE.id,
        FieldId.MAX_KPH.id,
        FieldId.MIN_KPH.id,
        FieldId.IDLE_TIMEOUT.id,
        FieldId.PAUSE_TIMEOUT.id,
        FieldId.SYSTEM_UNITS.id,
        // No MAX_PULSE (49): the I_TL console never answers a read that includes it, and the
        // vendor application does not ask for it (HCI capture, 2026-11-23).
        FieldId.WARMUP_TIMEOUT.id,
        FieldId.MOTOR_TOTAL_DISTANCE.id,
        FieldId.TOTAL_TIME.id,
        FieldId.COOLDOWN_TIMEOUT.id,
        FieldId.ACTIVATION_LOCK.id,
    )

    /** The poll mask in force, honouring the extended-mask flag. */
    fun activeTelemetryMask(): List<Int> =
        if (FeatureFlags.pollExtendedMasks) EXTENDED else TELEMETRY
}
