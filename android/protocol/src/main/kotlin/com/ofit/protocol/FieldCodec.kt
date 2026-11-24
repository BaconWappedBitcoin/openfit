package com.ofit.protocol

/**
 * Field addressing and value encoding.
 *
 * A request addresses fields by a bitmap of their numeric ids. The values follow in ascending id
 * order, each sized by its converter. All multi-byte integers are little-endian.
 *
 * ```text
 * write section: <n> <n mask bytes> <value> <value> ...   values in ascending id order
 * read section : <n> <n mask bytes>
 * ```
 *
 * Evidence: research.md section 7.
 */
object FieldCodec {

    /** Fields whose value is a physical quantity scaled by one hundred. */
    private val SCALED_BY_100 = setOf(
        FieldId.KPH,
        FieldId.GRADE,
        FieldId.ACTUAL_KPH,
        FieldId.ACTUAL_INCLINE,
        FieldId.MAX_GRADE,
        FieldId.MIN_GRADE,
        FieldId.AVERAGE_GRADE,
    )

    /**
     * Build the bitmap that addresses a set of field ids.
     *
     * Bit `i` of byte `n` addresses field id `n * 8 + i`.
     */
    fun encodeBitmap(ids: List<Int>): ByteArray {
        if (ids.isEmpty()) return ByteArray(0)
        val byteCount = (ids.max() / 8) + 1
        val bitmap = ByteArray(byteCount)
        for (id in ids) {
            require(id >= 0) { "field id must not be negative: $id" }
            val index = id / 8
            bitmap[index] = (bitmap[index].toInt() or (1 shl (id % 8))).toByte()
        }
        return bitmap
    }

    /**
     * Recover the field ids addressed by a bitmap, in ascending order.
     */
    fun decodeBitmap(bitmap: ByteArray): List<Int> {
        val ids = mutableListOf<Int>()
        for (index in bitmap.indices) {
            val byte = bitmap[index].toInt() and 0xFF
            for (bit in 0 until 8) {
                if (byte and (1 shl bit) != 0) {
                    ids += index * 8 + bit
                }
            }
        }
        return ids
    }

    /**
     * Encode a single value using its own type.
     *
     * Prefer [encodeForField] when the field is known: it applies the field's size and sign.
     */
    fun encodeValue(value: FieldValue): ByteArray = when (value) {
        is FieldValue.Speed -> bytes16(Math.round(value.kmh * 100.0).toInt())
        is FieldValue.Incline -> bytes16(Math.round(value.percent * 100.0).toInt())
        is FieldValue.Pulse -> byteArrayOf(
            value.currentBpm.toByte(),
            value.averageBpm.toByte(),
            value.count.toByte(),
            value.source.code.toByte(),
        )
        is FieldValue.WorkoutMode -> byteArrayOf(value.mode.code.toByte())
        is FieldValue.UInt -> bytes32(value.value)
        is FieldValue.SInt -> bytes16(value.value)
        is FieldValue.Flag -> byteArrayOf(if (value.value) 1 else 0)
    }

    /**
     * Encode a value for a known field, honouring that field's size, sign, and scale.
     */
    fun encodeForField(field: FieldId, value: FieldValue): ByteArray {
        val scaled = value is FieldValue.Speed || value is FieldValue.Incline
        return when {
            field.size == 1 -> when (value) {
                is FieldValue.WorkoutMode -> byteArrayOf(value.mode.code.toByte())
                is FieldValue.Flag -> byteArrayOf(if (value.value) 1 else 0)
                is FieldValue.UInt -> byteArrayOf(value.value.toByte())
                else -> encodeValue(value).copyOf(field.size)
            }

            field.size == 2 -> {
                val raw = when (value) {
                    is FieldValue.Speed -> Math.round(value.kmh * 100.0).toInt()
                    is FieldValue.Incline -> Math.round(value.percent * 100.0).toInt()
                    is FieldValue.UInt -> value.value.toInt()
                    is FieldValue.SInt -> value.value
                    else -> throw IllegalArgumentException("cannot encode $value as $field")
                }
                if (field.signed) bytes16(s16(raw)) else bytes16(u16(raw))
            }

            field.size == 4 -> when (value) {
                is FieldValue.Pulse -> encodeValue(value)
                is FieldValue.UInt -> bytes32(value.value)
                else -> throw IllegalArgumentException("cannot encode $value as $field")
            }

            else -> throw IllegalArgumentException("no converter for $field")
        }.also { encoded ->
            require(encoded.size == field.size) {
                "encoded $field into ${encoded.size} bytes, expected ${field.size}"
            }
            require(!scaled || field.size == 2) { "scaled field $field has an unexpected size" }
        }
    }

