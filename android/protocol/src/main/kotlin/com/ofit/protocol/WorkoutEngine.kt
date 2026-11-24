package com.ofit.protocol

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The engine that runs a programmed workout.
 *
 * The engine owns the schedule: it decides which step is current, applies speed and incline
 * together, and moves on when a step's time is up. It knows nothing about Bluetooth; it talks to
 * the machine through a [WorkoutCommandSink], which is what lets it be tested without a phone.
 *
 * Two rules govern everything here:
 *  - the ceiling is checked before any command is sent, in the protocol layer, so no screen can
 *    produce a command above it (constitution Article IV, Constraint 3);
 *  - a lost link ends the program (FR-013). The machine is never reported as running when the
 *    app cannot see it.
 */
class WorkoutEngine(
    private val limitsProvider: () -> SafetyLimits,
    private val sink: WorkoutCommandSink,
    /**
     * The cool-down to run when a workout ends (finished, or ended by the runner), or null for none
     * (no treadmill). The belt slows down over it instead of stopping at once; only E-STOP or
     * "End cool-down" stops it straight away (user request, 2026-11-24).
     */
    private val coolDownFor: (WorkoutEngineSnapshot) -> WorkoutProgram? = { null },
) {

    private val _snapshot = MutableStateFlow(WorkoutEngineSnapshot.idle())

    /** The current state of the engine. */
    val snapshot: StateFlow<WorkoutEngineSnapshot> = _snapshot.asStateFlow()

    private var program: WorkoutProgram? = null

    /**
     * Start a program.
     *
     * Steps outside the machine's range or the user's ceilings are moved into range first, so a
     * workout's values never stop it starting; then the program is validated (a program with no
     * steps or a step with no duration is still rejected, and nothing is sent).
     */
    suspend fun start(requested: WorkoutProgram): StartOutcome {
        if (_snapshot.value.isActive) {
            return StartOutcome.Failed("A workout is already running.")
        }

        val limits = limitsProvider()
        val program = requested.fitTo(limits)
        val validation = program.validate(limits)
        if (validation is ProgramValidation.Invalid) {
            return StartOutcome.Rejected(validation.problems)
        }

        val step = program.steps.first()
        val accepted = sink.startWorkout(step.speedKph, step.inclinePercent)
        if (!accepted) {
            return StartOutcome.Failed("The treadmill refused to start.")
        }

        this.program = program
        val next = program.steps.getOrNull(1)
        _snapshot.value = WorkoutEngineSnapshot(
            state = WorkoutEngineState.RUNNING,
            programName = program.name,
            stepIndex = 0,
            stepCount = program.steps.size,
            stepElapsedMillis = 0,
            stepRemainingMillis = step.durationMillis,
            totalElapsedMillis = 0,
            totalRemainingMillis = program.totalDurationSeconds * 1000L,
            speedKph = step.speedKph,
            inclinePercent = step.inclinePercent,
            nextSpeedKph = next?.speedKph,
            nextInclinePercent = next?.inclinePercent,
            message = null,
        )
        return StartOutcome.Started(program)
    }

    /**
     * Advance the schedule.
     *
     * [elapsedMillis] is the time since the last call. It is ignored unless the engine is
     * running, so a paused program does not lose or gain time.
     */
    suspend fun tick(elapsedMillis: Long) {
        if (elapsedMillis <= 0) return
        if (!_snapshot.value.isRunning) return

        var remaining = elapsedMillis
        while (remaining > 0) {
            val state = _snapshot.value
            if (!state.isRunning) return

            val program = this.program ?: return
            val step = program.steps.getOrNull(state.stepIndex) ?: return
            val stepRemaining = (step.durationMillis - state.stepElapsedMillis).coerceAtLeast(0)

            if (remaining < stepRemaining) {
                mutate {
                    it.copy(
                        stepElapsedMillis = it.stepElapsedMillis + remaining,
                        stepRemainingMillis = stepRemaining - remaining,
                        totalElapsedMillis = it.totalElapsedMillis + remaining,
                        totalRemainingMillis = (it.totalRemainingMillis - remaining).coerceAtLeast(0),
                    )
                }
                return
            }

            // This step ends now. Whatever is left over belongs to the next step.
            mutate {
                it.copy(
                    stepElapsedMillis = step.durationMillis,
                    stepRemainingMillis = 0,
                    totalElapsedMillis = it.totalElapsedMillis + stepRemaining,
                    totalRemainingMillis = (it.totalRemainingMillis - stepRemaining).coerceAtLeast(0),
                )
            }
            remaining -= stepRemaining
            if (!advanceToNextStep()) return
        }
    }

    /** Pause the program. The belt is stopped and the step keeps its remaining time. */
    suspend fun pause() {
        val state = _snapshot.value
        if (state.state != WorkoutEngineState.RUNNING) return

        val accepted = sink.applyStep(0.0, state.inclinePercent)
        mutate {
            it.copy(
                state = WorkoutEngineState.PAUSED,
                speedKph = 0.0,
                message = if (accepted) {
                    null
                } else {
                    "The treadmill did not confirm that it stopped."
                },
            )
        }
    }

    /** Resume the program at the same point in the same step. */
    suspend fun resume() {
        val state = _snapshot.value
        if (state.state != WorkoutEngineState.PAUSED) return

        val program = this.program ?: return
        val step = program.steps.getOrNull(state.stepIndex) ?: return

        val accepted = sink.applyStep(step.speedKph, step.inclinePercent)
        if (!accepted) {
            fail("The treadmill refused to resume.")
            return
        }

        mutate {
            it.copy(
                state = WorkoutEngineState.RUNNING,
                speedKph = step.speedKph,
                inclinePercent = step.inclinePercent,
                speedOverride = false,
                inclineOverride = false,
                message = null,
            )
        }
    }

    /**
     * Override the target speed for the current step.
     *
     * The safety ceiling is checked in the protocol layer before anything is sent to the machine.
     * The override lasts until the runner returns to the program (resetToProgram), across steps.
     */
    suspend fun overrideSpeed(speedKph: Double): LimitCheck {
        val state = _snapshot.value
        if (!state.isRunning) {
            return LimitCheck.Refused(Refusal.MACHINE_NOT_ARMED)
        }
        if (speedKph < 0.0) {
            return LimitCheck.Refused(Refusal.BELOW_MACHINE_MINIMUM)
        }
        val check = limitsProvider().checkSpeed(speedKph)
        if (check is LimitCheck.Refused) return check

        val accepted = sink.applyStep(speedKph, state.inclinePercent)
        if (!accepted) {
            mutate { it.copy(message = "The treadmill refused the speed change.") }
            return LimitCheck.Refused(Refusal.MACHINE_NOT_ARMED)
        }

        mutate {
            it.copy(
                speedKph = speedKph,
                speedOverride = true,
                message = null,
            )
        }
        return LimitCheck.Accepted
    }

    /**
     * Override the target incline for the current step.
     *
     * The safety ceiling is checked in the protocol layer before anything is sent to the machine.
     * The override lasts until the runner returns to the program (resetToProgram), across steps.
     */
    suspend fun overrideIncline(inclinePercent: Double): LimitCheck {
        val state = _snapshot.value
        if (!state.isRunning) {
            return LimitCheck.Refused(Refusal.MACHINE_NOT_ARMED)
        }
        val limits = limitsProvider()
        if (limits.machineMinInclinePercent == null && inclinePercent < DEFAULT_MIN_INCLINE_PERCENT) {
            return LimitCheck.Refused(Refusal.BELOW_MACHINE_MINIMUM)
        }
        val check = limits.checkIncline(inclinePercent)
        if (check is LimitCheck.Refused) return check

        val accepted = sink.applyStep(state.speedKph, inclinePercent)
        if (!accepted) {
            mutate { it.copy(message = "The treadmill refused the incline change.") }
            return LimitCheck.Refused(Refusal.MACHINE_NOT_ARMED)
        }

        mutate {
            it.copy(
                inclinePercent = inclinePercent,
                inclineOverride = true,
                message = null,
            )
        }
        return LimitCheck.Accepted
    }

    /** Return to the current step's programmed speed and incline. */
    suspend fun resetToProgram(): Boolean {
        val state = _snapshot.value
        if (state.state != WorkoutEngineState.RUNNING) return false
        val step = program?.steps?.getOrNull(state.stepIndex) ?: return false

        val accepted = sink.applyStep(step.speedKph, step.inclinePercent)
        if (!accepted) {
            mutate { it.copy(message = "The treadmill refused the step change.") }
            return false
        }

        mutate {
            it.copy(
                speedKph = step.speedKph,
                inclinePercent = step.inclinePercent,
                speedOverride = false,
                inclineOverride = false,
                message = null,
            )
        }
        return true
    }

    /** Return only the speed to the current step's programmed value; the incline stays as it is. */
    suspend fun resetSpeedToProgram(): Boolean {
        val state = _snapshot.value
        if (state.state != WorkoutEngineState.RUNNING) return false
        val step = program?.steps?.getOrNull(state.stepIndex) ?: return false
        if (!sink.applyStep(step.speedKph, state.inclinePercent)) {
            mutate { it.copy(message = "The treadmill refused the speed change.") }
            return false
        }
        mutate { it.copy(speedKph = step.speedKph, speedOverride = false, message = null) }
        return true
    }

    /** Return only the incline to the current step's programmed value; the speed stays as it is. */
    suspend fun resetInclineToProgram(): Boolean {
        val state = _snapshot.value
        if (state.state != WorkoutEngineState.RUNNING) return false
        val step = program?.steps?.getOrNull(state.stepIndex) ?: return false
        if (!sink.applyStep(state.speedKph, step.inclinePercent)) {
            mutate { it.copy(message = "The treadmill refused the incline change.") }
            return false
        }
        mutate { it.copy(inclinePercent = step.inclinePercent, inclineOverride = false, message = null) }
        return true
    }

    /** Move to the next step immediately. */
    suspend fun skip() {
        val state = _snapshot.value
        if (!state.isRunning && state.state != WorkoutEngineState.PAUSED) return
        // The workout's clock jumps to the start of the next step too, so the time, the progress
        // and the video move ahead with it (before, only the step number changed).
        val skipped = state.stepRemainingMillis.coerceAtLeast(0)
        mutate {
            it.copy(
                totalElapsedMillis = it.totalElapsedMillis + skipped,
                totalRemainingMillis = (it.totalRemainingMillis - skipped).coerceAtLeast(0),
            )
        }
        if (state.state == WorkoutEngineState.PAUSED) {
            // Paused means the belt stays stopped: move to the next step without sending it. Resume
            // sends that step's speed and incline.
            val program = this.program ?: return
            val nextIndex = state.stepIndex + 1
            if (nextIndex >= program.steps.size) {
                complete()
                return
            }
            val step = program.steps[nextIndex]
            val next = program.steps.getOrNull(nextIndex + 1)
            mutate {
                it.copy(
                    stepIndex = nextIndex,
                    stepElapsedMillis = 0,
                    stepRemainingMillis = step.durationMillis,
                    inclinePercent = if (it.inclineOverride) it.inclinePercent else step.inclinePercent,
                    nextSpeedKph = next?.speedKph,
                    nextInclinePercent = next?.inclinePercent,
                )
            }
            return
        }
        advanceToNextStep()
    }

    /**
     * Jump to a point in the workout (the progress bar, rewind, fast forward). The step at that
     * point becomes current: while running its speed and incline are sent; while paused nothing is
     * sent until Resume. Jumping to the end finishes the workout.
     */
    suspend fun seekTo(targetMillis: Long) {
        val state = _snapshot.value
        if (state.state != WorkoutEngineState.RUNNING && state.state != WorkoutEngineState.PAUSED) return
        val program = this.program ?: return
        val total = program.totalDurationSeconds * 1000L
        val target = targetMillis.coerceIn(0, total)
        if (target >= total) {
            mutate { it.copy(totalElapsedMillis = total, totalRemainingMillis = 0) }
            complete()
            return
        }
        var start = 0L
        var index = 0
        while (index < program.steps.size - 1 && start + program.steps[index].durationMillis <= target) {
            start += program.steps[index].durationMillis
            index++
        }
        val step = program.steps[index]
        val inStep = target - start
        // Manual speed or incline is kept across the jump, as at a step change.
        val seekSpeed = if (state.speedOverride) state.speedKph else step.speedKph
        val seekIncline = if (state.inclineOverride) state.inclinePercent else step.inclinePercent
        if (state.state == WorkoutEngineState.RUNNING && index != state.stepIndex) {
            if (!sink.applyStep(seekSpeed, seekIncline)) {
                fail("The treadmill refused the step change.")
                return
            }
        }
        val next = program.steps.getOrNull(index + 1)
        val running = state.state == WorkoutEngineState.RUNNING
        val sameStep = index == state.stepIndex
        mutate {
            it.copy(
                stepIndex = index,
                stepElapsedMillis = inStep,
                stepRemainingMillis = step.durationMillis - inStep,
                totalElapsedMillis = target,
                totalRemainingMillis = total - target,
                speedKph = if (sameStep) it.speedKph else if (running) seekSpeed else it.speedKph,
                inclinePercent = if (sameStep) it.inclinePercent else seekIncline,
                nextSpeedKph = next?.speedKph,
                nextInclinePercent = next?.inclinePercent,
            )
        }
    }

    /**
     * The runner ended the workout: go into the cool-down when there is one (the belt slows down
     * rather than stopping), otherwise stop. Ending the cool-down itself stops the belt. A paused
     * workout (belt already stopped) just ends.
     */
    suspend fun end() {
        val state = _snapshot.value
        when {
            state.state == WorkoutEngineState.COOLING_DOWN -> complete()
            state.state == WorkoutEngineState.RUNNING && startCoolDown() -> Unit
            else -> abandon()
        }
    }

    /** Switch from the workout to its cool-down without stopping the belt. False if there is none. */
    private suspend fun startCoolDown(): Boolean {
        val current = _snapshot.value
        val coolDown = coolDownFor(current)?.fitTo(limitsProvider()) ?: return false
        val first = coolDown.steps.firstOrNull() ?: return false
        if (!sink.applyStep(first.speedKph, first.inclinePercent)) return false
        program = coolDown
        val next = coolDown.steps.getOrNull(1)
        mutate {
            it.copy(
                state = WorkoutEngineState.COOLING_DOWN,
                stepIndex = 0,
                stepCount = coolDown.steps.size,
                stepElapsedMillis = 0,
                stepRemainingMillis = first.durationMillis,
                // The clock carries on from the workout, so time, history and summary include it.
                totalRemainingMillis = coolDown.totalDurationSeconds * 1000L,
                speedKph = first.speedKph,
                inclinePercent = first.inclinePercent,
                nextSpeedKph = next?.speedKph,
                nextInclinePercent = next?.inclinePercent,
                speedOverride = false,
                inclineOverride = false,
                message = null,
            )
        }
        return true
    }

    /** Stop the program and stop the machine at once (E-STOP, no treadmill, or a paused workout). */
    suspend fun abandon() {
        if (!_snapshot.value.isActive) return
        sink.stopWorkout()
        mutate {
            it.copy(
                state = WorkoutEngineState.ABANDONED,
                stepRemainingMillis = 0,
                totalRemainingMillis = 0,
                message = "The workout was stopped.",
            )
        }
    }

    /**
     * The link to the machine was lost.
     *
     * The program ends. No stop command is sent, because the machine is unreachable; what the
     * machine does when the link drops is its own timeout, and is one of the things the capture
     * procedure measures.
     */
    suspend fun onLinkLost() {
        if (!_snapshot.value.isActive) return
        mutate {
            it.copy(
                state = WorkoutEngineState.FAILED,
                nextSpeedKph = null,
                nextInclinePercent = null,
                message = "The link to the treadmill was lost.",
            )
        }
    }

    /** Return the engine to its idle state. */
    fun reset() {
        program = null
        _snapshot.value = WorkoutEngineSnapshot.idle()
    }

    /**
     * Move to the next step, applying its speed and incline together.
     *
     * Returns false when the program has ended or the machine refused.
     */
    private suspend fun advanceToNextStep(): Boolean {
        val program = this.program ?: return false
        val nextIndex = _snapshot.value.stepIndex + 1

        if (nextIndex >= program.steps.size) {
            // The workout ran to its end: cool down first, unless this already was the cool-down.
            if (_snapshot.value.state == WorkoutEngineState.RUNNING && startCoolDown()) return true
            complete()
            return false
        }

        val step = program.steps[nextIndex]
        // A speed or incline the runner set by hand stays until they choose to follow the program
        // again (user request 2026-11-24): the new step only brings what was not overridden.
        val now = _snapshot.value
        val speed = if (now.speedOverride) now.speedKph else step.speedKph
        val incline = if (now.inclineOverride) now.inclinePercent else step.inclinePercent
        val accepted = sink.applyStep(speed, incline)
        if (!accepted) {
            fail("The treadmill refused the next step.")
            return false
        }

        val next = program.steps.getOrNull(nextIndex + 1)
        mutate {
            it.copy(
                stepIndex = nextIndex,
                stepElapsedMillis = 0,
                stepRemainingMillis = step.durationMillis,
                speedKph = speed,
                inclinePercent = incline,
                nextSpeedKph = next?.speedKph,
                nextInclinePercent = next?.inclinePercent,
                message = null,
            )
        }
        return true
    }

    /** The program ran to the end: stop the machine. */
    private suspend fun complete() {
        sink.stopWorkout()
        mutate {
            it.copy(
                state = WorkoutEngineState.COMPLETED,
                stepRemainingMillis = 0,
                totalRemainingMillis = 0,
                message = "The workout is complete.",
            )
        }
    }

    /** Something went wrong that the user needs to know about. */
    private fun fail(reason: String) {
        mutate { it.copy(state = WorkoutEngineState.FAILED, message = reason) }
    }

    private fun mutate(block: (WorkoutEngineSnapshot) -> WorkoutEngineSnapshot) {
        _snapshot.update(block)
    }

    private companion object {
        // No decline unless the machine reports one: the T 6.5 refuses anything below 0 %.
        const val DEFAULT_MIN_INCLINE_PERCENT = 0.0
    }
}

