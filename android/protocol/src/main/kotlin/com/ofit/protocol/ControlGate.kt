package com.ofit.protocol

/**
 * Who is running the belt (spec 007).
 *
 * An app that stays connected while someone uses the treadmill by hand, and sends it commands then,
 * would be a safety problem. OpenFit only sends belt commands (speed, incline, pause)
 * while a run it started itself is in progress. Stop is always allowed.
 *
 * Pure state, fed by the session; no I/O, so it is unit-tested on its own.
 */
class ControlGate(
    /** After the app starts the belt, the console may still report IDLE for a moment. */
    private val startGraceMillis: Long = 6_000,
) {
    /** What a poll revealed. */
    enum class Observation {
        /** Nothing worth acting on. */
        NONE,

        /** The app's run ended at the console (STOP button, safety key, or its own results screen). */
        APP_RUN_ENDED,

        /** The belt is running and the app did not start it: someone is using the treadmill by hand. */
        MANUAL_USE,

        /** A run the app did not start has ended. */
        MANUAL_USE_ENDED,
    }

    @Volatile
    var ownsRun: Boolean = false
        private set

    @Volatile
    var manualUse: Boolean = false
        private set

    private var startedAtMillis = Long.MIN_VALUE / 2

    /** Speed, incline and pause may only be sent while the app owns the run. */
    fun allowsMovement(): Boolean = ownsRun && !manualUse

    /** The app's start (or resume) was accepted by the console. */
    fun onAppStart(nowMillis: Long) {
        ownsRun = true
        manualUse = false
        startedAtMillis = nowMillis
    }

    /** The app's stop was accepted, or the link is gone. */
    fun onAppStop() {
        ownsRun = false
    }

    /** Feed the console's reported mode from each poll. */
    fun observe(mode: WorkoutMode?, nowMillis: Long): Observation {
        val belt = mode == WorkoutMode.RUNNING || mode == WorkoutMode.PAUSE
        if (ownsRun) {
            if (!belt && mode != null && nowMillis - startedAtMillis > startGraceMillis) {
                ownsRun = false
                return Observation.APP_RUN_ENDED
            }
            return Observation.NONE
        }
        if (belt && !manualUse) {
            manualUse = true
            return Observation.MANUAL_USE
        }
        if (!belt && manualUse && mode != null) {
            manualUse = false
            return Observation.MANUAL_USE_ENDED
        }
        return Observation.NONE
    }
}
