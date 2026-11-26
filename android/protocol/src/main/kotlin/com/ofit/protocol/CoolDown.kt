package com.ofit.protocol

import kotlin.math.roundToInt

/**
 * The cool-down after a workout: ten minutes of walking that slows step by step, flat, so the
 * heart rate comes down before the belt stops (user request, 2026-11-24, "like other treadmill
 * apps"). It starts no faster than a brisk walk and no faster than the runner was going.
 */
object CoolDown {
    const val MINUTES = 10
    const val NAME = "Cool-down"

    /** Brisk walk, about 3.1 mph. */
    const val START_KPH = 5.0

    /** Easy stroll, about 2 mph. */
    const val END_KPH = 3.2

    fun build(fromKph: Double, limits: SafetyLimits): WorkoutProgram =
        WorkoutProgram(NAME, ramp(fromKph, limits, MINUTES)).fitTo(limits)

    /** [minutes] one-minute steps easing down from [fromKph] (no faster than a brisk walk), flat. */
    private fun ramp(fromKph: Double, limits: SafetyLimits, minutes: Int): List<WorkoutStep> {
        val minSpeed = limits.machineMinSpeedKph ?: 0.8
        val start = minOf(START_KPH, fromKph).coerceAtLeast(minSpeed)
        val end = minOf(END_KPH, start).coerceAtLeast(minSpeed)
        val incline = 0.0.coerceAtLeast(limits.machineMinInclinePercent ?: 0.0)
        return (0 until minutes).map { i ->
            val share = if (minutes == 1) 0.0 else i.toDouble() / (minutes - 1)
            val speed = ((start + (end - start) * share) * 10).roundToInt() / 10.0
            WorkoutStep(speed, incline, 60)
        }
    }

    /** The shortest tail worth building: under this, a workout keeps its own ending. */
    const val MIN_TAIL_MINUTES = 3

    /**
     * The program with its last [MINUTES] minutes turned into the cool-down (user request 2026-11-25:
     * "the programs should add a 10-minute cool-down to the end"), so that a video workout cools
     * down inside its video, on every workout. The steps cover the same time as before: the tail
     * replaces the end of the run rather than lengthening the program, and it eases down from
     * the speed the run had reached there. A short workout gives up at most 40 % of itself (whole
     * minutes), and one too short for a [MIN_TAIL_MINUTES]-minute tail is left alone. Already done: unchanged.
     */
    fun withTail(program: WorkoutProgram, limits: SafetyLimits): WorkoutProgram {
        if (program.coolDownFromSeconds != null) return program
        val total = program.totalDurationSeconds
        val tailMinutes = minOf(MINUTES, (total * 0.4).toInt() / 60)
        if (tailMinutes < MIN_TAIL_MINUTES) return program
        val cut = total - tailMinutes * 60
        val kept = mutableListOf<WorkoutStep>()
        var at = 0
        var speed = program.steps.firstOrNull()?.speedKph ?: START_KPH
        for (step in program.steps) {
            if (at >= cut) break
            val take = minOf(step.durationSeconds, cut - at)
            kept += step.copy(durationSeconds = take)
            speed = step.speedKph
            at += take
        }
        return program.copy(steps = kept + ramp(speed, limits, tailMinutes), coolDownFromSeconds = cut)
    }
}
