package com.ofit.protocol

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Skip step (user report 2026-11-24: "skip step doesn't skip ahead"). */
class WorkoutSkipTest {

    private val limits = SafetyLimits(maxSpeedKph = 12.0, maxInclinePercent = 10.0)
    private val sink = RecordingSink()
    private val program = WorkoutProgram(
        "Skip",
        listOf(
            WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60),
            WorkoutStep(speedKph = 6.0, inclinePercent = 2.0, durationSeconds = 120),
            WorkoutStep(speedKph = 5.0, inclinePercent = 1.0, durationSeconds = 60),
        ),
    )

    @Test
    fun `skip moves the clock to the start of the next step`() = runTest {
        val engine = WorkoutEngine({ limits }, sink)
        engine.start(program)
        engine.tick(20_000)
        engine.skip()
        val s = engine.snapshot.value
        assertEquals(1, s.stepIndex)
        assertEquals(60_000, s.totalElapsedMillis, "elapsed jumps to where step 2 begins")
        assertEquals(180_000, s.totalRemainingMillis)
        assertEquals(SinkCall.Apply(6.0, 2.0), sink.calls.last())
    }

    @Test
    fun `skip while paused keeps the belt stopped until resume`() = runTest {
        val engine = WorkoutEngine({ limits }, sink)
        engine.start(program)
        engine.tick(10_000)
        engine.pause()
        val callsWhenPaused = sink.calls.size
        engine.skip()
        assertEquals(callsWhenPaused, sink.calls.size, "nothing sent while paused")
        assertEquals(WorkoutEngineState.PAUSED, engine.snapshot.value.state)
        assertEquals(1, engine.snapshot.value.stepIndex)
        assertEquals(60_000, engine.snapshot.value.totalElapsedMillis)
        engine.resume()
        assertEquals(SinkCall.Apply(6.0, 2.0), sink.calls.last())
    }

    @Test
    fun `skipping the last step ends the workout`() = runTest {
        val engine = WorkoutEngine({ limits }, sink)
        engine.start(program)
        engine.skip(); engine.skip(); engine.skip()
        assertEquals(WorkoutEngineState.COMPLETED, engine.snapshot.value.state)
        assertEquals(SinkCall.Stop, sink.calls.last())
    }
}
