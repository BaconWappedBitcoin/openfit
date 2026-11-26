package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoolDownTailTest {
    private val limits = SafetyLimits.DEFAULT
    private fun program(minutes: Int, kph: Double = 9.0) = WorkoutProgram("Run", List(minutes) { WorkoutStep(kph, 2.0, 60) })

    @Test
    fun `the last ten minutes become the cool-down and the length stays the same`() {
        val p = CoolDown.withTail(program(40), limits)
        assertEquals(40 * 60, p.totalDurationSeconds)
        assertEquals(30 * 60, p.coolDownFromSeconds)
        // The run keeps its first 30 minutes, then eases from a brisk walk down to a stroll, flat.
        assertTrue(p.steps.take(30).all { it.speedKph == 9.0 && it.inclinePercent == 2.0 })
        val tail = p.steps.drop(30)
        assertEquals(10, tail.size)
        assertTrue(tail.all { it.inclinePercent == 0.0 && it.durationSeconds == 60 })
        assertEquals(CoolDown.START_KPH, tail.first().speedKph, 0.05)
        assertEquals(CoolDown.END_KPH, tail.last().speedKph, 0.05)
        assertTrue(tail.zipWithNext().all { (a, b) -> b.speedKph <= a.speedKph })
    }

    @Test
    fun `a step the cut falls in is split, and a slow run eases from where it was`() {
        // 25 minutes in one step at 4 kph: the cut is at 15 minutes, in the middle of it.
        val p = CoolDown.withTail(WorkoutProgram("Walk", listOf(WorkoutStep(4.0, 0.0, 25 * 60))), limits)
        assertEquals(25 * 60, p.totalDurationSeconds)
        assertEquals(15 * 60, p.coolDownFromSeconds)
        assertEquals(WorkoutStep(4.0, 0.0, 15 * 60), p.steps.first())
        assertEquals(4.0, p.steps[1].speedKph, 0.05) // starts no faster than it was going
    }

    @Test
    fun `short workouts give up at most forty percent, tiny ones are left alone, and it is done once`() {
        val short = CoolDown.withTail(program(15), limits) // 40 % of 15 min = 6 min
        assertEquals(15 * 60, short.totalDurationSeconds)
        assertEquals(9 * 60, short.coolDownFromSeconds)
        assertEquals(6, short.steps.count { it.inclinePercent == 0.0 })
        val tiny = program(6)
        assertEquals(tiny, CoolDown.withTail(tiny, limits)) // 40 % is 2 minutes: not worth it
        assertNull(CoolDown.withTail(tiny, limits).coolDownFromSeconds)
        val once = CoolDown.withTail(program(40), limits)
        assertEquals(once, CoolDown.withTail(once, limits))
    }

    @Test
    fun `a program with a cool-down inside plays on past its outro`() {
        val p = program(40).withBounds(120, 35 * 60)
        assertTrue(VideoBounds.reachedOutro(p, skip = true, elapsedSeconds = 36 * 60))
        val withTail = CoolDown.withTail(p, limits)
        assertEquals(false, VideoBounds.reachedOutro(withTail, skip = true, elapsedSeconds = 36 * 60))
    }
}
