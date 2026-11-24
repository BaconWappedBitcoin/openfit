package com.ofit.protocol

/**
 * The "For you" row on Browse (P18): workouts like the ones the runner usually does, that they have
 * not run in the last couple of days, longest-unrun first. "Like" means the same effort and type
 * (WorkoutRating) as their most frequent kind of workout over the last 30 days.
 */
object ForYou {
    data class Candidate(val id: String, val name: String, val program: WorkoutProgram)

    const val RECENT_MILLIS = 2 * 24 * 3_600_000L
    const val LOOKBACK_MILLIS = 30 * 24 * 3_600_000L

    fun pick(candidates: List<Candidate>, history: List<HistoryEntry>, nowMillis: Long, limit: Int = 8): List<Candidate> {
        if (candidates.isEmpty()) return emptyList()
        val byName = candidates.associateBy { it.name }
        val recentHistory = history.filter { nowMillis - it.startedAtMillis in 0..LOOKBACK_MILLIS }
        // The runner's usual kind of workout: the commonest (level, kind) among recent runs we can rate.
        val usual = recentHistory.mapNotNull { byName[it.name] }
            .map { WorkoutRating.of(it.program).let { r -> r.level to r.kind } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val lastRun = history.groupBy { it.name }.mapValues { (_, runs) -> runs.maxOf { it.startedAtMillis } }

        return candidates
            .filter { c -> lastRun[c.name]?.let { nowMillis - it >= RECENT_MILLIS } ?: true }
            .sortedWith(
                compareByDescending<Candidate> { c ->
                    // Same effort and type as usual first, then same type, then the rest.
                    val r = WorkoutRating.of(c.program)
                    when {
                        usual == null -> 0
                        r.level == usual.first && r.kind == usual.second -> 2
                        r.kind == usual.second -> 1
                        else -> 0
                    }
                }.thenBy { c -> lastRun[c.name] ?: Long.MIN_VALUE }, // never or longest ago first
            )
            .take(limit)
    }
}
