package com.ofit.protocol

/**
 * The standard Bluetooth Heart Rate profile: service 0x180D, measurement characteristic 0x2A37.
 *
 * Every chest strap and most watches in "broadcast heart rate" mode speak it (Polar, Garmin, Wahoo,
 * Coros, ...), so no vendor code is needed. Spec 002, section 3b.
 */
object HeartRateProfile {
    const val SERVICE = "0000180d-0000-1000-8000-00805f9b34fb"
    const val MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"

    /** A strap silent for this long is treated as gone (spec 002, H3). */
    const val STALE_AFTER_MILLIS = 5_000L
}

/** One Heart Rate Measurement notification, decoded. */
data class HeartRateMeasurement(
    val bpm: Int,
    /** null when the strap does not report skin contact at all. */
    val skinContact: Boolean?,
    val energyExpendedKj: Int?,
    /** RR intervals in milliseconds. */
    val rrIntervalsMillis: List<Int>,
) {
    /** Whether the value can be shown: a strap that says it has no contact is reading noise. */
    val usable: Boolean get() = skinContact != false && bpm in 25..250

    companion object {
        /**
         * Decode the characteristic value. Flags byte: bit 0 = 16-bit BPM, bits 1-2 = contact
         * status (bit 2 = supported, bit 1 = detected), bit 3 = energy expended present,
         * bit 4 = RR intervals present (1/1024 s each). Returns null for a malformed value.
         */
        fun parse(value: ByteArray): HeartRateMeasurement? {
            if (value.isEmpty()) return null
            val flags = value[0].toInt() and 0xFF
            var i = 1
            fun u8(): Int? = if (i < value.size) (value[i++].toInt() and 0xFF) else null
            fun u16(): Int? {
                if (i + 1 >= value.size) return null
                val v = (value[i].toInt() and 0xFF) or ((value[i + 1].toInt() and 0xFF) shl 8)
                i += 2
                return v
            }
            val bpm = (if (flags and 0x01 != 0) u16() else u8()) ?: return null
            val contactSupported = flags and 0x04 != 0
            val contact = if (contactSupported) flags and 0x02 != 0 else null
            val energy = if (flags and 0x08 != 0) (u16() ?: return null) else null
            val rr = mutableListOf<Int>()
            if (flags and 0x10 != 0) {
                while (i + 1 < value.size) {
                    val raw = u16() ?: break
                    rr += (raw * 1000L / 1024L).toInt()
                }
            }
            return HeartRateMeasurement(bpm, contact, energy, rr)
        }
    }
}

/**
 * Heart-rate zones 1-5 as a share of max heart rate (50-60, 60-70, 70-80, 80-90, 90-100 %), the
 * split most watches use. Below 50 % is zone 0 (resting / warm-up).
 */
data class HeartRateZones(val maxHr: Int) {
    init {
        require(maxHr in 100..230) { "max heart rate $maxHr is out of range" }
    }

    /** The zone (0-5) for a heart rate. */
    fun zoneOf(bpm: Int): Int {
        val share = bpm.toDouble() / maxHr
        return when {
            share < 0.5 -> 0
            share < 0.6 -> 1
            share < 0.7 -> 2
            share < 0.8 -> 3
            share < 0.9 -> 4
            else -> 5
        }
    }

    /** The lowest heart rate of a zone (1-5). */
    fun lowerBound(zone: Int): Int = (maxHr * (0.4 + 0.1 * zone.coerceIn(1, 5))).toInt()

    companion object {
        /** Max heart rate from the user's own value, or 220 minus age, or none (spec 002, H5). */
        fun of(userMaxHr: Int?, age: Int?): HeartRateZones? {
            val max = userMaxHr ?: age?.takeIf { it in 10..100 }?.let { 220 - it } ?: return null
            return runCatching { HeartRateZones(max) }.getOrNull()
        }
    }
}

/** Where the shown heart rate comes from. */
enum class HeartRateSource { STRAP, CONSOLE, NONE }

/**
 * Picks the heart rate to show (spec 002, H3): a fresh strap reading wins over the console's own
 * grip pulse; a strap silent for [HeartRateProfile.STALE_AFTER_MILLIS] falls back to the console.
 */
object HeartRateSelector {
    fun select(
        strapBpm: Int?,
        strapAtMillis: Long?,
        consoleBpm: Int?,
        nowMillis: Long,
    ): Pair<Int?, HeartRateSource> {
        if (strapBpm != null && strapAtMillis != null &&
            nowMillis - strapAtMillis <= HeartRateProfile.STALE_AFTER_MILLIS
        ) {
            return strapBpm to HeartRateSource.STRAP
        }
        if (consoleBpm != null && consoleBpm > 0) return consoleBpm to HeartRateSource.CONSOLE
        return null to HeartRateSource.NONE
    }
}

/**
 * The heart rate for one chart sample: the average of every reading since the last sample, so a
 * reading that comes and goes (hand grips read only while held) is kept, not lost between
 * samples (seen 2026-11-24: 15 s of readings became one chart point).
 */
class HeartRateWindow {
    private var sum = 0L
    private var count = 0

    @Synchronized
    fun add(bpm: Int?) {
        if (bpm == null || bpm <= 0) return
        sum += bpm
        count++
    }

    /** The average since the last take, or null with no reading; starts a new window. */
    @Synchronized
    fun take(): Int? {
        val result = if (count == 0) null else Math.round(sum.toDouble() / count).toInt()
        sum = 0
        count = 0
        return result
    }
}

/** Per-workout heart-rate record (spec 002, H6): one sample per second at most. */
class HeartRateRecord {
    private val samples = mutableListOf<Int>()
    private var lastSecond = -1L

    /** Add a reading taken at [elapsedMillis] into the workout. */
    @Synchronized
    fun add(elapsedMillis: Long, bpm: Int) {
        val second = elapsedMillis / 1000
        if (second == lastSecond) return
        lastSecond = second
        samples += bpm
    }

    @Synchronized fun clear() { samples.clear(); lastSecond = -1 }

    val average: Int? @Synchronized get() = if (samples.isEmpty()) null else samples.average().toInt()
    val max: Int? @Synchronized get() = samples.maxOrNull()
    val count: Int @Synchronized get() = samples.size
}
