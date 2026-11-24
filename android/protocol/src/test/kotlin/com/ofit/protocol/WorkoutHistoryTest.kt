package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkoutHistoryTest {

    private val run = HistoryEntry(
        startedAtMillis = 1_795_000_000_000,
        name = "Test \"ramp\"",
        durationSeconds = 1_800,
        distanceMeters = 4_000.0,
        calories = 250.5,
        verticalGainMeters = 40.0,
        averageHeartRate = 132,
        maxHeartRate = 161,
        outcome = "COMPLETED",
    )

    @Test
    fun `entries survive a round trip, newest first`() {
        val older = run.copy(startedAtMillis = 1_000, calories = null, averageHeartRate = null, maxHeartRate = null)
        val back = HistoryCodec.decode(HistoryCodec.encode(listOf(older, run)))
        assertEquals(listOf(run, older), back)
    }

    @Test
    fun `a damaged entry is skipped, a damaged file is empty`() {
        val text = "[{\"name\":\"no start\"}," + HistoryCodec.encode(listOf(run)).removePrefix("[")
        assertEquals(listOf(run), HistoryCodec.decode(text))
        assertTrue(HistoryCodec.decode("not json").isEmpty())
    }

    @Test
    fun `average speed and totals`() {
        assertEquals(8.0, run.averageSpeedKph!!, 1e-9)
        assertNull(run.copy(durationSeconds = 5).averageSpeedKph)
        val totals = HistoryTotals.of(listOf(run, run.copy(calories = null)))
        assertEquals(2, totals.workouts)
        assertEquals(3_600, totals.durationSeconds)
        assertEquals(8_000.0, totals.distanceMeters)
        assertEquals(250.5, totals.calories)
    }
}
