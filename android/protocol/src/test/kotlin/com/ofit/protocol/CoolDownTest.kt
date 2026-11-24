package com.ofit.protocol

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoolDownTest {
    private val limits = SafetyLimits(
        maxSpeedKph = 16.0,
        maxInclinePercent = 10.0,
        machineMaxSpeedKph = 16.0,
        machineMinSpeedKph = 1.6,
        machineMaxInclinePercent = 10.0,
        machineMinInclinePercent = 0.0,
    )

    @Test
    fun `ten flat minutes that slow down`() {
        val p = CoolDown.build(9.0, limits)
        assertEquals(600, p.totalDurationSeconds)
        assertEquals(5.0, p.steps.first().speedKph)
        assertEquals(3.2, p.steps.last().speedKph)
        assertTrue(p.steps.zipWithNext().all { (a, b) -> b.speedKph <= a.speedKph })
        assertTrue(p.steps.all { it.inclinePercent == 0.0 })
    }

    @Test
    fun `never faster than the runner was going, never below the machine`() {
        val slow = CoolDown.build(3.0, limits)
        assertTrue(slow.steps.all { it.speedKph <= 3.0 })
        val crawl = CoolDown.build(0.5, limits)
        assertTrue(crawl.steps.all { it.speedKph >= 1.6 })
    }

    private val sink = RecordingSink()
    private fun engine() = WorkoutEngine({ limits }, sink, coolDownFor = { CoolDown.build(it.speedKph, limits) })
    private val workout = WorkoutProgram("Run", listOf(WorkoutStep(9.0, 2.0, 60)))

    @Test
    fun `ending a workout cools down instead of stopping`() = runTest {
        val e = engine()
        e.start(workout)
        e.tick(10_000)
        e.end()

        assertEquals(WorkoutEngineState.COOLING_DOWN, e.snapshot.value.state)
        assertTrue(e.snapshot.value.isActive)
        assertEquals(SinkCall.Apply(5.0, 0.0), sink.calls.last())
        assertFalse(sink.calls.contains(SinkCall.Stop))
        assertEquals(10_000, e.snapshot.value.totalElapsedMillis, "the clock carries on")
    }

    @Test
    fun `a finished workout cools down, then stops at the end of it`() = runTest {
        val e = engine()
        e.start(workout)
        e.tick(60_000)
        assertEquals(WorkoutEngineState.COOLING_DOWN, e.snapshot.value.state)
        assertFalse(sink.calls.contains(SinkCall.Stop))

        e.tick(600_000)
        assertEquals(WorkoutEngineState.COMPLETED, e.snapshot.value.state)
        assertEquals(SinkCall.Stop, sink.calls.last())
    }

    @Test
    fun `ending the cool-down stops the belt`() = runTest {
        val e = engine()
        e.start(workout)
        e.end()
        e.end()
        assertEquals(WorkoutEngineState.COMPLETED, e.snapshot.value.state)
        assertEquals(SinkCall.Stop, sink.calls.last())
    }

    @Test
    fun `abandon (E-STOP) stops at once, with no cool-down`() = runTest {
        val e = engine()
        e.start(workout)
        e.abandon()
        assertEquals(WorkoutEngineState.ABANDONED, e.snapshot.value.state)
        assertEquals(SinkCall.Stop, sink.calls.last())
    }

    @Test
    fun `speed can be changed in the cool-down`() = runTest {
        val e = engine()
        e.start(workout)
        e.end()
        assertTrue(e.overrideSpeed(4.0).isAccepted)
    }

    @Test
    fun `no cool-down without one (no treadmill)`() = runTest {
        val e = WorkoutEngine({ limits }, sink)
        e.start(workout)
        e.end()
        assertEquals(WorkoutEngineState.ABANDONED, e.snapshot.value.state)
    }
}
