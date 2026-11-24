package com.ofit.protocol

/** One moment of a workout, kept for its charts: seconds in, speed, incline, heart rate if known. */
data class HistorySample(val seconds: Int, val speedKph: Double, val inclinePercent: Double, val heartRate: Int?)

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
    /** Run on screen only, with no treadmill; distance and climb are then worked out from the program. */
    val noTreadmill: Boolean = false,
    /** Seconds in each heart-rate zone, index 0 (below zone 1) to 5; null without a strap or zones. */
    val zoneSeconds: List<Long>? = null,
    /** Speed, incline and heart rate over the workout, every [HistoryCodec.SAMPLE_SECONDS]; empty for old entries. */
    val samples: List<HistorySample> = emptyList(),
    /** The runner's own rating after the workout, 1 to 5 stars (P15); null if not rated. */
    val stars: Int? = null,
    /** How it felt: [FEEL_EASY], [FEEL_RIGHT] or [FEEL_HARD]; null if not said. */
    val feel: String? = null,
) {
    companion object {
        const val FEEL_EASY = "easy"
        const val FEEL_RIGHT = "right"
        const val FEEL_HARD = "hard"
    }

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

    /** How often a sample is kept for the workout's charts. */
    const val SAMPLE_SECONDS = 10

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
            if (e.noTreadmill) append(",\"noTreadmill\":true")
            e.zoneSeconds?.let { append(",\"zoneSeconds\":").append(it.joinToString(",", "[", "]")) }
            e.stars?.let { append(",\"stars\":").append(it) }
            e.feel?.let { append(",\"feel\":").append(Json.quote(it)) }
            if (e.samples.isNotEmpty()) {
                // Compact: [seconds, speed, incline, heart rate or -1] per sample.
                append(",\"samples\":")
                append(
                    e.samples.joinToString(",", "[", "]") { s ->
                        "[${s.seconds},${"%.2f".format(java.util.Locale.ROOT, s.speedKph)},${"%.1f".format(java.util.Locale.ROOT, s.inclinePercent)},${s.heartRate ?: -1}]"
                    },
                )
            }
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
                    noTreadmill = o["noTreadmill"] == true,
                    zoneSeconds = (o["zoneSeconds"] as? List<*>)?.mapNotNull { (it as? Double)?.toLong() }?.takeIf { it.size == 6 },
                    samples = (o["samples"] as? List<*>)?.mapNotNull { item ->
                        val a = (item as? List<*>)?.map { it as? Double } ?: return@mapNotNull null
                        if (a.size < 4 || a.any { it == null }) return@mapNotNull null
                        HistorySample(a[0]!!.toInt(), a[1]!!, a[2]!!, a[3]!!.toInt().takeIf { it > 0 })
                    } ?: emptyList(),
                    stars = (o["stars"] as? Double)?.toInt()?.takeIf { it in 1..5 },
                    feel = (o["feel"] as? String)?.takeIf { it in setOf(HistoryEntry.FEEL_EASY, HistoryEntry.FEEL_RIGHT, HistoryEntry.FEEL_HARD) },
                )
            }.getOrNull()
        }.sortedByDescending { it.startedAtMillis }
    }
}
