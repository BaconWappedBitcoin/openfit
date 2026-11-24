package com.ofit.protocol

/**
 * "Match my speed": the video plays at belt speed ÷ the step's programmed speed, so going faster or
 * slower than the plan moves the scenery to match. Rounded to 0.05x steps, fine enough that a
 * single +0.5 mph press shows (quarter steps hid it: 6.2 ÷ 5.7 rounded back to 1x), coarse enough
 * that small wobbles of the belt reading don't change the rate every second. YouTube's player
 * takes these in-between rates (checked on the tablet, 2026-11-24). Kept to 0.5x .. 2x.
 */
object MatchSpeed {
    const val STEP = 0.05

    /**
     * What "normal speed" means for a workout: the video's own pace when the analysis read it,
     * otherwise the workout's time-weighted average speed (user, 2026-11-24: compare against the
     * workout, not each step, so a fast step plays the video fast).
     */
    fun referenceKph(program: WorkoutProgram): Double? {
        program.videoPaceKph?.takeIf { it > 0 }?.let { return it }
        val moving = program.steps.filter { it.speedKph > 0 && it.durationSeconds > 0 }
        val seconds = moving.sumOf { it.durationSeconds }
        if (seconds == 0) return null
        return moving.sumOf { it.speedKph * it.durationSeconds } / seconds
    }

    fun rate(beltKph: Double, programmedKph: Double): Float {
        if (programmedKph <= 0.0 || beltKph <= 0.0) return 1f
        val raw = (beltKph / programmedKph).coerceIn(0.5, 2.0)
        return (Math.round(raw / STEP) * STEP).let { Math.round(it * 100) / 100.0 }.toFloat()
    }

    /**
     * Real time left for [programMillis] of workout played at [rate]: at 1.25x, 10 minutes of
     * video takes 8. Shown as "remaining" so it is the time you will actually spend.
     */
    fun realMillis(programMillis: Long, rate: Float): Long =
        if (rate <= 0f) programMillis else Math.round(programMillis / rate.toDouble())
}
