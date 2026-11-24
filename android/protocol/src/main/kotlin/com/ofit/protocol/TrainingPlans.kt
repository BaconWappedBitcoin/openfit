package com.ofit.protocol

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Training plans with a schedule (P11/P12): a few built-in multi-week plans, each a list of
 * sessions in order. Progress is worked out from History alone (completed workouts whose name is
 * a session of the plan, since the plan began), so nothing drifts and a restore brings it back.
 * Sessions go in order: a missed day does not skip one, the next session simply waits.
 */
object TrainingPlans {
    data class Plan(
        val id: String,
        val name: String,
        val detail: String,
        val weeks: Int,
        val sessionsPerWeek: Int,
        /** Training days suggested when the plan is picked. */
        val suggestedDays: Set<DayOfWeek>,
        private val build: (week: Int, session: Int) -> List<WorkoutStep>,
    ) {
        val totalSessions: Int get() = weeks * sessionsPerWeek

        /** Session [index] (0-based over the whole plan) as a program, named so History can find it. */
        fun session(index: Int): WorkoutProgram {
            val i = index.coerceIn(0, totalSessions - 1)
            val week = i / sessionsPerWeek + 1
            val n = i % sessionsPerWeek + 1
            return WorkoutProgram(name = sessionName(week, n), steps = build(week, n))
        }

        fun sessionName(week: Int, n: Int) = "$name · Week $week, session $n"

        /** Whether a History entry's name is one of this plan's sessions. */
        fun owns(entryName: String) = entryName.startsWith("$name · Week ")
    }

    /** Someone following a plan: which, since when, on which days, and when to be reminded. */
    data class Enrollment(
        val planId: String,
        val startedAtMillis: Long,
        val days: Set<DayOfWeek>,
        /** Minutes after midnight for the reminder on training days; null for none. */
        val reminderMinuteOfDay: Int?,
    )

    data class Status(
        val plan: Plan,
        /** Completed sessions so far. */
        val done: Int,
        /** The next session, or null when the plan is finished. */
        val next: WorkoutProgram?,
        /** 1-based week of the next session (or the last week once finished). */
        val week: Int,
        /** Today is one of the chosen days. */
        val trainingDay: Boolean,
        /** A plan session was finished today already. */
        val doneToday: Boolean,
    ) {
        val finished: Boolean get() = next == null
        val progress: Float get() = done.toFloat() / plan.totalSessions
        /** Show "today's session" on Home and remind: a training day, nothing done yet today, plan not over. */
        val dueToday: Boolean get() = trainingDay && !doneToday && !finished
    }

    fun byId(id: String): Plan? = ALL.firstOrNull { it.id == id }

