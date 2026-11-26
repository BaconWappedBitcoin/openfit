package com.ofit.app.workout

import android.content.Context

/**
 * The real route of a workout imported from a GPX file, for the 3D trail map (2026-11-25): the
 * outline from [com.ofit.protocol.GpxRoute.outline], kept by workout name, since the workout gets
 * its id only when the editor saves it. A renamed workout falls back to the drawn route.
 */
object WorldRoute {
    private const val PREFS = "ofit_videos"

    fun save(context: Context, workoutName: String, outline: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("route:$workoutName", outline).apply()
    }

    fun outline(context: Context, workoutName: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("route:$workoutName", null)
}
