package com.ofit.protocol

import java.util.Locale

/**
 * Metric or imperial, for what the user sees.
 *
 * This is a display choice only. Programs, the protocol and the safety ceilings are always
 * stored and checked in km/h and per cent, so changing the unit can never change what is sent to
 * the machine. Incline is a per cent gradient in both systems and is not converted.
 */
enum class UnitSystem(
    val speedUnit: String,
    val distanceUnit: String,
    val paceUnit: String,
    private val kilometresPerUnit: Double,
) {
    METRIC("km/h", "km", "/km", 1.0),
    IMPERIAL("mph", "mi", "/mi", 1.609344),
    ;

    /** A speed in km/h, in this system's speed unit. */
    fun speedFromKph(kph: Double): Double = kph / kilometresPerUnit

    /** A speed in this system's speed unit, in km/h. */
    fun speedToKph(value: Double): Double = value * kilometresPerUnit

    /** A distance in metres, in this system's distance unit. */
    fun distanceFromMeters(meters: Double): Double = meters / 1000.0 / kilometresPerUnit

    /** "8.0 km/h" or "5.0 mph". */
    fun speedText(kph: Double, decimals: Int = 1): String =
        "%.${decimals}f %s".format(Locale.ROOT, speedFromKph(kph), speedUnit)

    /** "5.00 km" or "3.11 mi". */
    fun distanceText(meters: Double): String =
        "%.2f %s".format(Locale.ROOT, distanceFromMeters(meters), distanceUnit)

    /** "5.00" or "3.11": the number alone, for a tile whose label carries the unit. */
    fun distanceValueText(meters: Double): String =
        "%.2f".format(Locale.ROOT, distanceFromMeters(meters))

    /** The distance unit written out, for a tile label: "km" or "miles". */
    val distanceUnitLong: String get() = if (this == IMPERIAL) "miles" else "km"

    /** Pace as m:ss per distance unit, or "--:--" when the runner is not moving. */
    fun paceText(kph: Double?): String {
        if (kph == null || kph < 0.3) return "--:--"
        val totalSeconds = Math.round(3600.0 * kilometresPerUnit / kph).toInt()
        return "%d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
    }

    companion object {
        /** The stored name back to a system, METRIC if it is not one. */
        fun fromName(name: String?): UnitSystem = entries.firstOrNull { it.name == name } ?: METRIC
    }
}