/** Where the engine is in the program. */
enum class WorkoutEngineState {
    IDLE,
    RUNNING,
    PAUSED,
    /** The workout is over and the belt is slowing down over the cool-down; it stops at its end. */
    COOLING_DOWN,
    COMPLETED,
    ABANDONED,
    FAILED,
}

/** The outcome of starting a program. */
sealed interface StartOutcome {
    /** The program started. */
    data class Started(val program: WorkoutProgram) : StartOutcome

    /** The program was rejected. Nothing was sent to the machine. */
    data class Rejected(val problems: List<ProgramProblem>) : StartOutcome

    /** The machine refused to start. */
    data class Failed(val reason: String) : StartOutcome
}

/**
 * What the engine tells the machine.
 *
 * Kept as three methods rather than one command type so the mapping onto the protocol is
 * explicit: starting a workout and changing a running workout are different writes.
 */
interface WorkoutCommandSink {
    /** Start the machine at the given speed and incline. */
    suspend fun startWorkout(speedKph: Double, inclinePercent: Double): Boolean

    /** Change the running workout's speed and incline, together. */
    suspend fun applyStep(speedKph: Double, inclinePercent: Double): Boolean

    /** Stop the workout. */
    suspend fun stopWorkout(): Boolean
}

/** The engine's view of the world, for the user interface. */
data class WorkoutEngineSnapshot(
    val state: WorkoutEngineState,
    val programName: String,
    val stepIndex: Int,
    val stepCount: Int,
    val stepElapsedMillis: Long,
    val stepRemainingMillis: Long,
    val totalElapsedMillis: Long,
    val totalRemainingMillis: Long,
    val speedKph: Double,
    val inclinePercent: Double,
    val nextSpeedKph: Double?,
    val nextInclinePercent: Double?,
    val message: String?,
    /** The runner has changed the speed away from the program in this step. */
    val speedOverride: Boolean = false,
    /** The runner has changed the incline away from the program in this step. */
    val inclineOverride: Boolean = false,
) {
    /** Either value is under the runner's control rather than the program's. */
    val manualOverride: Boolean get() = speedOverride || inclineOverride

    /** Whether the program is running right now. */
    val isRunning: Boolean get() = state == WorkoutEngineState.RUNNING || state == WorkoutEngineState.COOLING_DOWN

    /** In the cool-down after the workout. */
    val isCoolingDown: Boolean get() = state == WorkoutEngineState.COOLING_DOWN

    /** Whether the program is started, whether or not it is paused. */
    val isActive: Boolean get() = state == WorkoutEngineState.RUNNING || state == WorkoutEngineState.PAUSED ||
        state == WorkoutEngineState.COOLING_DOWN

    /** Whether the program has ended, for any reason. */
    val isFinished: Boolean get() = state == WorkoutEngineState.COMPLETED ||
        state == WorkoutEngineState.ABANDONED ||
        state == WorkoutEngineState.FAILED

    companion object {
        /** The state of an engine that has not been started. */
        fun idle(): WorkoutEngineSnapshot = WorkoutEngineSnapshot(
            state = WorkoutEngineState.IDLE,
            programName = "",
            stepIndex = 0,
            stepCount = 0,
            stepElapsedMillis = 0,
            stepRemainingMillis = 0,
            totalElapsedMillis = 0,
            totalRemainingMillis = 0,
            speedKph = 0.0,
            inclinePercent = 0.0,
            nextSpeedKph = null,
            nextInclinePercent = null,
            message = null,
        )
    }
}
