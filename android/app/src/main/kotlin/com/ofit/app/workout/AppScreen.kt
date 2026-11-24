package com.ofit.app.workout

import com.ofit.app.data.SavedWorkout

/**
 * Where the application is.
 *
 * The app is small enough that a plain screen model is clearer than a navigation graph.
 */
sealed interface AppScreen {
    /** Finding the treadmill and driving it by hand. */
    data object Home : AppScreen

    /** The list of saved workouts. */
    data object Workouts : AppScreen

    /** Searching YouTube for a running video to make a workout from. */
    data object VideoSearch : AppScreen

    /** The community library of shared workouts. */
    data object Library : AppScreen

    /** Choosing a heart-rate strap, and the zones (spec 002, section 3b). */
    data object HeartRate : AppScreen

    /** Past workouts and totals. */
    data object History : AppScreen

    /** Connect or find a treadmill, and drive it by hand. */
    data object ManualControl : AppScreen

    /** About, licence and the safety / legal notice. */
    data object Legal : AppScreen

    /** The user's own Gemini key for video-to-workout. */
    data object AiKey : AppScreen

    /** Pick a speed, incline and goal and go. */
    data object QuickStart : AppScreen

    /** "Who's running?" */
    data object Profiles : AppScreen

    /** Browse workouts and videos: hero banner and rows. */
    data object Browse : AppScreen

    /** Training plans with a schedule and reminders (P11/P12). */
    data object Plans : AppScreen

    /** The coach designer: how the virtual coach looks. */
    data object CoachDesigner : AppScreen

    /** Writing a new workout, or changing one that is already saved. */
    data class Editor(val saved: SavedWorkout?) : AppScreen

    /** The visual editor: a workout on a timeline under its video (savedId null = a new one). */
    data class VisualEditor(
        val savedId: String?,
        val name: String,
        val steps: List<com.ofit.protocol.WorkoutStep>,
        val videoId: String?,
    ) : AppScreen

    /** After a workout: its summary (the History entry saved for it, found by its start time). */
    data class Summary(val startedAfterMillis: Long, val name: String) : AppScreen

    /** Running a workout. */
    data class Run(val saved: SavedWorkout, val resumeAtMillis: Long = 0) : AppScreen
}
