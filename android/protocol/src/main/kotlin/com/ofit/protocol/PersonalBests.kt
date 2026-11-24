package com.ofit.protocol

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The runner's best workouts so far, and how many weeks in a row they have worked out. */
data class PersonalBests(
    val longest: HistoryEntry?,
    val farthest: HistoryEntry?,
    val mostClimb: HistoryEntry?,
    val mostCalories: HistoryEntry?,
    /** Weeks in a row (Monday to Sunday) with at least one workout, up to this week or last week. */
    val weekStreak: Int,
    /** The longest such run ever. */
    val bestWeekStreak: Int,
) {
    companion object {
        fun of(entries: List<HistoryEntry>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): PersonalBests {
            val weeks = entries.map { weekOf(Instant.ofEpochMilli(it.startedAtMillis), zone) }.toSortedSet()
            val thisWeek = weekOf(now, zone)
            // A streak is still alive if this week has no workout yet but last week had one.
            var start = if (thisWeek in weeks) thisWeek else thisWeek.minusWeeks(1)
            var streak = 0
            while (start in weeks) {
                streak++
                start = start.minusWeeks(1)
            }
            var best = 0
            var run = 0
            var previous: LocalDate? = null
            for (week in weeks) {
                run = if (previous != null && previous.plusWeeks(1) == week) run + 1 else 1
                best = maxOf(best, run)
                previous = week
            }
            return PersonalBests(
                longest = entries.maxByOrNull { it.durationSeconds },
                farthest = entries.filter { it.distanceMeters > 0 }.maxByOrNull { it.distanceMeters },
                mostClimb = entries.filter { it.verticalGainMeters > 0 }.maxByOrNull { it.verticalGainMeters },
                mostCalories = entries.filter { (it.calories ?: 0.0) > 0 }.maxByOrNull { it.calories ?: 0.0 },
                weekStreak = streak,
                bestWeekStreak = best,
            )
        }

        /** The Monday that starts the week [instant] falls in. */
        private fun weekOf(instant: Instant, zone: ZoneId): LocalDate =
            instant.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
