package com.ofit.protocol

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Virtual trails (user request 2026-11-25): a workout for each 3D landscape, built from a length
 * and an effort. The hills suit the landscape (flat fields, rolling forest, steep mountains), the
 * pace eases a little on climbs, and every trail starts with a warm-up and ends easy. The same
 * trail, length and effort always give the same workout.
 */
object TrailProgram {
    /** A landscape: its key in the 3D page, its name, a line about it, and its steepest climb (%). */
    data class Trail(val key: String, val name: String, val blurb: String, val maxIncline: Double, val waves: Double)

    val TRAILS = listOf(
        Trail("forest", "Forest trail", "Oaks, birches and pines on a soft woodland path.", 5.0, 3.0),
        Trail("pine", "Pine forest", "Tall pines and spruces over rolling hills.", 6.0, 3.0),
        Trail("mountains", "Mountain pass", "Rocky slopes, boulders and snowy peaks. Long climbs.", 10.0, 2.0),
        Trail("autumn", "Autumn woods", "Golden birches and maples, leaves underfoot.", 4.0, 3.5),
        Trail("meadow", "Meadow", "Open grass and wildflowers, almost flat.", 2.0, 2.0),
        Trail("fields", "Farm fields", "Crops, hedges and lone trees on country lanes.", 2.5, 2.0),
        Trail("moor", "Heather moor", "Wide heather, rocks and lone pines in the wind.", 5.0, 2.5),
        Trail("desert", "Desert", "Sand, rocks and cacti under a big sky.", 4.0, 2.0),
        Trail("coast", "Coast path", "A sandy trail along the sea.", 3.0, 3.0),
        Trail("town", "Old town", "Pavements past terraced houses, lamps and street trees.", 3.0, 4.0),
    )

    /** How hard: a pace in km/h. */
    enum class Effort(val label: String, val kph: Double) {
        WALK("Walk", 5.0), BRISK("Brisk walk", 6.2), JOG("Jog", 8.0), RUN("Run", 10.0), FAST("Fast run", 12.0),
    }

    const val WARM_UP_MINUTES = 3
    const val EASY_END_MINUTES = 2

    fun trail(key: String): Trail = TRAILS.firstOrNull { it.key == key } ?: TRAILS.first()

    /** The workout: one-minute steps, a warm-up, the landscape's hills, an easy finish. */
    fun program(trail: Trail, minutes: Int, effort: Effort, seed: Int = trail.key.hashCode()): WorkoutProgram {
        val total = minutes.coerceIn(WARM_UP_MINUTES + EASY_END_MINUTES + 1, 240)
        val phase = (seed and 0xffff) / 65535.0 * 2 * PI
        val steps = (0 until total).map { m ->
            val warm = m < WARM_UP_MINUTES
            val easy = m >= total - EASY_END_MINUTES
            if (warm || easy) {
                val k = if (warm) 0.65 + 0.1 * m else 0.75
                WorkoutStep(round1(effort.kph * k), 0.0, 60)
            } else {
                // A wave of hills across the trail, with a shorter wave on top for variety.
                val t = (m - WARM_UP_MINUTES).toDouble() / (total - WARM_UP_MINUTES - EASY_END_MINUTES).coerceAtLeast(1)
                val wave = 0.5 + 0.35 * sin(2 * PI * trail.waves * t + phase) + 0.15 * sin(2 * PI * trail.waves * 2.7 * t + phase * 1.7)
                val incline = roundHalf((wave.coerceIn(0.0, 1.0) * trail.maxIncline))
                // Ease off a little on the climbs, as a runner on a real hill would.
                val speed = round1(effort.kph * (1.0 - incline * 0.015))
                WorkoutStep(speed, incline, 60)
            }
        }
        return WorkoutProgram("${trail.name} · $total min", steps)
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
    private fun roundHalf(v: Double) = (v * 2).roundToInt() / 2.0
}
