package com.ofit.app.workout

/**
 * What a top-bar tile can show (spec 002, R3). Only metrics this treadmill really reports, or that
 * ofit works out itself; no RPM / target RPM (bike and rower metrics).
 */
enum class HudStat(val pickerLabel: String) {
    INCLINE("Incline"),
    DISTANCE("Distance"),
    AVG_PACE("Avg. pace"),
    REMAINING("Time remaining"),
    ELAPSED("Time elapsed"),
    CALORIES("Cal (est)"),
    VERT_GAIN("Vertical gain"),
    SPEED("Speed"),
    HEART_RATE("Heart rate (BPM)"),
    AVG_HEART_RATE("Avg. heart rate"),
    ;

    companion object {
        /** The reference's seven, in its order. */
        val DEFAULT = listOf(INCLINE, DISTANCE, AVG_PACE, REMAINING, CALORIES, VERT_GAIN, SPEED)

        /** Fewer than this and the bar stops being useful; more and it no longer fits the tablet. */
        const val MIN_TILES = 3
        const val MAX_TILES = 8

        /** Read a saved list, dropping anything unknown; falls back to the default. */
        fun parse(saved: String?): List<HudStat> {
            if (saved.isNullOrBlank()) return DEFAULT
            val list = saved.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }.distinct()
            return if (list.size >= MIN_TILES) list.take(MAX_TILES) else DEFAULT
        }

        fun format(list: List<HudStat>): String = list.joinToString(",") { it.name }
    }
}
