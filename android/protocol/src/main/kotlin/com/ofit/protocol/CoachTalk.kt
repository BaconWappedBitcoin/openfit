package com.ofit.protocol

/**
 * What the virtual coach says, and when (user requests, 2026-11-24):
 *  - about 10 s before a change: the new speed and incline and how hard it will be;
 *  - a greeting, halfway with the time left, the last minute, the cool-down;
 *  - every few minutes a check-in, taking turns: encouragement, a fact about the place in the
 *    video (when the AI found some), and how far along the workout is.
 * Never more than one line every [MIN_GAP_MILLIS], except a warning about a change, which is worth
 * hearing on time.
 */
class CoachTalk(
    /** How a speed is said, in the runner's units: "6.0 miles per hour". */
    private val speedText: (Double) -> String = { "%.1f kilometres per hour".format(java.util.Locale.ROOT, it) },
) {
    private val said = mutableSetOf<String>()
    private var lastAt = Long.MIN_VALUE / 2
    private var lastCheckIn = 0
    private var checkIns = 0
    private var warnedStep = -1
    private var factIndex = 0

    /** Facts about the place in the video, to mention now and then. */
    var facts: List<String> = emptyList()

    /** A landmark seen in the video: when it comes into view, its name and a short fact. */
    data class Landmark(val seconds: Int, val name: String, val fact: String)

    /** Landmarks along the video, announced a few seconds before the runner reaches each. */
    var landmarks: List<Landmark> = emptyList()
    private val announced = mutableSetOf<Int>()

    /** The workout's usual pace, to judge how hard a step is. */
    var referenceKph: Double? = null

    fun reset() {
        said.clear()
        lastAt = Long.MIN_VALUE / 2
        lastCheckIn = 0
        checkIns = 0
        warnedStep = -1
        factIndex = 0
        announced.clear()
    }

    /** The line to say now, or null. Call about once a second. */
    fun update(s: WorkoutEngineSnapshot, nowMillis: Long): String? {
        if (s.state == WorkoutEngineState.IDLE) return null
        if (s.isCoolingDown) return once("cool", nowMillis, urgent = true) { "Great work. Walk it out and let your heart rate come down." }
        if (!s.isRunning) return null

        val total = s.totalElapsedMillis + s.totalRemainingMillis
        // Ahead of a change: about 10 s before the step ends.
        if (s.stepRemainingMillis in 5_000..11_000 && warnedStep != s.stepIndex) {
            val line = changeLine(s)
            if (line != null) {
                warnedStep = s.stepIndex
                return say(line, nowMillis)
            }
        }
        // Keep quiet just before a change is announced, so a long line never runs into the warning.
        if (s.stepRemainingMillis in 11_001..QUIET_BEFORE_CHANGE_MILLIS && changeLine(s) != null) return null
        // A landmark coming into view in the next few seconds (the workout clock follows the video).
        val second = (s.totalElapsedMillis / 1000).toInt()
        landmarks.firstOrNull { it.seconds !in announced && second in (it.seconds - LANDMARK_LEAD_SECONDS)..(it.seconds + 5) }?.let { l ->
            announced += l.seconds
            // Skipped past (a seek): no announcement for landmarks already behind.
            landmarks.filter { it.seconds < l.seconds }.forEach { announced += it.seconds }
            return say("Coming up: ${l.name}." + if (l.fact.isNotBlank()) " ${l.fact}" else "", nowMillis)
        }
        if (s.totalElapsedMillis < 20_000) return once("start", nowMillis) { "Let's go! Ease into it, I'll set the pace." }
        if (s.totalRemainingMillis in 1..60_000) return once("last", nowMillis, urgent = true) { "Last minute. Finish strong!" }
        if (total > 4 * 60_000 && s.totalElapsedMillis >= total / 2) {
            return once("half", nowMillis) { "Halfway there, ${minutesText(s.totalRemainingMillis)} to go. You're looking strong!" }
        }

        // A check-in every minute (user 2026-11-24: "tell me more", then "every minute"): a fact every other time,
        // with encouragement and how far along in between.
        val seconds = (s.totalElapsedMillis / 1000).toInt()
        if (seconds >= lastCheckIn + CHECK_IN_EVERY_SECONDS && nowMillis - lastAt >= MIN_GAP_MILLIS) {
            lastCheckIn = seconds
            val minutes = seconds / 60
            val line = when (checkIns++ % 4) {
                0, 2 -> nextFact() ?: ENCOURAGEMENT[checkIns % ENCOURAGEMENT.size]
                3 -> "$minutes minutes done, ${minutesText(s.totalRemainingMillis)} to go. Keep it steady."
                else -> ENCOURAGEMENT[(checkIns / 4) % ENCOURAGEMENT.size]
            }
            return say(line, nowMillis)
        }
        return null
    }

    private fun nextFact(): String? {
        if (facts.isEmpty()) return null
        return "Fun fact: " + facts[factIndex++ % facts.size]
    }

    private fun changeLine(s: WorkoutEngineSnapshot): String? {
        val nextSpeed = s.nextSpeedKph ?: return null
        val nextIncline = s.nextInclinePercent ?: s.inclinePercent
        val inclineUp = nextIncline - s.inclinePercent
        val speedUp = nextSpeed - s.speedKph
        if (kotlin.math.abs(speedUp) < 0.8 && kotlin.math.abs(inclineUp) < 1.0) return null
        val what = buildList {
            if (kotlin.math.abs(speedUp) >= 0.8) add((if (speedUp > 0) "speed up to " else "speed down to ") + speedText(nextSpeed))
            if (kotlin.math.abs(inclineUp) >= 1.0) add((if (inclineUp > 0) "incline up to " else "incline down to ") + inclineText(nextIncline))
        }.joinToString(" and ")
        val feel = intensity(nextSpeed, nextIncline)
        val tip = when {
            inclineUp >= 1.0 -> "Shorten your stride and keep your arms going."
            inclineUp <= -1.0 -> "Stay light on your feet."
            speedUp > 0 -> "Get ready!"
            else -> "Catch your breath."
        }
        return "In 10 seconds, $what. ${feel.replaceFirstChar { it.uppercase() }} stretch. $tip"
    }

    /** How hard a step is, against the workout's usual pace: easy, steady, hard or very hard. */
    fun intensity(speedKph: Double, inclinePercent: Double): String {
        val reference = referenceKph?.takeIf { it > 0 } ?: speedKph
        val effort = speedKph / reference + inclinePercent.coerceAtLeast(0.0) * 0.05
        return when {
            effort < 0.85 -> "an easy"
            effort < 1.08 -> "a steady"
            effort < 1.3 -> "a hard"
            else -> "a very hard"
        }
    }

    private fun inclineText(percent: Double): String =
        if (percent % 1.0 == 0.0) "${percent.toInt()} percent" else "%.1f percent".format(java.util.Locale.ROOT, percent)

    private fun minutesText(millis: Long): String {
        val m = ((millis + 30_000) / 60_000).toInt()
        return if (m <= 1) "about a minute" else "$m minutes"
    }

    private fun once(key: String, nowMillis: Long, urgent: Boolean = false, line: () -> String): String? {
        if (key in said) return null
        if (!urgent && nowMillis - lastAt < MIN_GAP_MILLIS) return null
        said += key
        return say(line(), nowMillis)
    }

    private fun say(line: String, nowMillis: Long): String {
        lastAt = nowMillis
        return line
    }

    companion object {
        const val MIN_GAP_MILLIS = 20_000L
        const val LANDMARK_LEAD_SECONDS = 6
        /** No other line in the last stretch before a change warning (a fact takes about 10 s to say). */
        const val QUIET_BEFORE_CHANGE_MILLIS = 26_000L
        const val CHECK_IN_EVERY_SECONDS = 60
        val ENCOURAGEMENT = listOf(
            "Nice rhythm. Relax your shoulders.",
            "You're doing great. Keep breathing steady.",
            "Strong pace! Stay with me.",
            "Every step counts. Keep it going.",
        )
    }
}
