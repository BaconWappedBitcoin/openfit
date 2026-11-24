package com.ofit.protocol

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The workout engine.
 *
 * The engine owns the schedule and the safety rules. Everything here is checked against a
 * recording sink, so the behaviour is verified without a treadmill.
 *
 * Constitution Article III, test first.
 */
class WorkoutEngineTest {

    private val limits = SafetyLimits(
        maxSpeedKph = 12.0,
        maxInclinePercent = 10.0,
        machineMaxSpeedKph = 16.0,
        machineMinSpeedKph = 0.0,
        machineMaxInclinePercent = 12.0,
        machineMinInclinePercent = 0.0,
    )

    private val sink = RecordingSink()

    private fun engine(limits: SafetyLimits = this.limits) = WorkoutEngine({ limits }, sink)

    private fun program(vararg steps: WorkoutStep) =
        WorkoutProgram("Test program", steps.toList())

    // ------------------------------------------------------------- validation

    @Test
    fun `a program with a step above the speed ceiling is rejected before anything is sent`() = runTest {
        val result = engine().start(program(WorkoutStep(speedKph = 14.0, inclinePercent = 0.0, durationSeconds = 60)))

        assertTrue(result is StartOutcome.Rejected)
        assertEquals(ProgramProblemReason.LIMIT_EXCEEDED, (result as StartOutcome.Rejected).problems.first().reason)
        assertEquals(Refusal.ABOVE_SPEED_CEILING, result.problems.first().refusal)
        assertEquals(0, result.problems.first().stepIndex)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun `a program with a step above the incline ceiling is rejected before anything is sent`() = runTest {
        val result = engine().start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 12.0, durationSeconds = 60)))

        assertTrue(result is StartOutcome.Rejected)
        assertEquals(Refusal.ABOVE_INCLINE_CEILING, (result as StartOutcome.Rejected).problems.first().refusal)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun `a program with no steps is rejected`() = runTest {
        val result = engine().start(program())

        assertTrue(result is StartOutcome.Rejected)
        assertEquals(ProgramProblemReason.NO_STEPS, (result as StartOutcome.Rejected).problems.first().reason)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun `a program with a step of no duration is rejected`() = runTest {
        val result = engine().start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 0.0, durationSeconds = 0)))

        assertTrue(result is StartOutcome.Rejected)
        assertEquals(
            ProgramProblemReason.NON_POSITIVE_DURATION,
            (result as StartOutcome.Rejected).problems.first().reason,
        )
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun `a program is rejected when the machine cannot reach the requested speed`() = runTest {
        val narrow = limits.copy(machineMaxSpeedKph = 8.0)

        val result = engine(narrow).start(program(WorkoutStep(speedKph = 9.0, inclinePercent = 0.0, durationSeconds = 60)))

        assertTrue(result is StartOutcome.Rejected)
        assertEquals(Refusal.ABOVE_MACHINE_MAXIMUM, (result as StartOutcome.Rejected).problems.first().refusal)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun `every offending step is reported, not just the first`() = runTest {
        val result = engine().start(
            program(
                WorkoutStep(speedKph = 14.0, inclinePercent = 0.0, durationSeconds = 60),
                WorkoutStep(speedKph = 5.0, inclinePercent = 0.0, durationSeconds = 60),
                WorkoutStep(speedKph = 5.0, inclinePercent = 20.0, durationSeconds = 60),
            ),
        )

        assertEquals(2, (result as StartOutcome.Rejected).problems.size)
        assertEquals(listOf(0, 2), result.problems.map { it.stepIndex })
    }

    // -------------------------------------------------------------- starting

    @Test
    fun `starting applies the first step's speed and incline together`() = runTest {
        val engine = engine()

        val result = engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 2.0, durationSeconds = 60)))

        assertTrue(result is StartOutcome.Started)
        assertEquals(listOf(SinkCall.Start(4.0, 2.0)), sink.calls)
        assertEquals(WorkoutEngineState.RUNNING, engine.snapshot.value.state)
    }

    @Test
    fun `a program that the machine refuses to start fails without claiming to be running`() = runTest {
        sink.startResult = false
        val engine = engine()

        val result = engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60)))

        assertTrue(result is StartOutcome.Failed)
        assertEquals(WorkoutEngineState.IDLE, engine.snapshot.value.state)
        assertFalse(engine.snapshot.value.isRunning)
    }

    // ------------------------------------------------------------- schedule

    @Test
    fun `the engine moves to the next step when the step's time is up`() = runTest {
        val engine = engine()
        engine.start(
            program(
                WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 10),
                WorkoutStep(speedKph = 8.0, inclinePercent = 3.0, durationSeconds = 10),
            ),
        )

        engine.tick(9_000)
        assertEquals(0, engine.snapshot.value.stepIndex)
        assertEquals(1, sink.calls.size)

        engine.tick(1_000)
        assertEquals(1, engine.snapshot.value.stepIndex)
        assertEquals(
            listOf(SinkCall.Start(4.0, 0.0), SinkCall.Apply(8.0, 3.0)),
            sink.calls,
        )
    }

    @Test
    fun `a step's speed and incline are applied in one command`() = runTest {
        val engine = engine()
        engine.start(
            program(
                WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 10),
                WorkoutStep(speedKph = 8.0, inclinePercent = 3.0, durationSeconds = 10),
            ),
        )

        engine.tick(10_000)

        val applied = sink.calls.filterIsInstance<SinkCall.Apply>().single()
        assertEquals(8.0, applied.speedKph)
        assertEquals(3.0, applied.inclinePercent)
    }

    @Test
    fun `the engine applies the step that is current when time runs past several steps`() = runTest {
        val engine = engine()
        engine.start(
            program(
                WorkoutStep(speedKph = 3.0, inclinePercent = 0.0, durationSeconds = 10),
                WorkoutStep(speedKph = 5.0, inclinePercent = 1.0, durationSeconds = 10),
                WorkoutStep(speedKph = 7.0, inclinePercent = 2.0, durationSeconds = 10),
            ),
        )

        engine.tick(25_000)

        assertEquals(2, engine.snapshot.value.stepIndex)
        assertEquals(
            listOf(SinkCall.Start(3.0, 0.0), SinkCall.Apply(5.0, 1.0), SinkCall.Apply(7.0, 2.0)),
            sink.calls,
        )
    }

    @Test
    fun `the program ends by stopping the machine`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 10)))

        engine.tick(10_000)

        assertEquals(WorkoutEngineState.COMPLETED, engine.snapshot.value.state)
        assertTrue(sink.calls.last() is SinkCall.Stop)
        assertTrue(engine.snapshot.value.isFinished)
    }

    @Test
    fun `the remaining time of the step and of the program is reported`() = runTest {
        val engine = engine()
        engine.start(
            program(
                WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 10),
                WorkoutStep(speedKph = 8.0, inclinePercent = 0.0, durationSeconds = 20),
            ),
        )

        engine.tick(4_000)

        assertEquals(6_000, engine.snapshot.value.stepRemainingMillis)
        assertEquals(26_000, engine.snapshot.value.totalRemainingMillis)
    }

    // --------------------------------------------------------- pause and resume

    @Test
    fun `pausing stops the belt and resuming continues the same step`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 2.0, durationSeconds = 60)))
        engine.tick(10_000)

        engine.pause()

        assertEquals(WorkoutEngineState.PAUSED, engine.snapshot.value.state)
        assertTrue(sink.calls.last() is SinkCall.Apply)
        assertEquals(0.0, (sink.calls.last() as SinkCall.Apply).speedKph)
        assertEquals(2.0, (sink.calls.last() as SinkCall.Apply).inclinePercent)

        engine.resume()

        assertEquals(WorkoutEngineState.RUNNING, engine.snapshot.value.state)
        val resumed = sink.calls.last() as SinkCall.Apply
        assertEquals(4.0, resumed.speedKph)
        assertEquals(2.0, resumed.inclinePercent)
    }

    @Test
    fun `a paused program reports the belt as stopped`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 2.0, durationSeconds = 60)))

        engine.pause()

        assertEquals(0.0, engine.snapshot.value.speedKph)
        assertEquals(2.0, engine.snapshot.value.inclinePercent)
    }

    @Test
    fun `resuming does not restart the step's clock`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60)))
        engine.tick(20_000)

        engine.pause()
        engine.tick(30_000)
        engine.resume()

        assertEquals(20_000, engine.snapshot.value.stepElapsedMillis)
        assertEquals(40_000, engine.snapshot.value.stepRemainingMillis)
    }

    @Test
    fun `a paused engine does not advance the schedule`() = runTest {
        val engine = engine()
        engine.start(
            program(
                WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 10),
                WorkoutStep(speedKph = 8.0, inclinePercent = 0.0, durationSeconds = 10),
            ),
        )
        engine.tick(5_000)

        engine.pause()
        engine.tick(30_000)

        assertEquals(0, engine.snapshot.value.stepIndex)
    }

    // ------------------------------------------------------------------ skip

    @Test
    fun `skipping moves to the next step`() = runTest {
        val engine = engine()
        engine.start(
            program(
                WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60),
                WorkoutStep(speedKph = 8.0, inclinePercent = 3.0, durationSeconds = 60),
            ),
        )

        engine.skip()

        assertEquals(1, engine.snapshot.value.stepIndex)
        assertEquals(SinkCall.Apply(8.0, 3.0), sink.calls.last())
    }

    @Test
    fun `skipping the last step ends the program and stops the machine`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60)))

        engine.skip()

        assertEquals(WorkoutEngineState.COMPLETED, engine.snapshot.value.state)
        assertTrue(sink.calls.last() is SinkCall.Stop)
    }

    @Test
    fun `skipping starts the next step's clock from zero`() = runTest {
        val engine = engine()
        engine.start(
            program(
                WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60),
                WorkoutStep(speedKph = 8.0, inclinePercent = 0.0, durationSeconds = 60),
            ),
        )
        engine.tick(30_000)

        engine.skip()

        assertEquals(0, engine.snapshot.value.stepElapsedMillis)
    }

    // --------------------------------------------------------------- abandon

    @Test
    fun `abandoning stops the machine`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60)))

        engine.abandon()

        assertEquals(WorkoutEngineState.ABANDONED, engine.snapshot.value.state)
        assertTrue(sink.calls.last() is SinkCall.Stop)
        assertFalse(engine.snapshot.value.isRunning)
    }

    @Test
    fun `an abandoned program does not advance the schedule`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60)))
        engine.abandon()

        engine.tick(120_000)

        assertEquals(0, engine.snapshot.value.stepIndex)
    }

    // ------------------------------------------------------------- link loss

    @Test
    fun `a lost link ends the program and the machine is no longer reported as running`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60)))

        engine.onLinkLost()

        assertEquals(WorkoutEngineState.FAILED, engine.snapshot.value.state)
        assertFalse(engine.snapshot.value.isRunning)
        assertFalse(engine.snapshot.value.isActive)
    }

    @Test
    fun `a lost link sends no command, because the machine cannot be reached`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60)))
        val callsBefore = sink.calls.size

        engine.onLinkLost()

        assertEquals(callsBefore, sink.calls.size)
    }

    @Test
    fun `a lost link ends the program rather than leaving it running in the background`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60)))

        engine.onLinkLost()
        engine.tick(120_000)

        assertEquals(WorkoutEngineState.FAILED, engine.snapshot.value.state)
        assertNull(engine.snapshot.value.nextSpeedKph)
    }

    // --------------------------------------------------------- manual override

    @Test
    fun `manual speed override sends a command within ceilings and marks the snapshot`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 2.0, durationSeconds = 60)))

        val result = engine.overrideSpeed(7.5)

        assertTrue(result.isAccepted)
        assertEquals(7.5, engine.snapshot.value.speedKph)
        assertEquals(2.0, engine.snapshot.value.inclinePercent)
        assertTrue(engine.snapshot.value.manualOverride)
        assertEquals(SinkCall.Apply(7.5, 2.0), sink.calls.last())
    }

    @Test
    fun `manual speed override above the ceiling is refused without sending anything`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 1.0, durationSeconds = 60)))
        val callsBefore = sink.calls.size

        val result = engine.overrideSpeed(13.0)

        assertEquals(Refusal.ABOVE_SPEED_CEILING, result.refusal)
        assertEquals(callsBefore, sink.calls.size)
        assertEquals(5.0, engine.snapshot.value.speedKph)
        assertFalse(engine.snapshot.value.manualOverride)
    }

    @Test
    fun `manual incline override sends a command within ceilings and clears on next step`() = runTest {
        val engine = engine()
        engine.start(
            program(
                WorkoutStep(speedKph = 5.0, inclinePercent = 1.0, durationSeconds = 10),
                WorkoutStep(speedKph = 6.0, inclinePercent = 3.0, durationSeconds = 10),
            ),
        )

        val result = engine.overrideIncline(4.5)

        assertTrue(result.isAccepted)
        assertEquals(4.5, engine.snapshot.value.inclinePercent)
        assertTrue(engine.snapshot.value.manualOverride)
        assertEquals(SinkCall.Apply(5.0, 4.5), sink.calls.last())

        engine.tick(10_000)

        assertEquals(1, engine.snapshot.value.stepIndex)
        assertEquals(6.0, engine.snapshot.value.speedKph)
        assertEquals(3.0, engine.snapshot.value.inclinePercent)
        assertFalse(engine.snapshot.value.manualOverride)
    }

    @Test
    fun `manual incline override above the ceiling is refused without sending anything`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 1.0, durationSeconds = 60)))
        val callsBefore = sink.calls.size

        val result = engine.overrideIncline(11.0)

        assertEquals(Refusal.ABOVE_INCLINE_CEILING, result.refusal)
        assertEquals(callsBefore, sink.calls.size)
        assertEquals(1.0, engine.snapshot.value.inclinePercent)
    }

    @Test
    fun `manual override is refused while paused so the belt is not started unexpectedly`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 1.0, durationSeconds = 60)))
        engine.pause()
        val callsBefore = sink.calls.size

        val result = engine.overrideSpeed(6.0)

        assertFalse(result.isAccepted)
        assertEquals(callsBefore, sink.calls.size)
    }

    @Test
    fun `resetting to program restores the current step speed and incline`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 2.0, durationSeconds = 60)))
        engine.overrideSpeed(8.0)
        engine.overrideIncline(4.0)

        val reset = engine.resetToProgram()

        assertTrue(reset)
        assertEquals(5.0, engine.snapshot.value.speedKph)
        assertEquals(2.0, engine.snapshot.value.inclinePercent)
        assertFalse(engine.snapshot.value.manualOverride)
        assertEquals(SinkCall.Apply(5.0, 2.0), sink.calls.last())
    }

    @Test
    fun `an incline change takes over only the incline, and a speed change only the speed`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 2.0, durationSeconds = 60)))

        engine.overrideIncline(3.0)
        assertTrue(engine.snapshot.value.inclineOverride)
        assertFalse(engine.snapshot.value.speedOverride)

        engine.overrideSpeed(6.0)
        assertTrue(engine.snapshot.value.speedOverride)
        assertTrue(engine.snapshot.value.inclineOverride)
    }

    @Test
    fun `following the program again on one value leaves the other alone`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 2.0, durationSeconds = 60)))
        engine.overrideSpeed(8.0)
        engine.overrideIncline(4.0)

        assertTrue(engine.resetSpeedToProgram())
        assertEquals(5.0, engine.snapshot.value.speedKph)
        assertEquals(4.0, engine.snapshot.value.inclinePercent)
        assertFalse(engine.snapshot.value.speedOverride)
        assertTrue(engine.snapshot.value.inclineOverride)
        assertEquals(SinkCall.Apply(5.0, 4.0), sink.calls.last())

        assertTrue(engine.resetInclineToProgram())
        assertEquals(2.0, engine.snapshot.value.inclinePercent)
        assertFalse(engine.snapshot.value.manualOverride)
        assertEquals(SinkCall.Apply(5.0, 2.0), sink.calls.last())
    }

    @Test
    fun `incline cannot go below 0 percent on a machine with no decline`() = runTest {
        val engine = engine()
        engine.start(program(WorkoutStep(speedKph = 5.0, inclinePercent = 0.0, durationSeconds = 60)))
        val callsBefore = sink.calls.size

        val result = engine.overrideIncline(-0.5)

        assertEquals(Refusal.BELOW_MACHINE_MINIMUM, result.refusal)
        assertEquals(callsBefore, sink.calls.size)
    }
}
