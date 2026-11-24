package com.ofit.protocol

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A video model's per-window answers become a workout: outliers replaced, a running mean, filled in
 * between readings, held to the library's limits, then neighbours that agree merged into one step.
 * A port of the backend's lib/build.ts (itself a port of build_program.py), so a workout made on the
 * tablet with the runner's own Gemini key matches one made by the server (user 2026-11-24: OpenFit
 * must work without the hosted server).
 */
object VideoProgramBuilder {
    /** What the model said about one window of the video; null when it could not tell. */
    data class WindowReading(
        val index: Int,
        val startSeconds: Int,
        val durationSeconds: Int,
        val speedKph: Double?,
        val inclinePercent: Double?,
        val confidence: Double,
        val terrain: String = "",
        val note: String = "",
    )

    /** The community library's limits (backend lib/program.ts LIMITS). */
    const val MIN_SPEED_KPH = 1.6
    const val MAX_SPEED_KPH = 12.0
    const val MIN_INCLINE = -5.0
    const val MAX_INCLINE = 10.0
    const val MAX_STEPS = 300
    const val MAX_NAME_LENGTH = 80

    data class Row(val duration: Double, val speed: Double, val incline: Double)

    /** Cut each window into [resolution]-second pieces on the straight line between window middles. */
    fun interpolate(rows: List<Row>, resolution: Double): List<Row> {
        if (resolution <= 0 || rows.size < 2) return rows
        val centres = ArrayList<Double>()
        var t = 0.0
        for (r in rows) {
            centres += t + r.duration / 2
            t += r.duration
        }
        fun at(time: Double, pick: (Row) -> Double): Double {
            if (time <= centres[0]) return pick(rows[0])
            for (i in 1 until rows.size) {
                if (time <= centres[i]) {
                    val share = (time - centres[i - 1]) / (centres[i] - centres[i - 1])
                    return pick(rows[i - 1]) + (pick(rows[i]) - pick(rows[i - 1])) * share
                }
            }
            return pick(rows.last())
        }
        val out = ArrayList<Row>()
        var start = 0.0
        for (r in rows) {
            var done = 0.0
            while (done < r.duration - 1e-9) {
                val piece = min(resolution, r.duration - done)
                val mid = start + done + piece / 2
                out += Row(piece, at(mid) { it.speed }, at(mid) { it.incline })
                done += piece
            }
            start += r.duration
        }
        return out
    }

    fun build(
        readings: List<WindowReading>,
        name: String,
        smooth: Int = 3,
        mergeSpeed: Double = 0.15,
        mergeIncline: Double = 0.4,
        resolution: Double = 10.0,
    ): WorkoutProgram {
        val speeds = readings.mapNotNull { it.speedKph }
        require(speeds.isNotEmpty()) { "no speeds were read from the video" }
        val inclines = readings.mapNotNull { it.inclinePercent }
        // A missing reading is not a stationary runner: fill with the median, never zero.
        val fallbackSpeed = median(speeds)
        val fallbackIncline = if (inclines.isEmpty()) 0.0 else median(inclines)
        var rows = readings.map {
            Row(it.durationSeconds.toDouble(), it.speedKph ?: fallbackSpeed, it.inclinePercent ?: fallbackIncline)
        }

        // One window that disagrees with both neighbours becomes their average.
        val smoothed = rows.toMutableList()
        for (i in 1 until rows.size - 1) {
            val a = rows[i - 1]; val b = rows[i]; val c = rows[i + 1]
            val speedGap = abs(b.speed - a.speed) + abs(b.speed - c.speed)
            val inclineGap = abs(b.incline - a.incline) + abs(b.incline - c.incline)
            if (speedGap > 3 && inclineGap > 6) smoothed[i] = b.copy(speed = (a.speed + c.speed) / 2, incline = (a.incline + c.incline) / 2)
        }
        rows = smoothed

        // Running mean, to drop the jitter of a model reading pictures.
        if (smooth > 1) {
            val half = smooth shr 1
            val source = rows
            rows = source.mapIndexed { i, r ->
                val w = source.subList(max(0, i - half), min(source.size, i + half + 1))
                Row(r.duration, w.sumOf { it.speed } / w.size, w.sumOf { it.incline } / w.size)
            }
        }

        val fine = interpolate(rows, resolution)
        var ms = mergeSpeed
        var mi = mergeIncline
        var steps = toSteps(fine, ms, mi)
        while (steps.size > MAX_STEPS) {
            ms *= 1.5; mi *= 1.5
            steps = toSteps(fine, ms, mi)
        }

        // The video's own pace, before limits and rounding, for "Match my speed".
        val timed = readings.filter { it.speedKph != null && it.durationSeconds > 0 }
        val seconds = timed.sumOf { it.durationSeconds }
        val pace = if (seconds > 0) timed.sumOf { it.speedKph!! * it.durationSeconds } / seconds else fallbackSpeed
        return WorkoutProgram(tidyTitle(name), steps, videoPaceKph = (pace * 100).roundToInt() / 100.0)
    }

    private fun toSteps(rows: List<Row>, mergeSpeed: Double, mergeIncline: Double): List<WorkoutStep> {
        data class Acc(val speed: Double, val incline: Double, var duration: Double)
        val acc = ArrayList<Acc>()
        for (r in rows) {
            val speed = (r.speed.coerceIn(MIN_SPEED_KPH, MAX_SPEED_KPH) * 20).roundToInt() / 20.0
            val incline = (r.incline.coerceIn(MIN_INCLINE, MAX_INCLINE) * 2).roundToInt() / 2.0
            if (r.duration <= 0) continue
            val last = acc.lastOrNull()
            if (last != null && abs(last.speed - speed) <= mergeSpeed && abs(last.incline - incline) <= mergeIncline) {
                last.duration += r.duration
            } else {
                acc += Acc(speed, incline, r.duration)
            }
        }
        return acc.map { WorkoutStep(it.speed, it.incline, it.duration.roundToInt()) }.filter { it.durationSeconds > 0 }
    }

    /** A video title shortened for a workout name: cut at a word, with no trailing dash. */
    fun tidyTitle(title: String, maxLength: Int = 60): String {
        val clean = title.replace(Regex("[\\u0000-\\u001f\\u007f]"), " ").replace(Regex("\\s+"), " ").trim()
        if (clean.length <= maxLength) return clean.ifEmpty { "Video workout" }
        val cut = clean.take(maxLength + 1)
        val atWord = cut.lastIndexOf(' ')
        val short = (if (atWord > maxLength / 2) cut.take(atWord) else clean.take(maxLength))
            .replace(Regex("""[\s\-–—|:,]+$"""), "")
        return short.ifEmpty { clean.take(maxLength) }
    }

    /** The 30-second windows a video of [durationSeconds] is read in. */
    fun windows(durationSeconds: Int, chunk: Int = 30): List<Triple<Int, Int, Int>> {
        val out = ArrayList<Triple<Int, Int, Int>>()
        var s = 0
        var i = 0
        while (s < durationSeconds) {
            out += Triple(i++, s, min(s + chunk, durationSeconds))
            s += chunk
        }
        return out
    }

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        val m = s.size shr 1
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }
}
