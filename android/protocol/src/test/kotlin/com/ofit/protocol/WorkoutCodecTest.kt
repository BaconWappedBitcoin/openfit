package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reading and writing workout programs.
 *
 * A program file is text a person can read and edit, so the format has to survive round trips
 * and reject malformed input rather than produce a half-built program.
 */
class WorkoutCodecTest {

    private val program = WorkoutProgram(
        name = "Warm up and intervals",
        steps = listOf(
            WorkoutStep(speedKph = 3.0, inclinePercent = 0.0, durationSeconds = 120),
            WorkoutStep(speedKph = 8.5, inclinePercent = 2.5, durationSeconds = 300),
            WorkoutStep(speedKph = 0.0, inclinePercent = -1.0, durationSeconds = 60),
        ),
    )

    @Test
    fun `a program survives a round trip`() {
        val text = WorkoutCodec.encode(program)

        assertEquals(program, WorkoutCodec.decode(text))
    }

    @Test
    fun `the encoded text is readable json`() {
        val text = WorkoutCodec.encode(program)

        assertTrue(text.contains("\"name\""))
        assertTrue(text.contains("\"steps\""))
        assertTrue(text.contains("\"speedKph\""))
        assertTrue(text.contains("\"inclinePercent\""))
        assertTrue(text.contains("\"durationSeconds\""))
    }

    @Test
    fun `a name with quotes and newlines survives a round trip`() {
        val awkward = program.copy(name = "Hill \"special\"\nsecond line")

        assertEquals(awkward, WorkoutCodec.decode(WorkoutCodec.encode(awkward)))
    }

    @Test
    fun `a program with no steps survives a round trip`() {
        val empty = WorkoutProgram("Empty", emptyList())

        assertEquals(empty, WorkoutCodec.decode(WorkoutCodec.encode(empty)))
    }

    @Test
    fun `text that is not json is rejected`() {
        assertThrows(WorkoutCodecException::class.java) {
            WorkoutCodec.decode("this is not a program")
        }
    }

    @Test
    fun `json that is not a program is rejected`() {
        assertThrows(WorkoutCodecException::class.java) {
            WorkoutCodec.decode("""{"name": "x", "steps": "not a list"}""")
        }
    }

    @Test
    fun `a step that is missing a field is rejected`() {
        assertThrows(WorkoutCodecException::class.java) {
            WorkoutCodec.decode(
                """{"name":"x","steps":[{"speedKph":3.0,"inclinePercent":0.0}]}""",
            )
        }
    }

    @Test
    fun `a step that is not a number is rejected`() {
        assertThrows(WorkoutCodecException::class.java) {
            WorkoutCodec.decode(
                """{"name":"x","steps":[{"speedKph":"fast","inclinePercent":0.0,"durationSeconds":60}]}""",
            )
        }
    }

    @Test
    fun `a step with a fraction of a second is rejected`() {
        assertThrows(WorkoutCodecException::class.java) {
            WorkoutCodec.decode(
                """{"name":"x","steps":[{"speedKph":3.0,"inclinePercent":0.0,"durationSeconds":60.5}]}""",
            )
        }
    }

    @Test
    fun `a program with no name is rejected`() {
        assertThrows(WorkoutCodecException::class.java) {
            WorkoutCodec.decode(
                """{"steps":[{"speedKph":3.0,"inclinePercent":0.0,"durationSeconds":60}]}""",
            )
        }
    }
}