    fun status(
        enrollment: Enrollment,
        history: List<HistoryEntry>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Status? {
        val plan = byId(enrollment.planId) ?: return null
        val mine = history.filter {
            it.outcome == "COMPLETED" && it.startedAtMillis >= enrollment.startedAtMillis && plan.owns(it.name)
        }
        val done = mine.size.coerceAtMost(plan.totalSessions)
        val today = LocalDate.ofInstant(now, zone)
        val doneToday = mine.any { LocalDate.ofInstant(Instant.ofEpochMilli(it.startedAtMillis), zone) == today }
        val next = if (done < plan.totalSessions) plan.session(done) else null
        return Status(
            plan = plan,
            done = done,
            next = next,
            week = (done.coerceAtMost(plan.totalSessions - 1) / plan.sessionsPerWeek) + 1,
            trainingDay = today.dayOfWeek in enrollment.days,
            doneToday = doneToday,
        )
    }

    // ---- The plans -------------------------------------------------------------------------

    private const val WALK = 5.5
    private const val EASY = 4.5

    private fun min(m: Double) = (m * 60).roundToInt()

    /** Run/walk blocks repeated to fill [mainSeconds], between a 5-minute warm-up and cool-down walk. */
    private fun runWalk(runSec: Int, walkSec: Int, mainSeconds: Int, runKph: Double): List<WorkoutStep> = buildList {
        add(WorkoutStep(EASY, 0.0, min(5.0)))
        if (walkSec == 0) {
            add(WorkoutStep(runKph, 1.0, mainSeconds))
        } else {
            var left = mainSeconds
            while (left > 0) {
                val r = minOf(runSec, left); add(WorkoutStep(runKph, 1.0, r)); left -= r
                if (left <= 0) break
                val w = minOf(walkSec, left); add(WorkoutStep(WALK, 1.0, w)); left -= w
            }
        }
        add(WorkoutStep(EASY, 0.0, min(5.0)))
    }

    val WALK_TO_RUN = Plan(
        id = "walk-to-run",
        name = "Walk to run",
        detail = "8 weeks from run-walk intervals to 20 minutes of steady jogging",
        weeks = 8,
        sessionsPerWeek = 3,
        suggestedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
    ) { week, _ ->
        val run = intArrayOf(60, 90, 120, 180, 300, 480, 600, 1200)[week - 1]
        val walk = intArrayOf(120, 120, 120, 90, 90, 60, 60, 0)[week - 1]
        runWalk(run, walk, min(20.0), runKph = 8.0)
    }

    val WALKING_HABIT = Plan(
        id = "walking-habit",
        name = "Walking habit",
        detail = "4 weeks of brisk walks, 20 minutes growing to 35",
        weeks = 4,
        sessionsPerWeek = 4,
        suggestedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
    ) { week, _ ->
        val main = 15.0 + 5.0 * week
        listOf(
            WorkoutStep(EASY, 0.0, min(3.0)),
            WorkoutStep(5.6, 1.0, min(main)),
            WorkoutStep(EASY, 0.0, min(2.0)),
        )
    }

    val HILL_STRENGTH = Plan(
        id = "hill-strength",
        name = "Hill strength",
        detail = "6 weeks of walking hill repeats, a little steeper each week",
        weeks = 6,
        sessionsPerWeek = 3,
        suggestedDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
    ) { week, _ ->
        val hill = 3.0 + week
        buildList {
            add(WorkoutStep(EASY, 0.0, min(4.0)))
            repeat(6) {
                add(WorkoutStep(5.0, hill, min(2.0)))
                add(WorkoutStep(5.0, 1.0, min(2.0)))
            }
            add(WorkoutStep(EASY, 0.0, min(2.0)))
        }
    }

    // ---- Race plans: 5K up to the marathon --------------------------------------------------

    private const val EASY_RUN = 8.5
    private const val LONG_RUN = 8.0
    private const val TEMPO = 10.5
    private const val INTERVAL = 11.5
    private const val RECOVER = 6.0
    private const val RACE = 9.5

    private fun kmSeconds(km: Double, kph: Double) = (km / kph * 3600).roundToInt()
    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t.coerceIn(0.0, 1.0)

    /**
     * A race plan: easy runs, one quality session a week (intervals and tempo runs in turn), a long
     * run that grows to [peakLongKm] then tapers, and the race distance itself as the last session.
     * Paces are moderate defaults; the intensity setting scales them for each runner.
     */
    private fun racePlan(
        id: String, name: String, detail: String, weeks: Int, perWeek: Int, raceKm: Double,
        startLongKm: Double, peakLongKm: Double, easyFromMin: Double, easyToMin: Double, days: Set<DayOfWeek>,
    ): Plan {
        val taper = if (weeks >= 12) 2 else 1
        val buildWeeks = weeks - taper
        return Plan(id, name, detail, weeks, perWeek, days) { week, n ->
            val t = (week - 1).toDouble() / (buildWeeks - 1).coerceAtLeast(1)
            val tapering = week > buildWeeks
            val taperShare = if (!tapering) 1.0 else if (week == weeks) 0.5 else 0.7
            when {
                // The last session of the plan: race day, on the belt.
                week == weeks && n == perWeek -> listOf(
                    WorkoutStep(EASY, 0.0, min(5.0)),
                    WorkoutStep(RACE, 1.0, kmSeconds(raceKm, RACE)),
                    WorkoutStep(EASY, 0.0, min(5.0)),
                )
                // The long run, last in the week.
                n == perWeek -> {
                    val km = (if (tapering) peakLongKm * taperShare else lerp(startLongKm, peakLongKm, t))
                    listOf(
                        WorkoutStep(EASY, 0.0, min(5.0)),
                        WorkoutStep(LONG_RUN, 1.0, kmSeconds((km * 2).roundToInt() / 2.0, LONG_RUN)),
                        WorkoutStep(EASY, 0.0, min(5.0)),
                    )
                }
                // The quality session, second in the week: intervals on odd weeks, tempo on even.
                n == 2 -> buildList {
                    add(WorkoutStep(EASY, 0.0, min(3.0)))
                    add(WorkoutStep(EASY_RUN, 1.0, min(7.0)))
                    if (week % 2 == 1) {
                        val reps = if (tapering) 3 else (4 + week / 3).coerceAtMost(8)
                        repeat(reps) {
                            add(WorkoutStep(INTERVAL, 1.0, min(3.0)))
                            add(WorkoutStep(RECOVER, 1.0, min(2.0)))
                        }
                    } else {
                        val tempo = if (tapering) 12.0 else (15.0 + week).coerceAtMost(40.0)
                        add(WorkoutStep(TEMPO, 1.0, min(tempo)))
                    }
                    add(WorkoutStep(EASY, 0.0, min(5.0)))
                }
                // Easy runs.
                else -> listOf(
                    WorkoutStep(EASY, 0.0, min(3.0)),
                    WorkoutStep(EASY_RUN, 1.0, min((lerp(easyFromMin, easyToMin, t) * taperShare).roundToInt().toDouble())),
                    WorkoutStep(EASY, 0.0, min(2.0)),
                )
            }
        }
    }

    val RUN_5K = racePlan(
        "run-5k", "5K", "8 weeks to a strong 5 km, for anyone who can jog 20 minutes",
        weeks = 8, perWeek = 3, raceKm = 5.0, startLongKm = 4.0, peakLongKm = 7.0, easyFromMin = 20.0, easyToMin = 30.0,
        days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
    )
    val RUN_10K = racePlan(
        "run-10k", "10K", "10 weeks to 10 km: easy runs, intervals and a growing long run",
        weeks = 10, perWeek = 3, raceKm = 10.0, startLongKm = 6.0, peakLongKm = 12.0, easyFromMin = 25.0, easyToMin = 40.0,
        days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
    )
    val HALF_MARATHON = racePlan(
        "half-marathon", "Half marathon", "12 weeks to 21.1 km, with a two-week taper",
        weeks = 12, perWeek = 4, raceKm = 21.1, startLongKm = 8.0, peakLongKm = 18.0, easyFromMin = 30.0, easyToMin = 45.0,
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
    )
    val MARATHON = racePlan(
        "marathon", "Marathon", "16 weeks to 42.2 km: long runs up to 32 km, then a taper",
        weeks = 16, perWeek = 4, raceKm = 42.195, startLongKm = 12.0, peakLongKm = 32.0, easyFromMin = 35.0, easyToMin = 55.0,
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
    )

    val ALL = listOf(WALKING_HABIT, WALK_TO_RUN, HILL_STRENGTH, RUN_5K, RUN_10K, HALF_MARATHON, MARATHON)
}
