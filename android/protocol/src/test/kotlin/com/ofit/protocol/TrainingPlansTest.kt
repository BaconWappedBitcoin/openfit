package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

class TrainingPlansTest {
    private fun entry(at: String, name: String, outcome: String = "COMPLETED") =
        HistoryEntry(Instant.parse(at).toEpochMilli(), name, 1200, 2000.0, null, 0.0, null, null, outcome)

    @Test
    fun `every session of every plan is a sensible program`() {
        TrainingPlans.ALL.forEach { plan ->
            repeat(plan.totalSessions) { i ->
                val p = plan.session(i)
                assertTrue(p.steps.isNotEmpty(), p.name)
                assertTrue(p.totalDurationSeconds in 15 * 60..6 * 3600, "${p.name}: ${p.totalDurationSeconds}")
                assertTrue(plan.owns(p.name))
            }
        }
        // Walk to run ends with one steady 20-minute jog between warm-up and cool-down.
        val last = TrainingPlans.WALK_TO_RUN.session(TrainingPlans.WALK_TO_RUN.totalSessions - 1)
        assertEquals(3, last.steps.size)
        assertEquals(1200, last.steps[1].durationSeconds)
        assertEquals("Walk to run · Week 1, session 2", TrainingPlans.WALK_TO_RUN.session(1).name)
    }

    @Test
    fun `race plans build to the race distance, with a taper before it`() {
        fun km(p: WorkoutProgram) = p.steps.sumOf { it.speedKph / 3.6 * it.durationSeconds } / 1000.0
        val m = TrainingPlans.MARATHON
        val race = m.session(m.totalSessions - 1)
        assertEquals("Marathon · Week 16, session 4", race.name)
        assertEquals(42.195, race.steps[1].speedKph / 3.6 * race.steps[1].durationSeconds / 1000.0, 0.05)
        // Long runs (session 4) grow to 32 km by the end of the build, then taper.
        val longs = (1..15).map { w -> km(m.session((w - 1) * 4 + 3)) }
        assertTrue(longs[13] > longs[0] + 15, longs.toString())
        assertTrue(longs[14] < longs[13], "taper: $longs")
        assertEquals(5.0, TrainingPlans.RUN_5K.session(TrainingPlans.RUN_5K.totalSessions - 1).let { it.steps[1].speedKph / 3.6 * it.steps[1].durationSeconds / 1000.0 }, 0.05)
        assertEquals(7, TrainingPlans.ALL.size)
    }

    @Test
    fun `progress counts completed plan sessions since the start, in order`() {
        val plan = TrainingPlans.WALK_TO_RUN
        val e = TrainingPlans.Enrollment(plan.id, Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(), setOf(DayOfWeek.TUESDAY), null)
        val history = listOf(
            entry("2026-10-30T08:00:00Z", plan.sessionName(1, 1)), // before the start
            entry("2026-11-02T08:00:00Z", plan.sessionName(1, 1)),
            entry("2026-11-04T08:00:00Z", plan.sessionName(1, 2), outcome = "ABANDONED"),
            entry("2026-11-05T08:00:00Z", plan.sessionName(1, 2)),
            entry("2026-11-06T08:00:00Z", plan.sessionName(1, 3)),
            entry("2026-11-07T08:00:00Z", "Paris France Virtual Run"),
            entry("2026-11-10T07:00:00Z", plan.sessionName(2, 1)),
        )
        // Tuesday 10 Nov: a training day, and a session was already done this morning.
        val s = TrainingPlans.status(e, history, Instant.parse("2026-11-10T18:00:00Z"), ZoneOffset.UTC)!!
        assertEquals(4, s.done)
        assertEquals(plan.sessionName(2, 2), s.next!!.name)
        assertEquals(2, s.week)
        assertTrue(s.trainingDay)
        assertTrue(s.doneToday)
        assertFalse(s.dueToday)
        // Next Tuesday, nothing yet: due.
        val later = TrainingPlans.status(e, history, Instant.parse("2026-11-17T09:00:00Z"), ZoneOffset.UTC)!!
        assertTrue(later.dueToday)
        // A Wednesday is not a chosen day.
        assertFalse(TrainingPlans.status(e, history, Instant.parse("2026-11-18T09:00:00Z"), ZoneOffset.UTC)!!.trainingDay)
    }

    @Test
    fun `a finished plan has no next session`() {
        val plan = TrainingPlans.WALKING_HABIT
        val e = TrainingPlans.Enrollment(plan.id, 0, DayOfWeek.values().toSet(), 18 * 60)
        val history = List(plan.totalSessions) { i ->
            entry("2026-11-%02dT08:00:00Z".format(i + 1), plan.session(i).name)
        }
        val s = TrainingPlans.status(e, history, Instant.parse("2026-11-30T09:00:00Z"), ZoneOffset.UTC)!!
        assertTrue(s.finished)
        assertNull(s.next)
        assertEquals(1f, s.progress)
        assertFalse(s.dueToday)
        assertNull(TrainingPlans.status(e.copy(planId = "nope"), history))
    }
}
