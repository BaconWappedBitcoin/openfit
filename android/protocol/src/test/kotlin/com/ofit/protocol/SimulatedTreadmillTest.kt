package com.ofit.protocol

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimulatedTreadmillTest {
    @Test
    fun `the belt ramps to the target, counts distance and calories, and stops`() = runBlocking {
        val sim = SimulatedTreadmill()
        assertEquals(ConnectionState.CONNECTED, sim.state.value.connectionState)
        assertEquals(WorkoutMode.IDLE, sim.state.value.workoutMode)
        assertTrue(sim.startWorkout(8.0, 2.0))
        assertTrue(sim.state.value.isRunning)
        sim.tick(1000)
        assertEquals(0.5, sim.state.value.actualSpeedKph)
        assertEquals(0.5, sim.state.value.actualInclinePercent)
        // Shown speed is the target while the belt is still getting going.
        assertEquals(8.0, sim.state.value.targetSpeedKph)
        repeat(20) { sim.tick(1000) }
        assertEquals(8.0, sim.state.value.actualSpeedKph)
        assertEquals(2.0, sim.state.value.actualInclinePercent)
        assertEquals(21L, sim.state.value.elapsedSeconds)
        val d = sim.state.value.distanceMeters!!
        assertTrue(d in 25L..45L, "distance $d after a 21 s ramp-up")
        assertTrue(sim.state.value.calories!! > 1.0)
        assertTrue(sim.stopWorkout())
        repeat(20) { sim.tick(1000) }
        assertEquals(0.0, sim.state.value.actualSpeedKph)
        assertEquals(WorkoutMode.IDLE, sim.state.value.workoutMode)
        assertFalse(sim.state.value.isRunning)
    }

    @Test
    fun `speed 0 pauses, the next step resumes, and targets stay inside the machine's range`() = runBlocking {
        val sim = SimulatedTreadmill()
        sim.startWorkout(30.0, 25.0)
        assertEquals(16.1, sim.state.value.targetSpeedKph)
        assertEquals(10.0, sim.state.value.targetInclinePercent)
        assertTrue(sim.applyStep(0.0, 1.0))
        assertEquals(WorkoutMode.PAUSE, sim.state.value.workoutMode)
        repeat(40) { sim.tick(1000) }
        assertEquals(0.0, sim.state.value.actualSpeedKph)
        assertTrue(sim.applyStep(6.0, 1.0))
        assertEquals(WorkoutMode.RUNNING, sim.state.value.workoutMode)
        assertEquals(6.0, sim.state.value.targetSpeedKph)
        sim.reset()
        assertEquals(0L, sim.state.value.distanceMeters)
        assertFalse(sim.applyStep(5.0, 0.0), "no step without a start")
    }
}
