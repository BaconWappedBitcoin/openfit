package com.ofit.protocol

/**
 * The FitPro command frame.
 *
 * ```text
 * [device][len][cmd][content...][checksum]
 * ```
 *  - `device`   equipment family, `0x04` for a treadmill
 *  - `len`      total frame length, header and checksum included
 *  - `cmd`      command id
 *  - `checksum` low byte of the sum of every preceding byte
 *
 * Evidence: research.md section 4, corroborated by the MIT-licensed decoder in
 * `DavisGoglin/treadmill` operating on the vendor application's own byte log.
 */
object Command {

    /** Minimum frame size: device, length, command, checksum. */
    const val MIN_FRAME_SIZE: Int = 4

    /**
     * Build a command frame.
     */
    fun encode(device: Int, command: Int, content: ByteArray): ByteArray {
        val total = 3 + content.size + 1
        val frame = ByteArray(total)
        frame[0] = device.toByte()
        frame[1] = total.toByte()
        frame[2] = command.toByte()
        content.copyInto(frame, 3)
        frame[total - 1] = checksum(frame, total - 1).toByte()
        return frame
    }

    /**
     * The checksum of the first [count] bytes: the low byte of their sum.
     */
    fun checksum(bytes: ByteArray, count: Int = bytes.size): Int {
        var sum = 0
        for (i in 0 until count) {
            sum += bytes[i].toInt() and 0xFF
        }
        return sum and 0xFF
    }

    /**
     * Parse a command frame.
     *
     * @throws IllegalArgumentException when the frame is too short or its length byte disagrees
     *   with its size.
     */
    fun decode(frame: ByteArray): CommandFrame {
        require(frame.size >= MIN_FRAME_SIZE) { "frame is too short: ${frame.size} bytes" }

        val length = frame[1].toInt() and 0xFF
        require(length == frame.size) {
            "length byte $length does not match the frame size ${frame.size}"
        }

        val content = frame.copyOfRange(3, frame.size - 1)
        val stored = frame[frame.size - 1].toInt() and 0xFF
        val computed = checksum(frame, frame.size - 1)

        return CommandFrame(
            device = frame[0].toInt() and 0xFF,
            length = length,
            command = frame[2].toInt() and 0xFF,
            content = content,
            checksum = stored,
            checksumValid = stored == computed,
        )
    }

    /** A human readable description of a frame, for logs and the CLI. */
    fun describe(frame: ByteArray): String {
        val decoded = runCatching { decode(frame) }.getOrNull()
            ?: return "malformed frame ${Framing.toHex(frame)}"
        val name = CommandId.nameOf(decoded.command)
        val status = if (decoded.checksumValid) "ok" else "bad-checksum"
        return "$name device=${decoded.device} $status content=${Framing.toHex(decoded.content)}"
    }
}

/** A parsed command frame. */
data class CommandFrame(
    val device: Int,
    val length: Int,
    val command: Int,
    val content: ByteArray,
    val checksum: Int,
    val checksumValid: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommandFrame) return false
        return device == other.device &&
            length == other.length &&
            command == other.command &&
            checksum == other.checksum &&
            checksumValid == other.checksumValid &&
            content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = device
        result = 31 * result + length
        result = 31 * result + command
        result = 31 * result + content.contentHashCode()
        result = 31 * result + checksum
        result = 31 * result + checksumValid.hashCode()
        return result
    }

    override fun toString(): String =
        "CommandFrame(device=$device, length=$length, command=${CommandId.nameOf(command)}, " +
            "content=${Framing.toHex(content)}, checksumValid=$checksumValid)"
}
