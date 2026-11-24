package com.ofit.protocol

/**
 * How hard a workout is and whether it is a walk or a run, worked out from its steps
 * (user request, 2026-11-24), for badges and filters.
 *
 * Effort is the time-weighted average of the ACSM metabolic equations for each step, in METs
 * (walking below 6.4 km/h, running at or above it), with grade counted uphill only:
 *   walking VO2 = 0.1·v + 1.8·v·grade + 3.5, running VO2 = 0.2·v + 0.9·v·grade + 3.5 (v in m/min).
 * Easy is under 5 METs (a steady walk), Hard is 8 METs or more (running, or steep fast walking),
 * Medium in between. A long workout (45 min or more) counts one level harder, at most Hard.
 */
object WorkoutRating {
    enum class Level(val label: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard") }
    enum class Kind(val label: String) { WALK("Walk"), RUN("Run"), RUN_WALK("Run-walk") }

    data class Rating(val level: Level, val kind: Kind, val mets: Double) {
        /** "Easy walk", "Hard run", "Medium run-walk". */
        val label: String get() = "${level.label} ${kind.label.lowercase()}"
    }

    const val RUN_FROM_KPH = 6.4

    fun of(program: WorkoutProgram): Rating {
        val steps = program.steps.filter { it.durationSeconds > 0 }
        val seconds = steps.sumOf { it.durationSeconds }.coerceAtLeast(1)
        val mets = steps.sumOf { mets(it.speedKph, it.inclinePercent) * it.durationSeconds } / seconds
        val runShare = steps.filter { it.speedKph >= RUN_FROM_KPH }.sumOf { it.durationSeconds }.toDouble() / seconds
        return classify(mets, runShare, program.totalDurationSeconds)
    }

    /**
     * The rating from a workout's average METs, the share of its time spent running and its length;
     * the community library sends these two numbers for each workout instead of its steps.
     */
    fun classify(mets: Double, runShare: Double, totalSeconds: Int): Rating {
        val kind = when {
            runShare >= 0.6 -> Kind.RUN
            runShare <= 0.15 -> Kind.WALK
            else -> Kind.RUN_WALK
        }
        var level = when {
            mets < 5.0 -> Level.EASY
            mets < 8.0 -> Level.MEDIUM
            else -> Level.HARD
        }
        if (totalSeconds >= 45 * 60 && level != Level.HARD) level = Level.entries[level.ordinal + 1]
        return Rating(level, kind, mets)
    }

    /** METs for one step (ACSM equations). */
    fun mets(speedKph: Double, inclinePercent: Double): Double {
        if (speedKph <= 0.0) return 1.0
        val v = speedKph * 1000 / 60 // m/min
        val grade = inclinePercent.coerceAtLeast(0.0) / 100
        val vo2 = if (speedKph < RUN_FROM_KPH) 0.1 * v + 1.8 * v * grade + 3.5 else 0.2 * v + 0.9 * v * grade + 3.5
        return vo2 / 3.5
    }
}
