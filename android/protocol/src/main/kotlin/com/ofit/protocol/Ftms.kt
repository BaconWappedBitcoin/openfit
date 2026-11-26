package com.ofit.protocol

/**
 * The standard Bluetooth Fitness Machine Service (FTMS, 0x1826): what almost every smart indoor bike
 * (and many treadmills) speak, including a NordicTrack S15i paired the way Zwift pairs it. This is
 * the bike foundation (2026-11-25, a community user asked for the S15i and bikes in general): the
 * wire formats only, no radio. The app's Bluetooth layer reads the characteristics and passes the
 * bytes here, and writes the bytes this builds. Layouts follow the Bluetooth SIG's FTMS 1.0
 * specification; every value below has a test with bytes worked out by hand.
 *
 * Nothing here sends anything: commands are only built, and the same safety limits as for a
 * treadmill (SafetyLimits) must be checked before one is written.
 */
object Ftms {
    // --- UUIDs (16-bit assigned numbers in the Bluetooth base UUID) ---
    private fun uuid(short: Int) = "0000%04x-0000-1000-8000-00805f9b34fb".format(short)
    val SERVICE = uuid(0x1826)
    val FEATURE = uuid(0x2ACC)
    val INDOOR_BIKE_DATA = uuid(0x2AD2)
    val TREADMILL_DATA = uuid(0x2ACD)
    val TRAINING_STATUS = uuid(0x2AD3)
    val SUPPORTED_INCLINATION_RANGE = uuid(0x2AD5)
    val SUPPORTED_RESISTANCE_RANGE = uuid(0x2AD6)
    val SUPPORTED_POWER_RANGE = uuid(0x2AD8)
    val CONTROL_POINT = uuid(0x2AD9)
    val MACHINE_STATUS = uuid(0x2ADA)

    // --- Indoor Bike Data (0x2AD2) ---

    /** A reading from a bike. Absent fields are null (the bike did not send them). */
    data class BikeData(
        val speedKph: Double? = null,
        val averageSpeedKph: Double? = null,
        val cadenceRpm: Double? = null,
        val averageCadenceRpm: Double? = null,
        val distanceMeters: Int? = null,
        val resistanceLevel: Int? = null,
        val powerWatts: Int? = null,
        val averagePowerWatts: Int? = null,
        val totalKcal: Int? = null,
        val heartRateBpm: Int? = null,
        val elapsedSeconds: Int? = null,
        val remainingSeconds: Int? = null,
    )

    /** Parse an Indoor Bike Data notification; null if it is too short for what its flags promise. */
    fun parseBikeData(bytes: ByteArray): BikeData? {
        if (bytes.size < 2) return null
        val r = Reader(bytes)
        val flags = r.u16() ?: return null
        fun has(bit: Int) = flags and (1 shl bit) != 0
        // Bit 0 is inverted: instantaneous speed is present when it is 0 ("More Data" is clear).
        val speed = if (!has(0)) (r.u16() ?: return null) * 0.01 else null
        val avgSpeed = if (has(1)) (r.u16() ?: return null) * 0.01 else null
        val cadence = if (has(2)) (r.u16() ?: return null) * 0.5 else null
        val avgCadence = if (has(3)) (r.u16() ?: return null) * 0.5 else null
        val distance = if (has(4)) r.u24() ?: return null else null
        val resistance = if (has(5)) r.s16() ?: return null else null
        val power = if (has(6)) r.s16() ?: return null else null
        val avgPower = if (has(7)) r.s16() ?: return null else null
        var kcal: Int? = null
        if (has(8)) {
            kcal = r.u16() ?: return null
            r.u16() ?: return null // per hour
            r.u8() ?: return null  // per minute
        }
        val heart = if (has(9)) r.u8() ?: return null else null
        if (has(10)) r.u8() ?: return null // metabolic equivalent: not used
        val elapsed = if (has(11)) r.u16() ?: return null else null
        val remaining = if (has(12)) r.u16() ?: return null else null
        return BikeData(speed, avgSpeed, cadence, avgCadence, distance, resistance, power, avgPower, kcal, heart, elapsed, remaining)
    }

    // --- Feature (0x2ACC) and the supported ranges ---

    /** What the machine can report and what it lets a client set. */
    data class Features(val machine: Long, val targets: Long) {
        val cadence get() = machine and (1L shl 1) != 0L
        val power get() = machine and (1L shl 14) != 0L
        val heartRate get() = machine and (1L shl 10) != 0L
        val inclinationTarget get() = targets and (1L shl 1) != 0L
        val resistanceTarget get() = targets and (1L shl 2) != 0L
        val powerTarget get() = targets and (1L shl 3) != 0L
        val speedTarget get() = targets and 1L != 0L
        val simulation get() = targets and (1L shl 13) != 0L
    }

    fun parseFeatures(bytes: ByteArray): Features? {
        if (bytes.size < 8) return null
        val r = Reader(bytes)
        return Features(r.u32() ?: return null, r.u32() ?: return null)
    }

    /** A supported range: [min]..[max] in [step]s, in the unit the characteristic names (see the helpers). */
    data class Range(val min: Double, val max: Double, val step: Double)

