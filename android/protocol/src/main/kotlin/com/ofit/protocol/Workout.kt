package com.ofit.protocol

/**
 * A programmed workout: an ordered list of timed speed and incline steps.
 *
 * This is a "programmed workout": the part of a video workout that actually drives the machine.
 * The video is only what is on screen;
 * the machine follows the program. That means a program can be run with no video at all, which
 * is what makes offline use possible without touching protected media.
 *
 * Evidence: research.md section 11 and the `glassos_programmedworkout_network` package in the
 * vendor application.
 */
data class WorkoutStep(
    /** Target speed in km/h. */
    val speedKph: Double,
    /** Target incline in percent. Negative values are downhill, if the machine supports them. */
    val inclinePercent: Double,
    /** How long the step lasts. Always positive for a valid program. */
    val durationSeconds: Int,
) {
    /** The step's duration in milliseconds. */
    val durationMillis: Long get() = durationSeconds * 1000L
}

/**
 * A named program of steps.
 */
data class WorkoutProgram(
    val name: String,
    val steps: List<WorkoutStep>,
    /**
     * The walker's or runner's own average pace in the video, in km/h, when known (the AI analysis
     * reads it). "Match my speed" plays the video at belt speed / this pace; without it, the
     * workout's average speed is used.
     */
    val videoPaceKph: Double? = null,
    /**
     * The suggested start and end of the run in its video, in seconds from the start (user request
     * 2026-11-24): the intro is before [introEndSeconds] and the outro after [outroStartSeconds].
     * Nothing is cut: the steps still cover the whole video, and "Skip intro and outro" (on by
     * default) jumps to the start and ends the workout at the end.
     */
    val introEndSeconds: Int? = null,
    val outroStartSeconds: Int? = null,
) {
    /** The bounds as given, kept only when they make sense for this program's length. */
    fun withBounds(introEnd: Int?, outroStart: Int?): WorkoutProgram {
        val (i, o) = VideoBounds.clean(introEnd, outroStart, totalDurationSeconds)
        return copy(introEndSeconds = i, outroStartSeconds = o)
    }

    /** The total running time of the program. */
    val totalDurationSeconds: Int get() = steps.sumOf { it.durationSeconds }

    /**
     * Check the program against the ceilings before anything is sent to the machine.
     *
     * A program is rejected as a whole, not trimmed: silently dropping a step would run a
     * workout the user did not author. Constitution Article IV and Constraint 3.
     */
    /**
     * The program fitted to what this machine and the user's ceilings allow: each step's speed and
     * incline are moved into range, so a workout made for another treadmill still starts here
     * (user request, 2026-11-24: "the workout values should never stop a workout from starting").
     * Nothing outside the range is ever sent, so the ceilings still hold.
     */
    fun fitTo(limits: SafetyLimits): WorkoutProgram {
        val minSpeed = limits.machineMinSpeedKph ?: 0.0
        val maxSpeed = limits.effectiveMaxSpeedKph
        val minIncline = limits.machineMinInclinePercent ?: Double.NEGATIVE_INFINITY
        val maxIncline = limits.effectiveMaxInclinePercent
        return copy(
            steps = steps.map { step ->
                step.copy(
                    speedKph = step.speedKph.coerceIn(minOf(minSpeed, maxSpeed), maxSpeed),
                    inclinePercent = step.inclinePercent.coerceIn(minOf(minIncline, maxIncline), maxIncline),
                )
            },
        )
    }

    fun validate(limits: SafetyLimits): ProgramValidation {
        val problems = mutableListOf<ProgramProblem>()

        if (name.isBlank()) {
            problems += ProgramProblem(
                stepIndex = null,
                reason = ProgramProblemReason.EMPTY_NAME,
                message = "The workout needs a name.",
            )
        }

        if (steps.isEmpty()) {
            problems += ProgramProblem(
                stepIndex = null,
                reason = ProgramProblemReason.NO_STEPS,
                message = "The workout needs at least one step.",
            )
        }

        steps.forEachIndexed { index, step ->
            if (step.durationSeconds <= 0) {
                problems += ProgramProblem(
                    stepIndex = index,
                    reason = ProgramProblemReason.NON_POSITIVE_DURATION,
                    message = "Step ${index + 1} needs a duration of at least one second.",
                )
            }

            val speed = limits.checkSpeed(step.speedKph)
            if (speed is LimitCheck.Refused) {
                problems += ProgramProblem(
                    stepIndex = index,
                    reason = ProgramProblemReason.LIMIT_EXCEEDED,
                    message = speed.reason.explanation,
                    refusal = speed.reason,
                )
            }

            val incline = limits.checkIncline(step.inclinePercent)
            if (incline is LimitCheck.Refused) {
                problems += ProgramProblem(
                    stepIndex = index,
                    reason = ProgramProblemReason.LIMIT_EXCEEDED,
                    message = incline.reason.explanation,
                    refusal = incline.reason,
                )
            }
        }

        return if (problems.isEmpty()) ProgramValidation.Valid else ProgramValidation.Invalid(problems)
    }
}

/** Why a program was rejected. */
enum class ProgramProblemReason {
    /** The program has no steps at all. */
    NO_STEPS,

    /** The program has no name. */
    EMPTY_NAME,

    /** A step has a duration of zero or less. */
    NON_POSITIVE_DURATION,

    /** A step asks for a value the ceilings or the machine do not allow. */
    LIMIT_EXCEEDED,
}

/**
 * One reason a program was rejected.
 *
 * [stepIndex] is the zero-based index of the offending step, or null when the problem is with
 * the program as a whole.
 */
data class ProgramProblem(
    val stepIndex: Int?,
    val reason: ProgramProblemReason,
    val message: String,
    /** Set when the problem is a limit refusal, so the caller can show the standard explanation. */
    val refusal: Refusal? = null,
)

/** The outcome of validating a program. */
sealed interface ProgramValidation {
    /** Every step is inside every limit. */
    data object Valid : ProgramValidation

    /** The program cannot be run as written. */
    data class Invalid(val problems: List<ProgramProblem>) : ProgramValidation

    /** Whether the program may be run. */
    val isValid: Boolean get() = this is Valid

    /** The problems, if there are any. */
    val allProblems: List<ProgramProblem> get() = (this as? Invalid)?.problems ?: emptyList()
}
