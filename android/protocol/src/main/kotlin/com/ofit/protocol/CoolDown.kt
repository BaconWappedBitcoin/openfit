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

    fun build(fromKph: Double, limits: SafetyLimits): WorkoutProgram {
        val minSpeed = limits.machineMinSpeedKph ?: 0.8
        val start = minOf(START_KPH, fromKph).coerceAtLeast(minSpeed)
        val end = minOf(END_KPH, start).coerceAtLeast(minSpeed)
        val incline = 0.0.coerceAtLeast(limits.machineMinInclinePercent ?: 0.0)
        val steps = (0 until MINUTES).map { i ->
            val share = if (MINUTES == 1) 0.0 else i.toDouble() / (MINUTES - 1)
            val speed = ((start + (end - start) * share) * 10).roundToInt() / 10.0
            WorkoutStep(speed, incline, 60)
        }
        return WorkoutProgram(NAME, steps).fitTo(limits)
    }
}
