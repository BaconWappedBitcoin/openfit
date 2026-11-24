package com.ofit.protocol

/**
 * Where a workout was when OpenFit last saw it running (P4): kept every few seconds while it runs
 * and cleared when it ends normally, so after a crash, an update or a closed app the runner can pick
 * up where they left off.
 */
data class ResumePoint(
    val workoutId: String,
    val name: String,
    val elapsedMillis: Long,
    val totalMillis: Long,
    val savedAtMillis: Long,
) {
    /** Worth offering: recent, past the first minute, and with more than a minute still to go. */
    fun worthOffering(nowMillis: Long): Boolean =
        nowMillis - savedAtMillis in 0..MAX_AGE_MILLIS &&
            elapsedMillis >= MIN_DONE_MILLIS &&
            totalMillis - elapsedMillis >= MIN_LEFT_MILLIS

    companion object {
        const val MAX_AGE_MILLIS = 12 * 3_600_000L
        const val MIN_DONE_MILLIS = 60_000L
        const val MIN_LEFT_MILLIS = 60_000L
        /** How often the point is written while a workout runs. */
        const val SAVE_EVERY_MILLIS = 5_000L
    }
}
