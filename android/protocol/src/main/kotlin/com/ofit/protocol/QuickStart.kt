package com.ofit.protocol

import kotlin.math.roundToInt

/**
 * Quick start: a one-tap workout from a speed, an incline and a goal, with an optional warm-up and
 * cool-down (BACKLOG lane 7, P3). Built as an ordinary program so it runs, records and shows like
 * any other workout.
 */
object QuickStart {

    sealed interface Goal {
        /** Run until stopped (capped at [OPEN_MINUTES]). */
        data object Open : Goal

        data class Time(val minutes: Int) : Goal

        data class Distance(val meters: Double) : Goal
    }

    /** An open run is still a program with an end, so it cannot run forever unattended. */
    const val OPEN_MINUTES = 60
    const val WARMUP_SECONDS = 180
    const val COOLDOWN_SECONDS = 180

    /** Warm-up and cool-down speed, as a share of the main speed. */
    private const val EASY_SHARE = 0.6

    fun build(
        speedKph: Double,
        inclinePercent: Double,
        goal: Goal,
        warmUp: Boolean,
        coolDown: Boolean,
        limits: SafetyLimits,
    ): WorkoutProgram {
        val minSpeed = limits.machineMinSpeedKph ?: 0.8
        val speed = speedKph.coerceIn(minSpeed, limits.effectiveMaxSpeedKph)
        val incline = inclinePercent.coerceIn(limits.machineMinInclinePercent ?: 0.0, limits.effectiveMaxInclinePercent)
        val easy = round1(maxOf(minSpeed, speed * EASY_SHARE))

        val mainSeconds = when (goal) {
            Goal.Open -> OPEN_MINUTES * 60
            is Goal.Time -> goal.minutes.coerceIn(1, 180) * 60
            // Distance covered in the main step only; warm-up and cool-down are extra.
            is Goal.Distance -> (goal.meters / (speed / 3.6)).roundToInt().coerceIn(30, 180 * 60)
        }
        val steps = buildList {
            if (warmUp) add(WorkoutStep(easy, 0.0, WARMUP_SECONDS))
            add(WorkoutStep(round1(speed), incline, mainSeconds))
            if (coolDown) add(WorkoutStep(easy, 0.0, COOLDOWN_SECONDS))
        }
        return WorkoutProgram(name = nameFor(goal), steps = steps)
    }

    fun nameFor(goal: Goal): String = when (goal) {
        Goal.Open -> "Quick start"
        is Goal.Time -> "Quick start · ${goal.minutes} min"
        is Goal.Distance -> "Quick start · %.1f km".format(goal.meters / 1000.0)
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    /** A one-tap starting point: a pace and a slope with a name. */
    data class Preset(val name: String, val detail: String, val speedKph: Double, val inclinePercent: Double)

    val PRESETS = listOf(
        Preset("Easy walk", "Gentle and flat", 4.0, 0.0),
        Preset("Brisk walk", "Quick steps, slight rise", 5.6, 1.0),
        Preset("Hill walk", "Steady climb", 5.0, 6.0),
        Preset("Jog", "Comfortable running", 8.0, 1.0),
        Preset("Run", "Strong pace", 10.0, 1.0),
    )

    /** What a program adds up to: distance and an estimate of calories (no heart rate). */
    data class Estimate(val meters: Double, val kcal: Double)

    fun estimate(program: WorkoutProgram, body: BodyProfile): Estimate {
        var meters = 0.0
        var kcal = 0.0
        program.steps.forEach { step ->
            meters += step.speedKph / 3.6 * step.durationSeconds
            kcal += CalorieEstimator.kcalPerMinute(step.speedKph, step.inclinePercent, null, body) * step.durationSeconds / 60.0
        }
        return Estimate(meters, kcal)
    }
}