    /**
     * Decode a value for a known field.
     *
     * Returns null when the buffer is shorter than the field, so that an absent value is never
     * confused with a zero.
     */
    fun decodeValue(field: FieldId, raw: ByteArray): FieldValue? {
        if (raw.size < field.size) return null
        val bytes = raw.copyOf(field.size)

        return when (field) {
            FieldId.KPH, FieldId.ACTUAL_KPH -> FieldValue.Speed(readU16(bytes) / 100.0)
            FieldId.GRADE, FieldId.ACTUAL_INCLINE, FieldId.MAX_GRADE,
            FieldId.MIN_GRADE, FieldId.AVERAGE_GRADE,
            -> FieldValue.Incline(readS16(bytes) / 100.0)

            FieldId.WORKOUT_MODE -> FieldValue.WorkoutMode(WorkoutMode.of(bytes[0].toInt() and 0xFF))

            FieldId.PULSE -> FieldValue.Pulse(
                currentBpm = bytes[0].toInt() and 0xFF,
                averageBpm = bytes[1].toInt() and 0xFF,
                count = bytes[2].toInt() and 0xFF,
                source = PulseSource.of(bytes[3].toInt() and 0xFF),
            )

            else -> if (field.signed) {
                FieldValue.SInt(readS16(bytes))
            } else {
                FieldValue.UInt(readU32(bytes))
            }
        }
    }

    /**
     * Build the write section: the mask, then the values in ascending field id order.
     */
    fun encodeWriteSection(values: List<Pair<FieldId, FieldValue>>): ByteArray {
        if (values.isEmpty()) return byteArrayOf(0)

        val ordered = values.sortedBy { it.first.id }
        val bitmap = encodeBitmap(ordered.map { it.first.id })
        val encodedValues = ordered.map { (field, value) -> encodeForField(field, value) }

        return byteArrayOf(bitmap.size.toByte()) + bitmap + encodedValues.reduce { a, b -> a + b }
    }

    /**
     * Build the read section: the mask only.
     */
    fun encodeReadSection(ids: List<Int>): ByteArray {
        val bitmap = encodeBitmap(ids)
        return byteArrayOf(bitmap.size.toByte()) + bitmap
    }

    /**
     * Decode a write section into its field values.
     */
    fun decodeWriteSection(content: ByteArray): Map<FieldId, FieldValue> {
        if (content.isEmpty()) return emptyMap()
        val maskCount = content[0].toInt() and 0xFF
        val ids = decodeBitmap(content.copyOfRange(1, 1 + maskCount))
        var offset = 1 + maskCount
        val values = mutableMapOf<FieldId, FieldValue>()
        for (id in ids) {
            val field = FieldId.of(id) ?: continue
            val raw = content.copyOfRange(offset, minOf(offset + field.size, content.size))
            offset += field.size
            decodeValue(field, raw)?.let { values[field] = it }
        }
        return values
    }

    /**
     * Decode a read section into the field ids it addresses.
     */
    fun decodeReadSection(content: ByteArray, offset: Int = 0): Pair<List<Int>, Int> {
        if (offset >= content.size) return emptyList<Int>() to offset
        val maskCount = content[offset].toInt() and 0xFF
        val end = minOf(offset + 1 + maskCount, content.size)
        val ids = decodeBitmap(content.copyOfRange(offset + 1, end))
        return ids to end
    }

    private fun u16(value: Int): Int = value and 0xFFFF

    private fun s16(value: Int): Int = value.toShort().toInt()

    private fun u32(value: Long): Long = value and 0xFFFFFFFFL

    private fun readU16(bytes: ByteArray): Int =
        (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)

    private fun readS16(bytes: ByteArray): Int = readU16(bytes).toShort().toInt()

    /** Little-endian unsigned value of a 1, 2 or 4 byte field. */
    private fun readU32(bytes: ByteArray): Long {
        var value = 0L
        for (i in 0 until minOf(bytes.size, 4)) {
            value = value or ((bytes[i].toLong() and 0xFF) shl (8 * i))
        }
        return value
    }

    private fun bytes16(value: Int): ByteArray {
        val v = u16(value)
        return byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    }

    private fun bytes32(value: Long): ByteArray {
        val v = u32(value)
        return byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )
    }
}
