package com.ofit.protocol

/**
 * How the machine gets unlocked for control.
 *
 * A connected machine accepts reads straight away. It only accepts writes after an unlock, and
 * only obeys them after it is armed.
 *
 * Two routes are supported:
 *  - a captured unlock payload, which is a fixed number of bytes that the vendor application sent
 *    successfully for this machine;
 *  - a computed unlock, whose hash construction is not fully published and is therefore disabled
 *    by default.
 *
 * Evidence: research.md section 8 and risk R1.
 */
interface UnlockProvider {

    /** Whether this provider can currently produce an unlock payload. */
    val isAvailable: Boolean

    /** A description of what this provider is doing, for the user interface. */
    val description: String

    /**
     * Produce the unlock payload for a machine.
     *
     * @param deviceInfo values the machine reported about itself
     * @return the unlock content, or null when no unlock could be produced
     */
    fun createUnlockPayload(deviceInfo: DeviceInfo): ByteArray?
}

/**
 * The values a machine reported about itself during discovery.
 *
 * These are the inputs to the unlock computation and are also what tells the user which treadmill
 * they are connected to.
 */
data class DeviceInfo(
    val serialNumber: Long? = null,
    val partNumber: Long? = null,
    val model: Long? = null,
    val masterLibraryVersion: Int? = null,
    val firmwareVersion: String? = null,
    val supportedFieldIds: List<Int> = emptyList(),
    val supportedCommandIds: List<Int> = emptyList(),
)

/**
 * An unlock payload captured from the vendor application.
 *
 * The payload is a fixed 36 bytes: a 32 byte security hash followed by a 4 byte secret. The
 * exact split is recorded in research.md section 8.
 */
class CapturedUnlock(payload: ByteArray) : UnlockProvider {

    /** The captured bytes. */
    val payload: ByteArray = payload.copyOf()

    override val isAvailable: Boolean = payload.isNotEmpty()

    override val description: String = "captured unlock (${payload.size} bytes)"

    override fun createUnlockPayload(deviceInfo: DeviceInfo): ByteArray? = payload.copyOf()

    companion object {
        /** The length of an unlock payload. */
        const val PAYLOAD_LENGTH: Int = 36

        /**
         * Parse a captured unlock from hex, ignoring separators and whitespace.
         *
         * @throws IllegalArgumentException when the value is not the expected length
         */
        fun fromHex(hex: String): CapturedUnlock {
            val cleaned = hex.filterNot { it.isWhitespace() || it == '-' || it == ':' }
            require(cleaned.length % 2 == 0) { "an unlock needs an even number of hex digits" }
            val bytes = ByteArray(cleaned.length / 2) {
                val high = cleaned[it * 2].digitToIntOrNull(16)
                val low = cleaned[it * 2 + 1].digitToIntOrNull(16)
                require(high != null && low != null) { "the unlock is not valid hex" }
                ((high shl 4) or low).toByte()
            }
            require(bytes.size == PAYLOAD_LENGTH) {
                "an unlock is $PAYLOAD_LENGTH bytes, got ${bytes.size}"
            }
            return CapturedUnlock(bytes)
        }
    }
}

/**
 * The published unlock computation.
 *
 * The payload structure is known: a 32 byte security hash computed from the machine's serial
 * number, part number, and model, followed by a 4 byte secret derived from the master library
 * version. The hash construction itself is not published in the sources consulted, so this
 * provider refuses to produce a payload unless a hash function is supplied.
 *
 * The flag `FeatureFlags.unlockComputed` is off by default for that reason.
 */
class ComputedUnlock(
    private val hashFunction: ((DeviceInfo) -> ByteArray?)? = null,
) : UnlockProvider {

    override val isAvailable: Boolean get() = hashFunction != null

    override val description: String
        get() = if (isAvailable) {
            "computed unlock"
        } else {
            "computed unlock unavailable (the security hash construction is not known)"
        }

    override fun createUnlockPayload(deviceInfo: DeviceInfo): ByteArray? {
        val hash = hashFunction ?: return null
        val digest = hash(deviceInfo) ?: return null
        require(digest.size == 32) { "the security hash must be 32 bytes" }

        val version = deviceInfo.masterLibraryVersion ?: return null
        val secret = byteArrayOf(
            (version * 8).toByte(),
            ((version * 8) shr 8).toByte(),
            ((version * 8) shr 16).toByte(),
            ((version * 8) shr 24).toByte(),
        )
        return digest + secret
    }
}
