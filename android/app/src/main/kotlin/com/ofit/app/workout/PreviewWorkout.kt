package com.ofit.app.workout

import com.ofit.protocol.TreadmillState
import com.ofit.protocol.WorkoutCommandSink
import com.ofit.protocol.WorkoutEngineSnapshot
import com.ofit.protocol.WorkoutProgram

/**
 * A machine that is not there.
 *
 * This sink accepts every command and sends nothing. It exists so that a program can be run
 * without a treadmill: the program runs, the display fills in, the video plays, and the runner
 * can see the shape of the workout before stepping onto the machine.
 *
 * Nothing here writes to the Bluetooth link, and the state it produces leaves the machine's own
 * state untouched, so a preview can never be mistaken for a machine that is running. The
 * specification requires that the machine is never shown as running when the app cannot see it
 * (FR-013), and a preview keeps that promise: the connection state stays as it is, disconnected.
 */
class PreviewWorkoutSink : WorkoutCommandSink {

    /** Accept the start. Nothing is sent anywhere. */
    override suspend fun startWorkout(speedKph: Double, inclinePercent: Double): Boolean = true

    /** Accept the step. Nothing is sent anywhere. */
    override suspend fun applyStep(speedKph: Double, inclinePercent: Double): Boolean = true

    /** Accept the stop. Nothing is sent anywhere. */
    override suspend fun stopWorkout(): Boolean = true
}

/**
 * What a preview shows in place of the machine's own readings.
 *
 * The values are the program's own: the speed and incline of the step being run, and the
 * distance the program has covered so far, worked out from the steps themselves.
 *
 * Nothing is invented beyond that. In particular the calories are left out, because a calorie
 * count needs the runner's weight and the app does not have it; the same rule the rest of the
 * app follows is kept here, that a value the machine did not report is left out rather than
 * shown as zero.
 */
fun previewMachineState(program: WorkoutProgram, snapshot: WorkoutEngineSnapshot): TreadmillState {
    val steps = program.steps
    var meters = 0.0

    var index = 0
    while (index < snapshot.stepIndex && index < steps.size) {
        val step = steps[index]
        meters += step.speedKph * step.durationSeconds / METERS_PER_KILOMETRE_PER_SECOND
        index++
    }

    // The step being run right now, counted up to where the program has got to.
    val current = steps.getOrNull(snapshot.stepIndex)
    if (current != null) {
        meters += current.speedKph * (snapshot.stepElapsedMillis / 1000.0) /
            METERS_PER_KILOMETRE_PER_SECOND
    }

    return TreadmillState(
        actualSpeedKph = snapshot.speedKph,
        actualInclinePercent = snapshot.inclinePercent,
        distanceMeters = meters.toLong(),
        elapsedSeconds = snapshot.totalElapsedMillis / 1000,
        calories = null,
        heartRateBpm = null,
    )
}

/** How many metres a second of running at one kilometre per hour covers. */
private const val METERS_PER_KILOMETRE_PER_SECOND = 3.6