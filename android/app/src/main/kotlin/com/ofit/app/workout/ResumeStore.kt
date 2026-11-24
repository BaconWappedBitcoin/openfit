package com.ofit.app.workout

import android.content.Context
import com.ofit.protocol.ResumePoint

/** The last running workout's position (P4), kept so it can be resumed after OpenFit closes mid-run. */
object ResumeStore {
    private const val PREFS = "ofit_resume"

    fun save(context: Context, point: ResumePoint) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("id", point.workoutId)
            .putString("name", point.name)
            .putLong("elapsed", point.elapsedMillis)
            .putLong("total", point.totalMillis)
            .putLong("savedAt", point.savedAtMillis)
            .apply()
    }

    fun load(context: Context): ResumePoint? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getString("id", null) ?: return null
        return ResumePoint(id, p.getString("name", "").orEmpty(), p.getLong("elapsed", 0), p.getLong("total", 0), p.getLong("savedAt", 0))
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
