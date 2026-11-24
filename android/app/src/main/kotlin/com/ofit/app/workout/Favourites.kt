package com.ofit.app.workout

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Favourite workouts (P16), by saved-workout id, kept on the tablet. */
object Favourites {
    private const val PREFS = "ofit_favourites"
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    private var loaded = false

    fun ids(context: Context): StateFlow<Set<String>> {
        if (!loaded) {
            _ids.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("ids", emptySet()).orEmpty().toSet()
            loaded = true
        }
        return _ids.asStateFlow()
    }

    fun toggle(context: Context, id: String) {
        ids(context)
        val next = if (id in _ids.value) _ids.value - id else _ids.value + id
        _ids.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("ids", next).apply()
    }
}
