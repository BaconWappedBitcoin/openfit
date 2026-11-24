package com.ofit.protocol

import kotlin.math.roundToInt

/**
 * Intensity (P7): the same workout, video ones included, made easier or harder. Speeds are scaled
 * and the incline nudged; the name and the video's own pace stay, so History and plans still know
 * it, and match-speed plays the video faster or slower with the belt. Each profile keeps its own
 * level, and the level after a "Too easy" / "Too hard" rating is suggested.
 */
object Intensity {
    enum class Level(val label: String, val speedFactor: Double, val inclineDelta: Double) {
        GENTLE("Gentle", 0.8, -2.0),
        EASY("Easy", 0.9, -1.0),
        STANDARD("Standard", 1.0, 0.0),
        HARD("Hard", 1.1, 1.0),
        HARDEST("Hardest", 1.2, 2.0);

        fun easier(): Level = values()[(ordinal - 1).coerceAtLeast(0)]
        fun harder(): Level = values()[(ordinal + 1).coerceAtMost(values().size - 1)]

        companion object {
            fun of(name: String?): Level = values().firstOrNull { it.name == name } ?: STANDARD
        }
    }

    /** [program] at [level]. Flat or uphill steps never go below flat; downhill steps keep their slope. */
    fun apply(program: WorkoutProgram, level: Level): WorkoutProgram {
        if (level == Level.STANDARD) return program
        return program.copy(
            steps = program.steps.map { s ->
                val incline = if (s.inclinePercent < 0) s.inclinePercent else (s.inclinePercent + level.inclineDelta).coerceAtLeast(0.0)
                s.copy(
                    speedKph = ((s.speedKph * level.speedFactor) * 10).roundToInt() / 10.0,
                    inclinePercent = (incline * 2).roundToInt() / 2.0,
                )
            },
        )
    }

    /**
     * The level to suggest for [workoutName] from its last rated run: one harder after "Too easy",
     * one easier after "Too hard", otherwise the profile's own [current] level. Null when there is
     * nothing to suggest beyond [current].
     */
    fun suggest(history: List<HistoryEntry>, workoutName: String, current: Level): Suggestion? {
        val last = history.filter { it.name == workoutName && it.feel != null }.maxByOrNull { it.startedAtMillis } ?: return null
        return when (last.feel) {
            HistoryEntry.FEEL_EASY -> current.harder().takeIf { it != current }?.let { Suggestion(it, "Last time felt too easy") }
            HistoryEntry.FEEL_HARD -> current.easier().takeIf { it != current }?.let { Suggestion(it, "Last time felt too hard") }
            else -> null
        }
    }

    data class Suggestion(val level: Level, val reason: String)
}
