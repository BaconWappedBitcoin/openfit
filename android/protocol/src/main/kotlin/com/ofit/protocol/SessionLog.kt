package com.ofit.protocol

/**
 * A record of every frame sent to and received from the machine.
 *
 * This is what makes a control problem diagnosable after the fact, without needing the vendor
 * application. Specification requirement FR-018, success criterion SC-010.
 */
class SessionLog(private val maxEntries: Int = 5000) {

    private val entries = ArrayDeque<SessionLogEntry>()

    /** Every entry, oldest first. */
    @Synchronized
    fun all(): List<SessionLogEntry> = entries.toList()

    /** Record an outbound frame. */
    @Synchronized
    fun outbound(bytes: ByteArray, command: Int? = null, note: String? = null) {
        append(
            SessionLogEntry(
                timestampMillis = System.currentTimeMillis(),
                direction = Direction.OUTBOUND,
                bytes = bytes.copyOf(),
                decodedCommand = command,
                note = note,
            ),
        )
    }

    /** Record an inbound frame. */
    @Synchronized
    fun inbound(bytes: ByteArray, command: Int? = null, note: String? = null) {
        append(
            SessionLogEntry(
                timestampMillis = System.currentTimeMillis(),
                direction = Direction.INBOUND,
                bytes = bytes.copyOf(),
                decodedCommand = command,
                note = note,
            ),
        )
    }

    /** Record a decoded field payload alongside the most recent inbound entry. */
    @Synchronized
    fun attachFields(fields: Map<FieldId, FieldValue>) {
        val last = entries.lastOrNull() ?: return
        entries[entries.lastIndex] = last.copy(decodedFields = fields)
    }

    /** Also called with every event note, for a platform log. */
    @Volatile var onEvent: ((String) -> Unit)? = null

    /** Record a note against the session as a whole. */
    @Synchronized
    fun event(note: String) {
        onEvent?.invoke(note)
        append(
            SessionLogEntry(
                timestampMillis = System.currentTimeMillis(),
                direction = Direction.EVENT,
                bytes = ByteArray(0),
                note = note,
            ),
        )
    }

    private fun append(entry: SessionLogEntry) {
        entries.addLast(entry)
        while (entries.size > maxEntries) entries.removeFirst()
    }

    /** Render the log as text, one entry per line. */
    fun render(): String = all().joinToString("\n") { it.render() }
}

/** Which way a log entry travelled. */
enum class Direction {
    OUTBOUND,
    INBOUND,
    EVENT,
}

/** One recorded frame or event. */
data class SessionLogEntry(
    val timestampMillis: Long,
    val direction: Direction,
    val bytes: ByteArray,
    val decodedCommand: Int? = null,
    val decodedFields: Map<FieldId, FieldValue> = emptyMap(),
    val note: String? = null,
) {
    /** Render this entry as a single line. */
    fun render(): String {
        val head = when (direction) {
            Direction.OUTBOUND -> "->"
            Direction.INBOUND -> "<-"
            Direction.EVENT -> "**"
        }
        val command = decodedCommand?.let { " ${CommandId.nameOf(it)}" } ?: ""
        val hex = if (bytes.isEmpty()) "" else " " + Framing.toHex(bytes)
        val fields = if (decodedFields.isEmpty()) {
            ""
        } else {
            " {" + decodedFields.entries.joinToString(", ") { "${it.key.name}=${it.value}" } + "}"
        }
        val text = note?.let { " $it" } ?: ""
        return "$head$command$hex$fields$text"
    }
}
