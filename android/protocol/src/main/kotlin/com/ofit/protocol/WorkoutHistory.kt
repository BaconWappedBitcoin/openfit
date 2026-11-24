package com.ofit.protocol

/** One finished workout, kept for the history screen. */
data class HistoryEntry(
    /** When it started, epoch milliseconds; also its id. */
    val startedAtMillis: Long,
    val name: String,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val calories: Double?,
    val verticalGainMeters: Double,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    /** COMPLETED, ABANDONED or FAILED. */
    val outcome: String,
) {
    /** Average speed over the whole workout, or null for a workout too short to say. */
    val averageSpeedKph: Double?
        get() = if (durationSeconds >= 10 && distanceMeters > 0) distanceMeters / 1000.0 / (durationSeconds / 3600.0) else null
}

/** Sums over a set of workouts. */
data class HistoryTotals(
    val workouts: Int,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val calories: Double,
    val verticalGainMeters: Double,
) {
    companion object {
        fun of(entries: List<HistoryEntry>) = HistoryTotals(
            workouts = entries.size,
            durationSeconds = entries.sumOf { it.durationSeconds },
            distanceMeters = entries.sumOf { it.distanceMeters },
            calories = entries.sumOf { it.calories ?: 0.0 },
            verticalGainMeters = entries.sumOf { it.verticalGainMeters },
        )
    }
}

/**
 * The history file: a JSON array of entries, newest first. A damaged entry is skipped rather than
 * losing the whole history.
 */
object HistoryCodec {

    /** Workouts shorter than this are warm-up taps and test starts, not worth keeping. */
    const val MIN_DURATION_SECONDS = 60L

    fun encode(entries: List<HistoryEntry>): String = entries.joinToString(",\n", "[\n", "\n]") { e ->
        buildString {
            append("{")
            append("\"startedAt\":").append(e.startedAtMillis)
            append(",\"name\":").append(Json.quote(e.name))
            append(",\"durationSeconds\":").append(e.durationSeconds)
            append(",\"distanceMeters\":").append(Json.number(e.distanceMeters))
            e.calories?.let { append(",\"calories\":").append(Json.number(it)) }
            append(",\"verticalGainMeters\":").append(Json.number(e.verticalGainMeters))
            e.averageHeartRate?.let { append(",\"avgHr\":").append(it) }
            e.maxHeartRate?.let { append(",\"maxHr\":").append(it) }
            append(",\"outcome\":").append(Json.quote(e.outcome))
            append("}")
        }
    }

    fun decode(text: String): List<HistoryEntry> {
        val root = runCatching { Json.parse(text) }.getOrNull() as? List<*> ?: return emptyList()
        return root.mapNotNull { item ->
            val o = item as? Map<*, *> ?: return@mapNotNull null
            runCatching {
                HistoryEntry(
                    startedAtMillis = (o["startedAt"] as Double).toLong(),
                    name = o["name"] as String,
                    durationSeconds = (o["durationSeconds"] as Double).toLong(),
                    distanceMeters = o["distanceMeters"] as Double,
                    calories = o["calories"] as? Double,
                    verticalGainMeters = (o["verticalGainMeters"] as? Double) ?: 0.0,
                    averageHeartRate = (o["avgHr"] as? Double)?.toInt(),
                    maxHeartRate = (o["maxHr"] as? Double)?.toInt(),
                    outcome = (o["outcome"] as? String) ?: "COMPLETED",
                )
            }.getOrNull()
        }.sortedByDescending { it.startedAtMillis }
    }
}
