package com.ofit.protocol

import java.util.Locale

/**
 * Reads and writes workout programs as JSON.
 *
 * The format is a small, hand written JSON subset so that the protocol library stays free of
 * third party dependencies and every byte of the format is covered by tests. Programs are
 * ordinary text files that a person can read, edit, or write by hand.
 *
 * The shape is:
 * ```
 * {
 *   "name": "Warm up",
 *   "steps": [
 *     { "speedKph": 3.0, "inclinePercent": 0.0, "durationSeconds": 120 }
 *   ]
 * }
 * ```
 */
object WorkoutCodec {

    /** Write a program as JSON. */
    fun encode(program: WorkoutProgram): String {
        val text = StringBuilder()
        text.append("{\n")
        text.append("  \"name\": ").append(quote(program.name)).append(",\n")
        if (program.steps.isEmpty()) {
            text.append("  \"steps\": []\n")
        } else {
            text.append("  \"steps\": [\n")
            program.steps.forEachIndexed { index, step ->
                text.append("    {")
                text.append("\"speedKph\": ").append(number(step.speedKph))
                text.append(", \"inclinePercent\": ").append(number(step.inclinePercent))
                text.append(", \"durationSeconds\": ").append(step.durationSeconds)
                text.append("}")
                if (index != program.steps.lastIndex) text.append(",")
                text.append("\n")
            }
            text.append("  ]\n")
        }
        text.append("}\n")
        return text.toString()
    }

    /** Read a program from JSON. Throws [WorkoutCodecException] when the text is not a program. */
    fun decode(text: String): WorkoutProgram {
        val root = try {
            Json.parse(text)
        } catch (error: WorkoutCodecException) {
            throw error
        } catch (error: Exception) {
            throw WorkoutCodecException("The program file is not readable: ${error.message}")
        }

        val fields = root as? Map<*, *> ?: throw WorkoutCodecException("A program must be a JSON object.")

        val name = fields["name"] as? String
            ?: throw WorkoutCodecException("The program has no name.")

        val rawSteps = fields["steps"] as? List<*>
            ?: throw WorkoutCodecException("The program has no list of steps.")

        val steps = rawSteps.mapIndexed { index, raw ->
            val step = raw as? Map<*, *>
                ?: throw WorkoutCodecException("Step ${index + 1} is not an object.")
            val speed = step.number("speedKph", index)
            val incline = step.number("inclinePercent", index)
            val duration = step.number("durationSeconds", index)
            if (duration % 1.0 != 0.0) {
                throw WorkoutCodecException(
                    "Step ${index + 1} has a duration that is not a whole number of seconds.",
                )
            }
            if (duration < 0.0 || duration > Int.MAX_VALUE.toDouble()) {
                throw WorkoutCodecException("Step ${index + 1} has a duration that is out of range.")
            }
            WorkoutStep(speedKph = speed, inclinePercent = incline, durationSeconds = duration.toInt())
        }

        return WorkoutProgram(name = name, steps = steps)
    }

    /** Read a number out of a step, or explain why it cannot. */
    private fun Map<*, *>.number(key: String, index: Int): Double {
        val value = this[key]
        if (value == null) {
            throw WorkoutCodecException("Step ${index + 1} is missing \"$key\".")
        }
        if (value !is Double) {
            throw WorkoutCodecException("Step ${index + 1} has a \"$key\" that is not a number.")
        }
        return value
    }

    /** Write a JSON string, escaping what has to be escaped. */
    private fun quote(value: String): String {
        val text = StringBuilder()
        text.append('"')
        for (character in value) {
            when (character) {
                '"' -> text.append("\\\"")
                '\\' -> text.append("\\\\")
                '\n' -> text.append("\\n")
                '\r' -> text.append("\\r")
                '\t' -> text.append("\\t")
                '\b' -> text.append("\\b")
                '\u000C' -> text.append("\\f")
                else -> if (character < ' ') {
                    text.append(String.format(Locale.US, "\\u%04x", character.code))
                } else {
                    text.append(character)
                }
            }
        }
        text.append('"')
        return text.toString()
    }

    /** Write a number that reads back as the same value. */
    private fun number(value: Double): String {
        val text = value.toString()
        return if (text.contains('E') || text.contains('e')) {
            String.format(Locale.US, "%f", value)
        } else {
            text
        }
    }
}

/** Raised when a program file cannot be read. */
class WorkoutCodecException(message: String) : Exception(message)
