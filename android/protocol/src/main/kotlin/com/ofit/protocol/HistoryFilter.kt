package com.ofit.protocol

/** Searching, filtering and sorting the History list. */
object HistoryFilter {

    enum class Show(val label: String) {
        ALL("All"),
        TREADMILL("Treadmill"),
        NO_TREADMILL("No treadmill"),
        COMPLETED("Completed"),
        LAST_30_DAYS("Last 30 days"),
    }

    enum class Sort(val label: String) { NEWEST("Newest"), LONGEST("Longest"), FARTHEST("Farthest"), MOST_CALORIES("Calories") }

    fun apply(
        entries: List<HistoryEntry>,
        query: String = "",
        show: Show = Show.ALL,
        sort: Sort = Sort.NEWEST,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<HistoryEntry> {
        val q = query.trim()
        return entries
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            .filter {
                when (show) {
                    Show.ALL -> true
                    Show.TREADMILL -> !it.noTreadmill
                    Show.NO_TREADMILL -> it.noTreadmill
                    Show.COMPLETED -> it.outcome == "COMPLETED"
                    Show.LAST_30_DAYS -> it.startedAtMillis >= nowMillis - 30L * 24 * 3600 * 1000
                }
            }
            .let { list ->
                when (sort) {
                    Sort.NEWEST -> list.sortedByDescending { it.startedAtMillis }
                    Sort.LONGEST -> list.sortedByDescending { it.durationSeconds }
                    Sort.FARTHEST -> list.sortedByDescending { it.distanceMeters }
                    Sort.MOST_CALORIES -> list.sortedByDescending { it.calories ?: 0.0 }
                }
            }
    }
}
