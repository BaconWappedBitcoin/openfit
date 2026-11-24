package com.ofit.protocol

/**
 * A small JSON reader for the subset this project uses.
 *
 * Objects, arrays, strings, numbers, and the three literals are supported. Anything else is an
 * error, which is what keeps a malformed file from producing a half-built value.
 *
 * There is no third party JSON library in the protocol module on purpose: the module has to stay
 * a plain JVM library with no dependencies, so that the wire format can be inspected and tested
 * from a terminal. Constitution Article VII and Article VIII.
 */
internal object Json {

    /** Read one document, and refuse anything after it. */
    fun parse(text: String): Any? = JsonReader(text).parseDocument()

    /** Write a JSON string, escaping what has to be escaped. */
    fun quote(value: String): String {
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
                    text.append(String.format(java.util.Locale.US, "\\u%04x", character.code))
                } else {
                    text.append(character)
                }
            }
        }
        text.append('"')
        return text.toString()
    }

    /** Write a number that reads back as the same value. */
    fun number(value: Double): String {
        val text = value.toString()
        return if (text.contains('E') || text.contains('e')) {
            String.format(java.util.Locale.US, "%f", value)
        } else {
            text
        }
    }
}

/** Raised when a JSON document cannot be read. */
internal class JsonException(message: String) : Exception(message)

/**
 * A recursive descent reader for the supported JSON subset.
 */
internal class JsonReader(private val text: String) {

    private var position = 0

    /** Read one document, and refuse anything after it. */
    fun parseDocument(): Any? {
        val value = parseValue()
        skipWhitespace()
        if (position != text.length) {
            fail("there is more text after the value")
        }
        return value
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        if (position >= text.length) fail("the text ends unexpectedly")
        return when (val character = text[position]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            else -> {
                if (character == '-' || character.isDigit()) {
                    parseNumber()
                } else {
                    fail("unexpected character '$character'")
                }
            }
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val fields = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            position++
            return fields
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("a field name must be a string")
            val name = parseString()
            skipWhitespace()
            expect(':')
            fields[name] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                '}' -> {
                    position++
                    return fields
                }
                else -> fail("expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val items = mutableListOf<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            position++
            return items
        }
        while (true) {
            items += parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                ']' -> {
                    position++
                    return items
                }
                else -> fail("expected ',' or ']'")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (true) {
            if (position >= text.length) fail("a string is not closed")
            when (val character = text[position]) {
                '"' -> {
                    position++
                    return result.toString()
                }
                '\\' -> {
                    position++
                    result.append(parseEscape())
                }
                else -> {
                    result.append(character)
                    position++
                }
            }
        }
    }

    private fun parseEscape(): Char {
        if (position >= text.length) fail("an escape is not finished")
        val character = text[position]
        position++
        return when (character) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (position + 4 > text.length) fail("a unicode escape is not finished")
                val code = text.substring(position, position + 4).toIntOrNull(16)
                    ?: fail("a unicode escape is not a number")
                position += 4
                code.toChar()
            }
            else -> fail("unknown escape '\\$character'")
        }
    }

    private fun parseNumber(): Double {
        val start = position
        if (peek() == '-') position++
        while (position < text.length && text[position].isDigit()) position++
        if (position < text.length && text[position] == '.') {
            position++
            while (position < text.length && text[position].isDigit()) position++
        }
        if (position < text.length && (text[position] == 'e' || text[position] == 'E')) {
            position++
            if (position < text.length && (text[position] == '+' || text[position] == '-')) position++
            while (position < text.length && text[position].isDigit()) position++
        }
        val token = text.substring(start, position)
        return token.toDoubleOrNull() ?: fail("'$token' is not a number")
    }

    private fun <T> parseLiteral(literal: String, value: T): T {
        if (!text.startsWith(literal, position)) fail("'$literal' was expected")
        position += literal.length
        return value
    }

    private fun peek(): Char? = if (position < text.length) text[position] else null

    private fun expect(character: Char) {
        if (position >= text.length || text[position] != character) {
            fail("'$character' was expected")
        }
        position++
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position].isWhitespace()) position++
    }

    private fun fail(reason: String): Nothing = throw JsonException(reason)
}
