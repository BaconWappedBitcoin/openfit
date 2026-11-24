package com.ofit.protocol

/**
 * Speed and incline ceilings.
 *
 * The ceiling is enforced in the protocol layer rather than in the user interface, so that no
 * screen can produce a command above it. Constitution Article IV and Constraint 3.
 *
 * The machine's own limits are read from the machine at connect time and are never assumed. When
 * the machine has not reported its limits, the user's ceiling stands on its own.
 */
data class SafetyLimits(
    val maxSpeedKph: Double,
    val maxInclinePercent: Double,
    val machineMaxSpeedKph: Double? = null,
    val machineMinSpeedKph: Double? = null,
    val machineMaxInclinePercent: Double? = null,
    val machineMinInclinePercent: Double? = null,
) {
    /** Validate a target speed against the user ceiling and the machine's reported limits. */
    fun checkSpeed(speedKph: Double): LimitCheck {
        // Zero is "stop the belt" (Pause sends it), never "too slow": it must always get through.
        // Refusing it once the machine's minimum was known left the belt running on Pause
        // (found live, 2026-11-23).
        if (speedKph == 0.0) return LimitCheck.Accepted
        if (speedKph > maxSpeedKph) return LimitCheck.Refused(Refusal.ABOVE_SPEED_CEILING)
        val machineMin = machineMinSpeedKph
        if (machineMin != null && speedKph < machineMin) {
            return LimitCheck.Refused(Refusal.BELOW_MACHINE_MINIMUM)
        }
        val machineMax = machineMaxSpeedKph
        if (machineMax != null && speedKph > machineMax) {
            return LimitCheck.Refused(Refusal.ABOVE_MACHINE_MAXIMUM)
        }
        return LimitCheck.Accepted
    }

    /** Validate a target incline against the user ceiling and the machine's reported limits. */
    fun checkIncline(inclinePercent: Double): LimitCheck {
        if (inclinePercent > maxInclinePercent) {
            return LimitCheck.Refused(Refusal.ABOVE_INCLINE_CEILING)
        }
        val machineMin = machineMinInclinePercent
        if (machineMin != null && inclinePercent < machineMin) {
            return LimitCheck.Refused(Refusal.BELOW_MACHINE_MINIMUM)
        }
        val machineMax = machineMaxInclinePercent
        if (machineMax != null && inclinePercent > machineMax) {
            return LimitCheck.Refused(Refusal.ABOVE_MACHINE_MAXIMUM)
        }
        return LimitCheck.Accepted
    }

    /**
     * Validate a combined speed and incline command, including whether the machine is in a state
     * that can accept it.
     */
    fun check(speedKph: Double, inclinePercent: Double, readiness: MachineReadiness): LimitCheck {
        if (readiness == MachineReadiness.BUSY_LOCAL) {
            return LimitCheck.Refused(Refusal.MACHINE_BUSY_LOCAL)
        }
        if (readiness != MachineReadiness.ARMED) {
            return LimitCheck.Refused(Refusal.MACHINE_NOT_ARMED)
        }
        val speed = checkSpeed(speedKph)
        if (speed is LimitCheck.Refused) return speed
        return checkIncline(inclinePercent)
    }

    /** The speed ceiling actually in force: the lower of the user's ceiling and the machine's maximum. */
    val effectiveMaxSpeedKph: Double
        get() = minOf(maxSpeedKph, machineMaxSpeedKph ?: maxSpeedKph)

    /** The incline ceiling actually in force: the lower of the user's ceiling and the machine's maximum. */
    val effectiveMaxInclinePercent: Double
        get() = minOf(maxInclinePercent, machineMaxInclinePercent ?: maxInclinePercent)

    companion object {
        /** The ceilings used until the user changes them. */
        val DEFAULT = SafetyLimits(
            maxSpeedKph = 12.0,
            maxInclinePercent = 10.0,
        )
    }
}

/** The outcome of a limit check. */
sealed interface LimitCheck {
    /** The command is within every limit. */
    data object Accepted : LimitCheck

    /** The command was refused, for the given reason. */
    data class Refused(val reason: Refusal) : LimitCheck

    val isAccepted: Boolean get() = this is Accepted

    val refusal: Refusal? get() = (this as? Refused)?.reason
}

/** Why a command was refused. */
enum class Refusal {
    ABOVE_SPEED_CEILING,
    ABOVE_INCLINE_CEILING,
    BELOW_MACHINE_MINIMUM,
    ABOVE_MACHINE_MAXIMUM,
    MACHINE_NOT_ARMED,
    MACHINE_BUSY_LOCAL,
    NO_APP_WORKOUT,
    ;

    /** A message suitable for showing to the user. */
    val explanation: String
        get() = when (this) {
            ABOVE_SPEED_CEILING -> "That speed is above your speed ceiling."
            ABOVE_INCLINE_CEILING -> "That incline is above your incline ceiling."
            BELOW_MACHINE_MINIMUM -> "That is below the machine's minimum."
            ABOVE_MACHINE_MAXIMUM -> "That is above the machine's maximum."
            MACHINE_NOT_ARMED -> "The treadmill is not ready to accept commands yet."
            MACHINE_BUSY_LOCAL -> "Someone is using the treadmill by hand, so OpenFit leaves it alone."
            NO_APP_WORKOUT -> "OpenFit only changes the belt during a workout it started. Start one first."
        }
}
