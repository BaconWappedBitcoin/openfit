package com.ofit.protocol

/**
 * Computes the VerifySecurity (0x90) unlock from the console's own handshake answers, so no captured
 * code is needed.
 *
 *  - 32 bytes are built from a bit mask and two numbers, then a 4-byte secret (`version * 8`,
 *    little-endian) is appended to make 36 bytes;
 *  - DeviceInfo (0x81) gives the bit mask: frame bytes 6..9, summed from SIGNED bytes;
 *  - SystemInfo (0x82) gives two numbers: frame bytes 7..10 and 11..14, little-endian, with one
 *    special case (370357 on model 39915 becomes 374677);
 *  - VersionInfo (0x84) gives the version: frame byte 4.
 *
 * Every index below is into the whole command frame `[device][len][cmd][status][...]`. Checked byte
 * for byte against the unlock seen in a Bluetooth capture from the user's NordicTrack T 6.5
 * (see UnlockCalculatorTest).
 */
object UnlockCalculator {

    /**
     * The 36-byte unlock payload, or null when a frame is too short to hold what is needed.
     *
     * @param deviceInfo the console's reply to DeviceInfo (0x81), as a whole frame
     * @param systemInfo the console's reply to SystemInfo (0x82), as a whole frame
     * @param versionInfo the console's reply to VersionInfo (0x84), as a whole frame
     */
    fun compute(deviceInfo: ByteArray, systemInfo: ByteArray, versionInfo: ByteArray): ByteArray? {
        if (deviceInfo.size < 10 || systemInfo.size < 15 || versionInfo.size < 5) return null

        // Signed bytes, added: the only reading that reproduces the captured unlock.
        val mask = deviceInfo[6].toInt() + (deviceInfo[7].toInt() shl 8) +
            (deviceInfo[8].toInt() shl 16) + (deviceInfo[9].toInt() shl 24)
        val factor = littleEndian(systemInfo, 7)
        val raw = littleEndian(systemInfo, 11)
        val key = if (raw == 370357 && factor == 39915) 374677 else raw
        val version = versionInfo[4].toInt()

        val out = ByteArray(36)
        for (i in 0 until 32) {
            val b = (i + 1).toByte().toInt()
            val mixed = when {
                ((mask shr i) and 1) != 1 -> ((i + factor) * b).toByte().toInt()
                i < 16 -> (((key shl 16) or (key shr 16)) shr i).toByte().toInt()
                else -> (key shr i).toByte().toInt()
            }
            out[i] = (b xor mixed).toByte()
        }
        val secret = version * 8
        out[32] = secret.toByte()
        out[33] = (secret shr 8).toByte()
        out[34] = (secret shr 16).toByte()
        out[35] = (secret shr 24).toByte()
        return out
    }

    private fun littleEndian(bytes: ByteArray, from: Int): Int =
        (bytes[from].toInt() and 0xFF) or
            ((bytes[from + 1].toInt() and 0xFF) shl 8) or
            ((bytes[from + 2].toInt() and 0xFF) shl 16) or
            ((bytes[from + 3].toInt() and 0xFF) shl 24)
}
