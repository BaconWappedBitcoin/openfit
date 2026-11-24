package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkoutTemplatesTest {

    @Test
    fun `every template has the length it says and valid steps`() {
        val minutes = mapOf("Steady" to 30, "Intervals" to 30, "Hill climb" to 30, "Pyramid" to 30, "Blank" to 20)
        WorkoutTemplates.all.forEach { t ->
            assertEquals(minutes.getValue(t.name) * 60, t.steps.sumOf { it.durationSeconds }, t.name)
            t.steps.forEach { s ->
                assertTrue(s.durationSeconds > 0, t.name)
                assertTrue(s.speedKph in 0.5..12.0, t.name)
                assertTrue(s.inclinePercent in 0.0..8.0, t.name)
            }
        }
    }

    @Test
    fun `templates fit a common home treadmill`() {
        val limits = SafetyLimits(maxSpeedKph = 16.0, maxInclinePercent = 10.0)
        WorkoutTemplates.all.forEach { t ->
            assertTrue(WorkoutProgram(t.name, t.steps).validate(limits) !is ProgramValidation.Invalid, t.name)
        }
    }
}
