package com.ofit.app.workout

import android.content.Context
import com.ofit.protocol.Intensity

/** Each profile's own intensity level (P7), in prefs `ofit_intensity` under the profile's key. */
object IntensityStore {
    private const val PREFS = "ofit_intensity"

    fun load(context: Context, profileKey: String): Intensity.Level =
        Intensity.Level.of(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(profileKey, null))

    fun save(context: Context, profileKey: String, level: Intensity.Level) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(profileKey, level.name).apply()
    }
}
