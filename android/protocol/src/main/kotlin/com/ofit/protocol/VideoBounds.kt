package com.ofit.protocol

/**
 * A video workout's suggested start and end (user request 2026-11-24): where the intro ends and the
 * outro starts, in seconds from the video's start. The same rules as the backend's videoBounds and
 * cleanBounds, so a workout made on the tablet matches one made by the server.
 */
object VideoBounds {
    /** At least this much run must be left between the start and the end. */
    const val MIN_RUN_SECONDS = 60

    /**
     * From the AI's per-window answers: the end of the windows read as intro at the start, and the
     * start of those read as outro at the end. An intro over 40 % of the video, an outro starting
     * before 60 %, or less than a minute of run between them is not believed.
     */
    fun fromReadings(readings: List<VideoProgramBuilder.WindowReading>): Pair<Int?, Int?> {
        val sorted = readings.sortedBy { it.startSeconds }
        if (sorted.size < 3) return null to null
        val total = sorted.last().startSeconds + sorted.last().durationSeconds
        var intro = 0
        for (r in sorted) {
            if (r.part != "intro") break
            intro = r.startSeconds + r.durationSeconds
        }
        var outro = total
        for (r in sorted.asReversed()) {
            if (r.part != "outro") break
            outro = r.startSeconds
        }
        val introOk = intro > 0 && intro <= total * 0.4
        val outroOk = outro < total && outro >= total * 0.6
        if ((if (outroOk) outro else total) - (if (introOk) intro else 0) < MIN_RUN_SECONDS) return null to null
        return (if (introOk) intro else null) to (if (outroOk) outro else null)
    }

    /** Keep a start and end only when they are inside a program [total] seconds long, with a minute between. */
    fun clean(introEnd: Int?, outroStart: Int?, total: Int): Pair<Int?, Int?> {
        val i = introEnd?.takeIf { it in 1 until total }
        val o = outroStart?.takeIf { it in 1 until total }
        if (i != null && o != null && o - i < MIN_RUN_SECONDS) return null to null
        return i to o
    }

    /**
     * Where a workout starts when skipping: the intro's end, or null to start at the beginning.
     * [skip] is the runner's "Skip intro and outro" choice.
     */
    fun startSeconds(program: WorkoutProgram, skip: Boolean): Int? = if (skip) program.introEndSeconds else null

    /** True once a skipping workout has reached the outro, so it should end there. */
    fun reachedOutro(program: WorkoutProgram, skip: Boolean, elapsedSeconds: Long): Boolean {
        val outro = program.outroStartSeconds ?: return false
        return skip && elapsedSeconds >= outro
    }
}
