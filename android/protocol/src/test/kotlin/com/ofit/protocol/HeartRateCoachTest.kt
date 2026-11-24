package com.ofit.protocol

import com.ofit.protocol.HeartRateCoach.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeartRateCoachTest {

    // Max 180: zone 2 = 108..125.
    private val zones = HeartRateZones(180)
    private val noWarmup = HeartRateCoach.Config(warmupMillis = 0)

    /** Feed one reading a second for [seconds], starting at [from]; returns the decisions. */
    private fun HeartRateCoach.run(from: Long, seconds: Int, bpm: Int, speed: Double, step: Double = 4.0): List<Decision> =
        (0 until seconds).map { s ->
            val now = from + s * 1000L
            decide(now, bpm, now, speed, step, 0.8)
        }

    @Test
    fun `the band is the zone`() {
        assertEquals(108..125, HeartRateCoach(zones, 2).band)
        assertEquals(162..180, HeartRateCoach(zones, 5).band)
    }

    @Test
    fun `below the zone it speeds up by 0_2 at most every 15 s, after the step wait`() {
        val coach = HeartRateCoach(zones, 2, noWarmup)
        coach.onStepChanged(0)
        val early = coach.run(0, 45, 95, 4.0)
        assertTrue(early.all { it is Decision.Hold }, "no change in the first 45 s of a step")
        assertEquals(Decision.SetSpeed(4.2), coach.decide(45_000, 95, 45_000, 4.0, 4.0, 0.8))
        assertTrue(coach.run(46_000, 14, 95, 4.2).all { it is Decision.Hold }, "one change per 15 s")
        assertEquals(Decision.SetSpeed(4.4), coach.decide(60_000, 95, 60_000, 4.2, 4.0, 0.8))
    }

    @Test
    fun `never more than 1 km per h above the step`() {
        val coach = HeartRateCoach(zones, 2, noWarmup)
        assertEquals(Decision.SetSpeed(5.0), coach.decide(100_000, 95, 100_000, 4.9, 4.0, 0.8))
        coach.decide(200_000, 95, 200_000, 5.0, 4.0, 0.8).let { assertTrue(it is Decision.Hold) }
    }

    @Test
    fun `above the zone it slows by 0_3, not below the machine minimum`() {
        val coach = HeartRateCoach(zones, 2, noWarmup)
        assertEquals(Decision.SetSpeed(3.7), coach.decide(100_000, 140, 100_000, 4.0, 4.0, 0.8))
        val low = HeartRateCoach(zones, 2, noWarmup)
        assertEquals(Decision.SetSpeed(0.8), low.decide(100_000, 140, 100_000, 1.0, 1.0, 0.8))
    }

    @Test
    fun `inside the zone nothing changes`() {
        val coach = HeartRateCoach(zones, 2, noWarmup)
        assertTrue(coach.run(100_000, 60, 115, 4.0).all { it is Decision.Hold })
    }

    @Test
    fun `a stale or missing reading holds and never speeds up`() {
        val coach = HeartRateCoach(zones, 2, noWarmup)
        assertEquals(Decision.Hold("Heart rate lost: holding speed"), coach.decide(100_000, 95, 85_000, 4.0, 4.0, 0.8))
        assertEquals(Decision.Hold("Heart rate lost: holding speed"), coach.decide(100_000, null, null, 4.0, 4.0, 0.8))
    }

    @Test
    fun `very high heart rate for 10 s turns it off and slows to the step speed`() {
        val coach = HeartRateCoach(zones, 2, noWarmup)
        val decisions = coach.run(100_000, 11, 172, 4.6, step = 4.0)
        assertEquals(Decision.TurnOff("Heart rate very high", 4.0), decisions.last())
        assertTrue(decisions.dropLast(1).none { it is Decision.TurnOff })
    }

    @Test
    fun `a sudden jump is ignored as strap noise`() {
        val coach = HeartRateCoach(zones, 2, noWarmup)
        coach.run(100_000, 20, 115, 4.0)
        // One 115 -> 175 spike must not count towards the average.
        coach.decide(120_000, 175, 120_000, 4.0, 4.0, 0.8).let { assertTrue(it is Decision.Hold) }
    }

    @Test
    fun `no speed-up during the warm-up, but slowing down is allowed`() {
        val coach = HeartRateCoach(zones, 2) // Normal: 3 min warm-up
        val early = coach.run(0, 170, 95, 4.0)
        assertTrue(early.all { it is Decision.Hold }, "below the zone in the first 3 min: hold")
        assertEquals(Decision.SetSpeed(4.2), coach.decide(180_000, 95, 180_000, 4.0, 4.0, 0.8))
        val hot = HeartRateCoach(zones, 2)
        assertEquals(Decision.SetSpeed(3.7), hot.decide(1_000, 140, 1_000, 4.0, 4.0, 0.8))
    }

    @Test
    fun `gentle never goes more than 0_5 above the step, strong allows 1_5`() {
        fun ceilingOf(level: HeartRateCoach.Aggressiveness): Double {
            val coach = HeartRateCoach(zones, 2, level.config)
            var speed = 4.0
            var now = 0L
            repeat(900) {
                now += 1_000
                (coach.decide(now, 95, now, speed, 4.0, 0.8) as? Decision.SetSpeed)?.let { speed = it.kph }
            }
            return speed
        }
        assertEquals(4.5, ceilingOf(HeartRateCoach.Aggressiveness.GENTLE), 1e-9)
        assertEquals(5.0, ceilingOf(HeartRateCoach.Aggressiveness.NORMAL), 1e-9)
        assertEquals(5.5, ceilingOf(HeartRateCoach.Aggressiveness.STRONG), 1e-9)
    }

    @Test
    fun `gentle moves in 0_1 steps no more than every 30 s`() {
        val coach = HeartRateCoach(zones, 2, HeartRateCoach.Aggressiveness.GENTLE.config.copy(warmupMillis = 0))
        assertEquals(Decision.SetSpeed(4.1), coach.decide(100_000, 95, 100_000, 4.0, 4.0, 0.8))
        assertTrue(coach.run(101_000, 29, 95, 4.1).all { it is Decision.Hold })
        assertEquals(Decision.SetSpeed(4.2), coach.decide(130_000, 95, 130_000, 4.1, 4.0, 0.8))
    }
}
