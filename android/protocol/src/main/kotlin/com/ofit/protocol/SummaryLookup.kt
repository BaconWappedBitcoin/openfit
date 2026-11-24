package com.ofit.protocol

/**
 * Which History entry the workout summary shows. The workout's start is taken from the wall clock
 * when it began: the program clock runs slower or faster than real time when the video plays at a
 * matched rate, so working the start out from it missed the entry (seen 2026-11-24: a 1:37 run at
 * 0.8x was saved but the summary said "under a minute").
 */
object SummaryLookup {
    /** How much earlier than the recorded start an entry may begin (History rounds its own start). */
    const val SLACK_MILLIS = 15_000L

    fun find(entries: List<HistoryEntry>, name: String, startedWallMillis: Long): HistoryEntry? =
        entries.firstOrNull { it.name == name && it.startedAtMillis >= startedWallMillis - SLACK_MILLIS }
}
