package com.ofit.protocol

/**
 * The field table, from public reverse-engineering projects and Bluetooth captures of the console
 * enumeration and the converter sizes used by its byte-log decoder.
 *
 * A field is addressed by its numeric id. Values are encoded with the converter implied by the
 * field's size and by the table below.
 */
enum class FieldId(
    val id: Int,
    val size: Int,
    val signed: Boolean = false,
    val scale: Int = 1,
) {
    KPH(0, 2),
    GRADE(1, 2, signed = true),
    RESISTANCE(2, 2),
    WATTS(3, 2),
    CURRENT_DISTANCE(4, 4),
    RPM(5, 2),
    DISTANCE(6, 4),
    KEY_OBJECT(7, 14),
    FAN_SPEED(8, 1),
    VOLUME(9, 1),
    PULSE(10, 4),
    RUNNING_TIME(11, 4),
    WORKOUT_MODE(12, 1),
    CALORIES(13, 4),
    AUDIO_SOURCE(14, 3),
    LAP_TIME(15, 2),
    ACTUAL_KPH(16, 2),
    ACTUAL_INCLINE(17, 2),
    ACTUAL_RESISTANCE(18, 2),
    ACTUAL_DISTANCE(19, 4),
    CURRENT_TIME(20, 4),
    CURRENT_CALORIES(21, 4),
    GOAL_TIME(22, 4),
    INTERVAL_KPH(23, 2),
    AGE(24, 1),
    WEIGHT(25, 2),
    GEAR(26, 8),
    MAX_GRADE(27, 2, signed = true),
    MIN_GRADE(28, 2, signed = true),
    MAX_KPH(30, 2),
    MIN_KPH(31, 2),
    IDLE_TIMEOUT(34, 2),
    PAUSE_TIMEOUT(35, 2),
    SYSTEM_UNITS(36, 1),
    WARMUP_DISTANCE(44, 4),
    WARMUP_TIME(45, 2),
    WARMUP_TIMEOUT(46, 2),
    MAX_PULSE(49, 1),
    AVERAGE_GRADE(52, 2, signed = true),
    AVERAGE_WATTS(54, 2),
    TOTAL_TIME(70, 4),
    COOLDOWN_TIMEOUT(71, 2),
    VERTICAL_METER_NET(75, 4),
    VERTICAL_METER_GAIN(76, 4),
    MOTOR_TOTAL_DISTANCE(69, 4),
    IDLE_MODE_LOCKOUT(95, 1),
    START_REQUESTED(96, 1),
    ACTIVATION_LOCK(100, 1),
    PAUSED_TIME(103, 4),
    SLEEP_TIMER_STATE(107, 1),
    REQUIRE_START_REQUESTED(108, 1),
    IS_CLUB_UNIT(115, 1),
    ;

    companion object {
        private val BY_ID = entries.associateBy { it.id }

        fun of(id: Int): FieldId? = BY_ID[id]
    }
}

/** The machine's operating mode, field 12. */
enum class WorkoutMode(val code: Int) {
    UNKNOWN(0),
    IDLE(1),
    RUNNING(2),
    PAUSE(3),
    RESULTS(4),
    DEBUG(5),
    LOG(6),
    MAINTENANCE(7),
    DMK(8),
    DEMO(9),
    WARM_UP(10),
    COOL_DOWN(11),
    SLEEP(12),
    RESUME(13),
    LOCKED(14),
    PAUSE_OVERRIDE(20),
    ;

    companion object {
        private val BY_CODE = entries.associateBy { it.code }

        fun of(code: Int): WorkoutMode = BY_CODE[code] ?: UNKNOWN
    }
}

/** Where a heart-rate reading came from, byte 3 of the pulse composite. */
enum class PulseSource(val code: Int) {
    NONE(0x00),
    HAND_GRIP(0x01),
    UNKNOWN_2(0x02),
    UNKNOWN_3(0x03),
    BLE_HEART_RATE_MONITOR(0x04),
    ;

    companion object {
        private val BY_CODE = entries.associateBy { it.code }

        fun of(code: Int): PulseSource = BY_CODE[code] ?: UNKNOWN_2
    }
}

/**
 * A decoded field value.
 *
 * Speed and incline are decoded as physical units; the scaling that reaches the wire is applied
 * by the converter, not by the caller.
 */
sealed interface FieldValue {

    /** Target speed in km/h. */
    data class Speed(val kmh: Double) : FieldValue

    /** Target incline in percent. May be negative. */
    data class Incline(val percent: Double) : FieldValue

    /** A four byte pulse composite. */
    data class Pulse(
        val currentBpm: Int,
        val averageBpm: Int,
        val count: Int,
        val source: PulseSource,
    ) : FieldValue

    /** The machine's operating mode. */
    data class WorkoutMode(val mode: com.ofit.protocol.WorkoutMode) : FieldValue

    /** A plain unsigned integer of one, two, or four bytes. */
    data class UInt(val value: Long) : FieldValue

    /** A plain signed integer of two bytes. */
    data class SInt(val value: Int) : FieldValue

    /** A single byte flag. */
    data class Flag(val value: Boolean) : FieldValue
}
