package com.ofit.protocol

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Seeking with the progress bar, rewind and fast forward. */
class WorkoutSeekTest {

    private val limits = SafetyLimits(maxSpeedKph = 12.0, maxInclinePercent = 10.0)
    private val sink = RecordingSink()
    private val program = WorkoutProgram(
        "Seek",
        listOf(
            WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60),
            WorkoutStep(speedKph = 6.0, inclinePercent = 2.0, durationSeconds = 120),
            WorkoutStep(speedKph = 5.0, inclinePercent = 1.0, durationSeconds = 60),
        ),
    )

    @Test
    fun `seeking into another step makes it current and sends it`() = runTest {
        val engine = WorkoutEngine({ limits }, sink)
        engine.start(program)
        engine.seekTo(90_000)
        val s = engine.snapshot.value
        assertEquals(1, s.stepIndex)
        assertEquals(30_000, s.stepElapsedMillis)
        assertEquals(90_000, s.stepRemainingMillis)
        assertEquals(90_000, s.totalElapsedMillis)
        assertEquals(150_000, s.totalRemainingMillis)
        assertEquals(SinkCall.Apply(6.0, 2.0), sink.calls.last())
    }

    @Test
    fun `seeking within the same step sends nothing`() = runTest {
        val engine = WorkoutEngine({ limits }, sink)
        engine.start(program)
        val before = sink.calls.size
        engine.seekTo(40_000)
        assertEquals(before, sink.calls.size)
        assertEquals(40_000, engine.snapshot.value.totalElapsedMillis)
    }

    @Test
    fun `rewinding goes back a step`() = runTest {
        val engine = WorkoutEngine({ limits }, sink)
        engine.start(program)
        engine.seekTo(100_000)
        engine.seekTo(100_000 - 60_000)
        assertEquals(0, engine.snapshot.value.stepIndex)
        assertEquals(SinkCall.Apply(4.0, 0.0), sink.calls.last())
    }

    @Test
    fun `seeking while paused sends nothing`() = runTest {
        val engine = WorkoutEngine({ limits }, sink)
        engine.start(program)
        engine.pause()
        val before = sink.calls.size
        engine.seekTo(200_000)
        assertEquals(before, sink.calls.size)
        assertEquals(2, engine.snapshot.value.stepIndex)
        engine.resume()
        assertEquals(SinkCall.Apply(5.0, 1.0), sink.calls.last())
    }

    @Test
    fun `seeking to the end finishes the workout`() = runTest {
        val engine = WorkoutEngine({ limits }, sink)
        engine.start(program)
        engine.seekTo(10_000_000)
        assertEquals(WorkoutEngineState.COMPLETED, engine.snapshot.value.state)
        assertEquals(SinkCall.Stop, sink.calls.last())
    }
}
