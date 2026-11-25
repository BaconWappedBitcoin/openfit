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
    /** For picking among the ways of saying things (seeded in tests). */
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
) {
    // Each pool hands out its lines in a shuffled order and only repeats once all have been used,
    // so the coach does not say the same thing over and over (user request 2026-11-24).
    private val encouragement = ShuffleBag(ENCOURAGEMENT, random)
    private val changeOpenings = ShuffleBag(CHANGE_OPENINGS, random)
    private val feelings = ShuffleBag(FEELINGS, random)
    private val climbTips = ShuffleBag(CLIMB_TIPS, random)
    private val descentTips = ShuffleBag(DESCENT_TIPS, random)
    private val faster = ShuffleBag(FASTER_TIPS, random)
    private val easier = ShuffleBag(EASIER_TIPS, random)
    private val progress = ShuffleBag(PROGRESS, random)
    private var nextCheckInGap = CHECK_IN_EVERY_SECONDS

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

    /**
     * Warn about coming speed and incline changes. Off with no treadmill (user report 2026-11-24):
     * there is no belt to change, so the runner sets their own speed.
     */
    var changeTips: Boolean = true

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
        if (s.isCoolingDown) return once("cool", nowMillis, urgent = true) { COOL.random(random) }
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
        if (s.totalElapsedMillis < 20_000) return once("start", nowMillis) { START.random(random) }
        if (s.totalRemainingMillis in 1..60_000) return once("last", nowMillis, urgent = true) { LAST.random(random) }
        if (total > 4 * 60_000 && s.totalElapsedMillis >= total / 2) {
            return once("half", nowMillis) { HALF.random(random).replace("{left}", minutesText(s.totalRemainingMillis)) }
        }

        // A check-in now and then, a minute and a half to two and a half apart so it never feels
        // like a timer: mostly a fact about the place when there are some, otherwise encouragement,
        // and now and again how far along (user requests 2026-11-24).
        val seconds = (s.totalElapsedMillis / 1000).toInt()
        if (seconds >= lastCheckIn + nextCheckInGap && nowMillis - lastAt >= MIN_GAP_MILLIS) {
            lastCheckIn = seconds
            nextCheckInGap = CHECK_IN_EVERY_SECONDS + random.nextInt(0, CHECK_IN_SPREAD_SECONDS + 1)
            val minutes = seconds / 60
            val kind = CHECK_IN_ORDER[checkIns++ % CHECK_IN_ORDER.length]
            val line = when (kind) {
                'f' -> nextFact() ?: encouragement.next()
                'p' -> progress.next()
                    .replace("{done}", if (minutes == 1) "1 minute" else "$minutes minutes")
                    .replace("{left}", minutesText(s.totalRemainingMillis))
                else -> encouragement.next()
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
        if (!changeTips) return null
        // A speed or incline set by hand stays through the next step (the engine keeps it), so
        // only what still follows the program can change (user report 2026-11-24).
        val nextSpeed = if (s.speedOverride) s.speedKph else s.nextSpeedKph ?: return null
        val nextIncline = if (s.inclineOverride) s.inclinePercent else s.nextInclinePercent ?: s.inclinePercent
        val inclineUp = nextIncline - s.inclinePercent
        val speedUp = nextSpeed - s.speedKph
        if (kotlin.math.abs(speedUp) < 0.8 && kotlin.math.abs(inclineUp) < 1.0) return null
        val what = buildList {
            if (kotlin.math.abs(speedUp) >= 0.8) add((if (speedUp > 0) "speed up to " else "speed down to ") + speedText(nextSpeed))
            if (kotlin.math.abs(inclineUp) >= 1.0) add((if (inclineUp > 0) "incline up to " else "incline down to ") + inclineText(nextIncline))
        }.joinToString(" and ")
        val feel = intensity(nextSpeed, nextIncline)
        val tip = when {
            inclineUp >= 1.0 -> climbTips.next()
            inclineUp <= -1.0 -> descentTips.next()
            speedUp > 0 -> faster.next()
            else -> easier.next()
        }
        // Said a different way each time: the opening, how hard, and the tip all vary.
        val opening = changeOpenings.next().replace("{what}", what)
        val hard = feelings.next().replace("{feel}", feel).replaceFirstChar { it.uppercase() }
        return "$opening $hard $tip"
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
        const val CHECK_IN_EVERY_SECONDS = 90
        const val CHECK_IN_SPREAD_SECONDS = 60
        /** f: a fact (or encouragement without facts), e: encouragement, p: progress. */
        const val CHECK_IN_ORDER = "fefpfe"

        val START = listOf(
            "Let's go! Ease into it, I'll set the pace.",
            "Here we go. Start easy and let your legs warm up.",
            "Good to have you. Nice and relaxed for the first few minutes.",
            "Off we go! Find your rhythm, there's no rush.",
        )
        val HALF = listOf(
            "Halfway there, {left} to go. You're looking strong!",
            "That's half done. {left} left, and you've got this.",
            "Halfway! Settle in for the second half, {left} to go.",
        )
        val LAST = listOf(
            "Last minute. Finish strong!",
            "One minute left. Give it what you've got!",
            "Final minute! Stay tall and bring it home.",
        )
        val COOL = listOf(
            "Great work. Walk it out and let your heart rate come down.",
            "That's the workout done. Nice and easy now, let your breathing settle.",
            "Well done! Ease off and enjoy the cool-down.",
        )
        val ENCOURAGEMENT = listOf(
            "Nice rhythm. Relax your shoulders.",
            "You're doing great. Keep breathing steady.",
            "Strong pace! Stay with me.",
            "Every step counts. Keep it going.",
            "Looking good. Light, quick feet.",
            "Keep your head up and enjoy the view.",
            "Smooth and steady. This is where fitness is built.",
            "You showed up today, and that's the hardest part.",
            "Loose hands, easy breathing. You're in control.",
            "Stay with it. You're stronger than you think.",
            "Good form. Tall posture, eyes forward.",
            "Nice work. Let your arms swing and drive you on.",
            "Find that groove and settle into it.",
            "Proud of this effort. Keep it rolling.",
            "Breathe in through the nose, out through the mouth. Nice.",
            "You're making this look easy.",
            "One step at a time. You've got this.",
            "Steady wins it. Keep that pace.",
            "Drop your shoulders and shake out your hands.",
            "Great consistency. That's how you get better.",
        )
        val PROGRESS = listOf(
            "{done} done, {left} to go. Keep it steady.",
            "{done} in the bag. {left} left.",
            "That's {done}. Just {left} to go.",
            "{left} left. You're well on your way.",
        )
        /** {what} is "speed up to 6.0 miles per hour and incline up to 4 percent". */
        val CHANGE_OPENINGS = listOf(
            "In 10 seconds, {what}.",
            "Heads up: {what} in about ten seconds.",
            "Coming up in ten seconds, {what}.",
            "Get ready, in ten seconds it's {what}.",
            "Ten seconds to the next change: {what}.",
        )
        /** {feel} is "an easy", "a steady", "a hard" or "a very hard". */
        val FEELINGS = listOf(
            "{feel} stretch.",
            "This one's {feel} one.",
            "Expect {feel} effort.",
            "{feel} section ahead.",
        )
        val CLIMB_TIPS = listOf(
            "Shorten your stride and keep your arms going.",
            "Lean in a little from the ankles.",
            "Small steps, strong arms.",
            "Eyes up the hill, keep the effort even.",
        )
        val DESCENT_TIPS = listOf(
            "Stay light on your feet.",
            "Let gravity help, but stay in control.",
            "Quick, soft steps on the way down.",
        )
        val FASTER_TIPS = listOf(
            "Get ready!",
            "Time to pick it up.",
            "Let's see what you've got.",
            "Quicken your cadence and go with it.",
        )
        val EASIER_TIPS = listOf(
            "Catch your breath.",
            "Use this to recover.",
            "Shake out your arms and relax.",
            "Nice, a chance to reset.",
        )
    }
}

/**
 * Hands out [items] in a random order, each once, before starting a new round; a new round never
 * begins with the line that ended the last one.
 */
class ShuffleBag<T>(private val items: List<T>, private val random: kotlin.random.Random) {
    private val left = ArrayDeque<T>()
    private var last: T? = null

    fun next(): T {
        if (left.isEmpty()) {
            val round = items.shuffled(random).toMutableList()
            if (round.size > 1 && round.first() == last) round.add(round.removeAt(0))
            left.addAll(round)
        }
        return left.removeFirst().also { last = it }
    }
}
