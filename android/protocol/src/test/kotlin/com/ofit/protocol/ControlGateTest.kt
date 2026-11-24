package com.ofit.protocol

import com.ofit.protocol.ControlGate.Observation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControlGateTest {

    @Test
    fun `no belt commands until the app starts a run`() {
        val gate = ControlGate()
        assertFalse(gate.allowsMovement())
        gate.onAppStart(0)
        assertTrue(gate.allowsMovement())
        gate.onAppStop()
        assertFalse(gate.allowsMovement())
    }

    @Test
    fun `a belt the app did not start is manual use, and blocks commands`() {
        val gate = ControlGate()
        assertEquals(Observation.NONE, gate.observe(WorkoutMode.IDLE, 0))
        assertEquals(Observation.MANUAL_USE, gate.observe(WorkoutMode.RUNNING, 1_000))
        assertEquals(Observation.NONE, gate.observe(WorkoutMode.RUNNING, 2_000))
        assertFalse(gate.allowsMovement())
        assertEquals(Observation.MANUAL_USE_ENDED, gate.observe(WorkoutMode.RESULTS, 3_000))
        assertFalse(gate.manualUse)
    }

    @Test
    fun `the console ending the app's run (STOP button) takes control away`() {
        val gate = ControlGate()
        gate.onAppStart(0)
        // Just after the start the console may still say IDLE: ignored.
        assertEquals(Observation.NONE, gate.observe(WorkoutMode.IDLE, 1_000))
        assertTrue(gate.allowsMovement())
        assertEquals(Observation.NONE, gate.observe(WorkoutMode.RUNNING, 3_000))
        assertEquals(Observation.NONE, gate.observe(WorkoutMode.PAUSE, 10_000))
        assertTrue(gate.allowsMovement(), "the app's own pause keeps the run")
        assertEquals(Observation.APP_RUN_ENDED, gate.observe(WorkoutMode.RESULTS, 20_000))
        assertFalse(gate.allowsMovement())
        // And a later belt start by hand is manual use, not the app's.
        assertEquals(Observation.MANUAL_USE, gate.observe(WorkoutMode.RUNNING, 30_000))
    }

    @Test
    fun `an unknown mode changes nothing`() {
        val gate = ControlGate()
        gate.onAppStart(0)
        assertEquals(Observation.NONE, gate.observe(null, 60_000))
        assertTrue(gate.allowsMovement())
    }
}