    /** Supported Resistance Level Range (0x2AD6): unitless, 0.1 resolution. */
    fun parseResistanceRange(bytes: ByteArray): Range? = rangeOf(bytes, 0.1)

    /** Supported Power Range (0x2AD8): watts, 1 resolution. */
    fun parsePowerRange(bytes: ByteArray): Range? = rangeOf(bytes, 1.0)

    /** Supported Inclination Range (0x2AD5): percent, 0.1 resolution. */
    fun parseInclinationRange(bytes: ByteArray): Range? = rangeOf(bytes, 0.1)

    private fun rangeOf(bytes: ByteArray, unit: Double): Range? {
        if (bytes.size < 6) return null
        val r = Reader(bytes)
        return Range((r.s16() ?: return null) * unit, (r.s16() ?: return null) * unit, (r.u16() ?: return null) * unit)
    }

    // --- Control Point (0x2AD9): commands to write, and the answer ---

    /** Take control. Must come first; the machine answers with a response code. */
    fun requestControl() = byteArrayOf(0x00)

    fun reset() = byteArrayOf(0x01)

    /** Start or resume a session. */
    fun start() = byteArrayOf(0x07)

    /** Stop (a finished session). */
    fun stop() = byteArrayOf(0x08, 0x01)

    /** Pause (the session can resume). */
    fun pause() = byteArrayOf(0x08, 0x02)

    /** Target speed, km/h (resolution 0.01), for a treadmill or a bike that takes one. */
    fun setTargetSpeed(kph: Double) = byteArrayOf(0x02) + u16le((kph * 100).toInt().coerceIn(0, 0xFFFF))

    /** Target inclination, percent (resolution 0.1, may be negative). */
    fun setTargetInclination(percent: Double) = byteArrayOf(0x03) + s16le(Math.round(percent * 10).toInt())

    /** Target resistance level (resolution 0.1, unitless: the machine's own scale). */
    fun setTargetResistance(level: Double) = byteArrayOf(0x04, (Math.round(level * 10).toInt().coerceIn(0, 255)).toByte())

    /** Target power, watts, for a bike in ERG mode. */
    fun setTargetPower(watts: Int) = byteArrayOf(0x05) + s16le(watts)

    /**
     * Indoor bike simulation: how the bike should feel as the road changes. Wind m/s (0.001), grade
     * percent (0.01), rolling resistance coefficient (0.0001) and wind resistance coefficient kg/m (0.01).
     */
    fun setSimulation(windMps: Double = 0.0, gradePercent: Double, crr: Double = 0.004, cw: Double = 0.51) =
        byteArrayOf(0x11) + s16le(Math.round(windMps * 1000).toInt()) + s16le(Math.round(gradePercent * 100).toInt()) +
            byteArrayOf(Math.round(crr * 10000).toInt().coerceIn(0, 255).toByte(), Math.round(cw * 100).toInt().coerceIn(0, 255).toByte())

    /** The machine's answer to a command: which command, and whether it accepted. */
    data class Response(val command: Int, val result: Result) {
        enum class Result { SUCCESS, NOT_SUPPORTED, INVALID_PARAMETER, FAILED, NOT_PERMITTED, OTHER }
        val accepted get() = result == Result.SUCCESS
    }

    /** Parse a Control Point indication (0x80, the command it answers, a result code). */
    fun parseResponse(bytes: ByteArray): Response? {
        if (bytes.size < 3 || (bytes[0].toInt() and 0xFF) != 0x80) return null
        val result = when (bytes[2].toInt() and 0xFF) {
            0x01 -> Response.Result.SUCCESS
            0x02 -> Response.Result.NOT_SUPPORTED
            0x03 -> Response.Result.INVALID_PARAMETER
            0x04 -> Response.Result.FAILED
            0x05 -> Response.Result.NOT_PERMITTED
            else -> Response.Result.OTHER
        }
        return Response(bytes[1].toInt() and 0xFF, result)
    }

    // --- byte helpers (FTMS is little-endian) ---
    private fun u16le(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun s16le(v: Int) = u16le(v.coerceIn(-32768, 32767) and 0xFFFF)

    private class Reader(private val b: ByteArray) {
        private var i = 0
        fun u8(): Int? = if (i + 1 <= b.size) b[i++].toInt() and 0xFF else null
        fun u16(): Int? = if (i + 2 <= b.size) (b[i++].toInt() and 0xFF) or ((b[i++].toInt() and 0xFF) shl 8) else null
        fun s16(): Int? = u16()?.let { if (it >= 0x8000) it - 0x10000 else it }
        fun u24(): Int? = if (i + 3 <= b.size) (b[i++].toInt() and 0xFF) or ((b[i++].toInt() and 0xFF) shl 8) or ((b[i++].toInt() and 0xFF) shl 16) else null
        fun u32(): Long? = if (i + 4 <= b.size) {
            var v = 0L
            for (k in 0 until 4) v = v or ((b[i++].toLong() and 0xFF) shl (8 * k))
            v
        } else null
    }
}
