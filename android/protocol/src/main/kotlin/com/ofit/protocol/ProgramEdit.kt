package com.ofit.protocol

import kotlin.math.roundToInt

/**
 * The visual editor's operations on a list of steps (no AI needed: the runner authors the workout
 * against the video). Pure and immutable: every call returns a new list.
 *
 * The total length never changes when steps are dragged, split or deleted, so the workout stays
 * matched to its video.
 */
object ProgramEdit {

    /** No step shorter than this, so every step can be seen and grabbed. */
    const val MIN_STEP_SECONDS = 5

    enum class Lane { SPEED, INCLINE }

    /** A workout of one step covering a whole video, the starting point for authoring. */
    fun forVideo(durationSeconds: Int, speedKph: Double, inclinePercent: Double): List<WorkoutStep> =
        listOf(WorkoutStep(speedKph, inclinePercent, durationSeconds.coerceAtLeast(MIN_STEP_SECONDS)))

    /** Index of the step playing at [seconds] (the last step for anything past the end). */
    fun stepAt(steps: List<WorkoutStep>, seconds: Double): Int {
        var start = 0
        steps.forEachIndexed { i, step ->
            if (seconds < start + step.durationSeconds) return i
            start += step.durationSeconds
        }
        return (steps.size - 1).coerceAtLeast(0)
    }

    /** Where step [index] starts, in seconds. */
    fun startOf(steps: List<WorkoutStep>, index: Int): Int = steps.take(index).sumOf { it.durationSeconds }

    /** Set one lane of one step, clamped to the limits and snapped (speed 0.1 km/h, incline 0.5 %). */
    fun setLevel(steps: List<WorkoutStep>, index: Int, lane: Lane, value: Double, limits: SafetyLimits): List<WorkoutStep> {
        if (index !in steps.indices) return steps
        val step = steps[index]
        val updated = when (lane) {
            Lane.SPEED -> step.copy(
                speedKph = (value.coerceIn(limits.machineMinSpeedKph ?: 0.8, limits.effectiveMaxSpeedKph) * 10).roundToInt() / 10.0,
            )
            Lane.INCLINE -> step.copy(
                inclinePercent = (value.coerceIn(limits.machineMinInclinePercent ?: 0.0, limits.effectiveMaxInclinePercent) * 2).roundToInt() / 2.0,
            )
        }
        return steps.toMutableList().also { it[index] = updated }
    }

    /**
     * Move the boundary in front of step [index] (between index-1 and index) to [startSeconds],
     * shortening one neighbour and lengthening the other. Both keep at least [MIN_STEP_SECONDS].
     */
    fun moveBoundary(steps: List<WorkoutStep>, index: Int, startSeconds: Int): List<WorkoutStep> {
        if (index <= 0 || index >= steps.size) return steps
        val before = steps[index - 1]
        val after = steps[index]
        val prevStart = startOf(steps, index - 1)
        val end = prevStart + before.durationSeconds + after.durationSeconds
        val newStart = startSeconds.coerceIn(prevStart + MIN_STEP_SECONDS, end - MIN_STEP_SECONDS)
        return steps.toMutableList().also {
            it[index - 1] = before.copy(durationSeconds = newStart - prevStart)
            it[index] = after.copy(durationSeconds = end - newStart)
        }
    }

    /** Split the step playing at [seconds] into two identical halves around that point. */
    fun split(steps: List<WorkoutStep>, seconds: Int): List<WorkoutStep> {
        if (steps.isEmpty()) return steps
        val index = stepAt(steps, seconds.toDouble())
        val start = startOf(steps, index)
        val step = steps[index]
        val first = seconds - start
        val second = step.durationSeconds - first
        if (first < MIN_STEP_SECONDS || second < MIN_STEP_SECONDS) return steps
        return steps.toMutableList().also {
            it[index] = step.copy(durationSeconds = first)
            it.add(index + 1, step.copy(durationSeconds = second))
        }
    }

    /** Delete a step; its time goes to the step before it (or after, for the first). */
    fun delete(steps: List<WorkoutStep>, index: Int): List<WorkoutStep> {
        if (steps.size <= 1 || index !in steps.indices) return steps
        val removed = steps[index]
        val list = steps.toMutableList()
        list.removeAt(index)
        val target = if (index > 0) index - 1 else 0
        list[target] = list[target].copy(durationSeconds = list[target].durationSeconds + removed.durationSeconds)
        return list
    }
}
