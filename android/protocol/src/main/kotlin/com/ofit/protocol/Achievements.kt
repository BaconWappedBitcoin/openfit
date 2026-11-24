package com.ofit.protocol

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Achievements and the monthly distance challenge (P13), worked out from History alone: nothing is
 * stored, so they come back after a restore and never drift from the workouts that earned them.
 */
object Achievements {
    data class Badge(val id: String, val title: String, val detail: String, val earned: Boolean, val progress: Float)

    data class Challenge(val goalMeters: Double, val doneMeters: Double, val daysLeft: Int) {
        val progress: Float get() = (doneMeters / goalMeters).toFloat().coerceIn(0f, 1f)
        val complete: Boolean get() = doneMeters >= goalMeters
    }

    /** 40 km a month: about 10 km a week, an achievable aim for a regular walker. */
    const val MONTHLY_GOAL_METERS = 40_000.0

    /** The same aim as a round figure in miles (25 mi), for imperial users. */
    const val MONTHLY_GOAL_METERS_IMPERIAL = 25 * 1609.344

    fun badges(entries: List<HistoryEntry>, bestWeekStreak: Int): List<Badge> {
        val count = entries.size
        val hours = entries.sumOf { it.durationSeconds } / 3600.0
        val km = entries.sumOf { it.distanceMeters } / 1000.0
        val climb = entries.sumOf { it.verticalGainMeters }
        val longestMinutes = (entries.maxOfOrNull { it.durationSeconds } ?: 0L) / 60.0
        fun badge(id: String, title: String, detail: String, have: Double, need: Double) =
            Badge(id, title, detail, have >= need, (have / need).toFloat().coerceIn(0f, 1f))
        return listOf(
            badge("first", "First steps", "Finish a workout", count.toDouble(), 1.0),
            badge("w10", "Regular", "10 workouts", count.toDouble(), 10.0),
            badge("w25", "Committed", "25 workouts", count.toDouble(), 25.0),
            badge("w50", "Dedicated", "50 workouts", count.toDouble(), 50.0),
            badge("w100", "Century", "100 workouts", count.toDouble(), 100.0),
            badge("m30", "Half hour", "A 30-minute workout", longestMinutes, 30.0),
            badge("m60", "Full hour", "A 60-minute workout", longestMinutes, 60.0),
            badge("h10", "Ten hours", "10 hours in all", hours, 10.0),
            badge("h50", "Fifty hours", "50 hours in all", hours, 50.0),
            badge("k42", "Marathon", "A marathon's distance in all", km, 42.195),
            badge("k100", "Hundred", "100 km in all", km, 100.0),
            badge("k500", "Five hundred", "500 km in all", km, 500.0),
            badge("s2", "Two in a row", "Workouts 2 weeks in a row", bestWeekStreak.toDouble(), 2.0),
            badge("s4", "Month strong", "Workouts 4 weeks in a row", bestWeekStreak.toDouble(), 4.0),
            badge("s8", "Habit", "Workouts 8 weeks in a row", bestWeekStreak.toDouble(), 8.0),
            badge("c500", "Hill climber", "500 m climbed in all", climb, 500.0),
        )
    }

    fun monthly(
        entries: List<HistoryEntry>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        goalMeters: Double = MONTHLY_GOAL_METERS,
    ): Challenge {
        val month = YearMonth.from(now.atZone(zone))
        val done = entries.filter { YearMonth.from(Instant.ofEpochMilli(it.startedAtMillis).atZone(zone)) == month }
            .sumOf { it.distanceMeters }
        val daysLeft = month.lengthOfMonth() - now.atZone(zone).dayOfMonth
        return Challenge(goalMeters, done, daysLeft)
    }
}
