package com.ofit.protocol

/**
 * A History entry as what Health Connect keeps (P17), worked out here so it can be tested on the
 * JVM; the app turns it into Health Connect records. Every record carries [clientId], so sending the
 * same workout again replaces it rather than adding a second copy.
 */
object HealthExport {
    data class Session(
        val clientId: String,
        val title: String,
        val startMillis: Long,
        val endMillis: Long,
        /** Running on the treadmill; otherwise walking. */
        val running: Boolean,
        val distanceMeters: Double,
        val kcal: Double?,
        val climbMeters: Double,
        /** (time, beats per minute), inside the session. */
        val heartRate: List<Pair<Long, Int>>,
        /** (time, metres per second), inside the session. */
        val speed: List<Pair<Long, Double>>,
    )

    /** Below this average a treadmill workout counts as walking. */
    const val RUNNING_KPH = 7.0

    /** Null for a workout too short to be worth sending, or one that failed. */
    fun of(entry: HistoryEntry): Session? {
        if (entry.durationSeconds < 60 || entry.outcome == "FAILED") return null
        val start = entry.startedAtMillis
        val end = start + entry.durationSeconds * 1000
        fun at(seconds: Int) = (start + seconds * 1000L).coerceIn(start, end - 1)
        val samples = entry.samples.filter { it.seconds >= 0 && it.seconds < entry.durationSeconds }
        // Running if most of the moving time was at running speed (a run-walk counts by its majority).
        val moving = samples.filter { it.speedKph > 0.5 }
        val running = if (moving.isNotEmpty()) moving.count { it.speedKph >= RUNNING_KPH } * 2 > moving.size
        else (entry.averageSpeedKph ?: 0.0) >= RUNNING_KPH
        return Session(
            clientId = "openfit-${entry.startedAtMillis}",
            title = entry.name,
            startMillis = start,
            endMillis = end,
            running = running,
            distanceMeters = entry.distanceMeters.coerceAtLeast(0.0),
            kcal = entry.calories?.takeIf { it > 0 },
            climbMeters = entry.verticalGainMeters.coerceAtLeast(0.0),
            heartRate = samples.mapNotNull { s -> s.heartRate?.takeIf { it in 30..250 }?.let { at(s.seconds) to it } }.distinctBy { it.first },
            speed = samples.map { at(it.seconds) to it.speedKph / 3.6 }.distinctBy { it.first },
        )
    }
}
