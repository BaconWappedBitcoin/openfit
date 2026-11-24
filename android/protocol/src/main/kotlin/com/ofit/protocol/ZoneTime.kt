package com.ofit.protocol

/**
 * Time spent in each heart-rate zone during a workout, by the runner's own zones (from their max
 * heart rate or age, see HeartRateZones). Index 0 is below zone 1; 1..5 are the zones.
 */
class ZoneTimer {
    private val millis = LongArray(6)

    /** Count [elapsedMillis] at [bpm]. Nothing is counted without zones or a reading. */
    fun add(elapsedMillis: Long, bpm: Int?, zones: HeartRateZones?) {
        if (elapsedMillis <= 0 || bpm == null || zones == null) return
        millis[zones.zoneOf(bpm).coerceIn(0, 5)] += elapsedMillis
    }

    /** Whole seconds per zone, index 0 (below zone 1) to 5; null if nothing was counted. */
    fun seconds(): List<Long>? = if (millis.all { it == 0L }) null else millis.map { it / 1000 }

    fun reset() = millis.fill(0)
}
