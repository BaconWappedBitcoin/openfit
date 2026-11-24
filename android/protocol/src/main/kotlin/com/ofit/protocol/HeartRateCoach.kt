package com.ofit.protocol

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Hold my zone" (spec 003): nudges belt speed to keep the runner's heart rate in a zone.
 *
 * Pure decision logic, called about once a second with the latest strap reading; it never sends
 * anything itself. Whatever it asks for still goes through the session and SafetyLimits.
 */
class HeartRateCoach(
    private val zones: HeartRateZones,
    /** Zone 1-5 to hold. */
    val targetZone: Int,
    private val config: Config = Config(),
) {
    data class Config(
        val checkEveryMillis: Long = 15_000,
        val averageOverMillis: Long = 30_000,
        val waitAfterStepMillis: Long = 45_000,
        val staleAfterMillis: Long = 10_000,
        val stepUpKph: Double = 0.2,
        val stepDownKph: Double = 0.3,
        val maxAboveStepKph: Double = 1.0,
        val dangerShareOfMax: Double = 0.95,
        val dangerForMillis: Long = 10_000,
        val maxJumpBpm: Int = 40,
        val jumpWindowMillis: Long = 2_000,
        /**
         * No speed-ups for this long after the coach is switched on: heart rate is naturally low at
         * the start, and pushing the belt hard then is the wrong response.
         * Slowing down is always allowed.
         */
        val warmupMillis: Long = 3 * 60_000,
    )

    /** How hard the coach pushes, chosen by the runner (Gentle / Normal / Strong). */
    enum class Aggressiveness(val label: String, val config: Config) {
        GENTLE(
            "Gentle",
            Config(checkEveryMillis = 30_000, waitAfterStepMillis = 60_000, stepUpKph = 0.1, stepDownKph = 0.3,
                maxAboveStepKph = 0.5, warmupMillis = 5 * 60_000),
        ),
        NORMAL("Normal", Config()),
        STRONG(
            "Strong",
            Config(checkEveryMillis = 10_000, waitAfterStepMillis = 30_000, stepUpKph = 0.3, stepDownKph = 0.4,
                maxAboveStepKph = 1.5, warmupMillis = 2 * 60_000),
        ),
        ;

        companion object {
            fun fromName(name: String?): Aggressiveness = entries.firstOrNull { it.name == name } ?: NORMAL
        }
    }

    sealed interface Decision {
        /** Leave the speed alone; [note] is shown on screen when there is something to say. */
        data class Hold(val note: String? = null) : Decision

        data class SetSpeed(val kph: Double) : Decision

        /** Switch the coach off, optionally slowing to [kph] first. */
        data class TurnOff(val reason: String, val kph: Double? = null) : Decision
    }

    /** The heart-rate band to hold, in BPM: [low, high). */
    val band: IntRange = run {
        val low = zones.lowerBound(targetZone.coerceIn(1, 5))
        val high = if (targetZone >= 5) zones.maxHr else zones.lowerBound(targetZone + 1) - 1
        low..high
    }

    private val samples = ArrayDeque<Pair<Long, Int>>()
    private var lastAdjustAt = Long.MIN_VALUE / 2
    private var stepChangedAt = Long.MIN_VALUE / 2
    private var dangerSince: Long? = null
    private var startedAt: Long? = null

    /** A program step changed: heart rate lags, so wait before judging it (C4). */
    fun onStepChanged(nowMillis: Long) {
        stepChangedAt = nowMillis
    }

    /**
     * One tick.
     *
     * @param bpm the strap's latest reading, or null; [bpmAtMillis] is when it arrived
     * @param currentKph the speed the belt was last told
     * @param stepKph the program step's own speed
     */
    fun decide(
        nowMillis: Long,
        bpm: Int?,
        bpmAtMillis: Long?,
        currentKph: Double,
        stepKph: Double,
        machineMinKph: Double,
    ): Decision {
        // S1: never act on a stale or missing reading.
        if (bpm == null || bpmAtMillis == null || nowMillis - bpmAtMillis > config.staleAfterMillis) {
            dangerSince = null
            return Decision.Hold("Heart rate lost: holding speed")
        }
        record(nowMillis, bpm)
        val since = startedAt ?: nowMillis.also { startedAt = it }

        // S2: very high heart rate for a while: back to the step's speed (never faster) and off.
        if (bpm >= zones.maxHr * config.dangerShareOfMax) {
            val since = dangerSince ?: nowMillis.also { dangerSince = it }
            if (nowMillis - since >= config.dangerForMillis) {
                return Decision.TurnOff("Heart rate very high", minOf(stepKph, currentKph))
            }
        } else {
            dangerSince = null
        }

        if (nowMillis - stepChangedAt < config.waitAfterStepMillis) return Decision.Hold()
        if (nowMillis - lastAdjustAt < config.checkEveryMillis) return Decision.Hold()

        val average = samples.map { it.second }.average().roundToInt()
        val ceiling = stepKph + config.maxAboveStepKph
        val target = when {
            average < band.first -> {
                // Warm-up hold: never push the belt up in the first minutes.
                if (nowMillis - since < config.warmupMillis) return Decision.Hold("Warming up: holding speed")
                minOf(currentKph + config.stepUpKph, ceiling)
            }
            average > band.last -> maxOf(currentKph - config.stepDownKph, machineMinKph)
            else -> return Decision.Hold()
        }
        val rounded = (target * 10).roundToInt() / 10.0
        if (abs(rounded - currentKph) < 0.05) return Decision.Hold()
        lastAdjustAt = nowMillis
        return Decision.SetSpeed(rounded)
    }

    private fun record(nowMillis: Long, bpm: Int) {
        // S3: a jump of 40+ BPM within 2 s is strap noise, not a heart.
        val last = samples.lastOrNull()
        if (last != null && nowMillis - last.first <= config.jumpWindowMillis && abs(bpm - last.second) > config.maxJumpBpm) return
        if (last == null || last.first != nowMillis) samples.addLast(nowMillis to bpm)
        while (samples.isNotEmpty() && nowMillis - samples.first().first > config.averageOverMillis) samples.removeFirst()
    }
}
