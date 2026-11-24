package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Speed and incline ceilings.
 *
 * The ceiling is enforced in the protocol layer, not in the user interface, so that no screen can
 * produce a command above it. Constitution Article IV and Constraint 3.
 */
class SafetyLimitsTest {

    private val limits = SafetyLimits(
        maxSpeedKph = 12.0,
        maxInclinePercent = 10.0,
        machineMaxSpeedKph = 16.0,
        machineMinSpeedKph = 0.0,
        machineMaxInclinePercent = 12.0,
        machineMinInclinePercent = 0.0,
    )

    @Test
    fun `a speed at the ceiling is accepted`() {
        assertTrue(limits.checkSpeed(12.0).isAccepted)
    }

    @Test
    fun `a speed above the ceiling is refused`() {
        val result = limits.checkSpeed(12.5)

        assertEquals(Refusal.ABOVE_SPEED_CEILING, result.refusal)
    }

    @Test
    fun `an incline above the ceiling is refused`() {
        val result = limits.checkIncline(10.5)

        assertEquals(Refusal.ABOVE_INCLINE_CEILING, result.refusal)
    }

    @Test
    fun `a speed above the machine maximum is refused even below the user ceiling`() {
        val narrow = limits.copy(machineMaxSpeedKph = 10.0)

        val result = narrow.checkSpeed(11.0)

        assertEquals(Refusal.ABOVE_MACHINE_MAXIMUM, result.refusal)
    }

    @Test
    fun `a speed below the machine minimum is refused`() {
        val result = limits.checkSpeed(-1.0)

        assertEquals(Refusal.BELOW_MACHINE_MINIMUM, result.refusal)
    }

    @Test
    fun `a command is refused when the machine is not armed`() {
        val result = limits.check(speedKph = 5.0, inclinePercent = 1.0, readiness = MachineReadiness.LOCKED)

        assertEquals(Refusal.MACHINE_NOT_ARMED, result.refusal)
    }

    @Test
    fun `a command is refused when the machine is running its own workout`() {
        val result = limits.check(
            speedKph = 5.0,
            inclinePercent = 1.0,
            readiness = MachineReadiness.BUSY_LOCAL,
        )

        assertEquals(Refusal.MACHINE_BUSY_LOCAL, result.refusal)
    }

    @Test
    fun `an armed machine accepts a command inside both ceilings`() {
        val result = limits.check(speedKph = 5.0, inclinePercent = 1.0, readiness = MachineReadiness.ARMED)

        assertTrue(result.isAccepted)
    }

    @Test
    fun `the effective ceiling is the lower of the user ceiling and the machine maximum`() {
        val narrow = limits.copy(machineMaxSpeedKph = 8.0)

        assertEquals(8.0, narrow.effectiveMaxSpeedKph)
        assertEquals(10.0, narrow.effectiveMaxInclinePercent)
    }

    @Test
    fun `a machine that has not reported its limits does not lower the user ceiling`() {
        val unknown = limits.copy(
            machineMaxSpeedKph = null,
            machineMaxInclinePercent = null,
        )

        assertEquals(12.0, unknown.effectiveMaxSpeedKph)
        assertEquals(10.0, unknown.effectiveMaxInclinePercent)
    }

    @Test
    fun `speed zero (pause) is accepted even when the machine reports a minimum speed`() {
        val limits = SafetyLimits(
            maxSpeedKph = 16.1,
            maxInclinePercent = 10.0,
            machineMinSpeedKph = 0.8,
            machineMaxSpeedKph = 16.1,
        )
        assertTrue(limits.checkSpeed(0.0).isAccepted)
        assertTrue(limits.check(0.0, 2.0, MachineReadiness.ARMED).isAccepted)
        // Anything between zero and the minimum is still refused.
        assertEquals(Refusal.BELOW_MACHINE_MINIMUM, limits.checkSpeed(0.5).refusal)
    }
}
