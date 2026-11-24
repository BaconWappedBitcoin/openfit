package com.ofit.protocol

/**
 * An estimate of how far the AI has got with a video, for a progress bar. The server sends no
 * progress, so this grows with time against the expected length and waits at 95 % until done.
 */
object AnalysisProgress {
    fun estimate(elapsedMillis: Long, expectedMillis: Long): Float {
        if (expectedMillis <= 0) return 0f
        val linear = elapsedMillis.toFloat() / expectedMillis
        // Straight up to 80 %, then slower towards 95 %, never reaching it on a guess.
        return if (linear <= 0.8f) linear.coerceAtLeast(0f) else (0.8f + 0.15f * (1f - 1f / (1f + (linear - 0.8f) * 3f))).coerceAtMost(0.95f)
    }

    fun stage(share: Float): String = when {
        share < 0.15f -> "Fetching the video"
        share < 0.55f -> "Watching for pace"
        share < 0.8f -> "Measuring the hills"
        else -> "Building the steps"
    }
}
