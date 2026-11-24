package com.ofit.protocol

/**
 * The workout engine's commands, sent to a real machine through the session.
 *
 * Pausing is a mode change, not a speed: the I_TL console refuses a speed of 0 while running
 * (status 04, seen live 2026-11-23) and keeps the belt going, so the engine's "speed 0" becomes
 * WorkoutMode PAUSE, and the next non-zero step after it is sent as a start (mode RUNNING with
 * speed and incline), which is what brings the belt back.
 */
class SessionWorkoutSink(private val session: Session) : WorkoutCommandSink {

    /** Whether this sink has paused the machine and not yet resumed it. */
    @Volatile
    private var paused = false

    /** Start the machine at the given speed and incline. */
    override suspend fun startWorkout(speedKph: Double, inclinePercent: Double): Boolean {
        val started = session.start(speedKph, inclinePercent) is LimitCheck.Accepted
        if (started) paused = false
        return started
    }

    /** Change the running workout's speed and incline, in one write; 0 means pause. */
    override suspend fun applyStep(speedKph: Double, inclinePercent: Double): Boolean {
        if (speedKph == 0.0) {
            val ok = session.pause()
            if (ok) paused = true
            return ok
        }
        if (paused) {
            val resumed = session.start(speedKph, inclinePercent) is LimitCheck.Accepted
            if (resumed) paused = false
            return resumed
        }
        return session.setSpeedAndIncline(speedKph, inclinePercent) is LimitCheck.Accepted
    }

    /** Stop the workout. */
    override suspend fun stopWorkout(): Boolean {
        paused = false
        return session.stop()
    }
}
