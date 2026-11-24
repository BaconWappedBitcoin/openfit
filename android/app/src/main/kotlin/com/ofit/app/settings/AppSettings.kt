package com.ofit.app.settings

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.ofit.protocol.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/** The units every screen formats with. Metric until a screen provides something else. */
val LocalUnits = compositionLocalOf { UnitSystem.METRIC }

/**
 * Small settings that belong to the user and to this phone.
 *
 * The unit system is a display choice only: programs, the protocol and the safety ceilings stay in
 * km/h and per cent whatever is chosen here.
 */
class AppSettings(context: Context, prefsName: String = BASE_PREFS) {

    private val appContext = context.applicationContext

    /** Shared by every profile: the install id. */
    private val globalPrefs = appContext.getSharedPreferences(BASE_PREFS, Context.MODE_PRIVATE)

    /** This profile's settings. */
    private var prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** Load another profile's settings. */
    fun switchProfile(prefsName: String) {
        prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        _units.value = initialUnits()
        _hudTiles.value = com.ofit.app.workout.HudStat.parse(prefs.getString(KEY_HUD_TILES, null))
        _hudSwapped.value = prefs.getBoolean(KEY_HUD_SWAPPED, false)
    }

    private val _units = MutableStateFlow(initialUnits())

    /** The unit system in use. */
    val units: StateFlow<UnitSystem> = _units.asStateFlow()

    fun setUnits(units: UnitSystem) {
        prefs.edit().putString(KEY_UNITS, units.name).apply()
        _units.value = units
    }

    private val _hudTiles = MutableStateFlow(com.ofit.app.workout.HudStat.parse(prefs.getString(KEY_HUD_TILES, null)))

    /** The workout HUD's top-bar tiles, in order (spec 002, R3). */
    val hudTiles: StateFlow<List<com.ofit.app.workout.HudStat>> = _hudTiles.asStateFlow()

    fun setHudTiles(tiles: List<com.ofit.app.workout.HudStat>) {
        val clean = tiles.distinct().take(com.ofit.app.workout.HudStat.MAX_TILES)
        if (clean.size < com.ofit.app.workout.HudStat.MIN_TILES) return
        prefs.edit().putString(KEY_HUD_TILES, com.ofit.app.workout.HudStat.format(clean)).apply()
        _hudTiles.value = clean
    }

    private val _hudSwapped = MutableStateFlow(prefs.getBoolean(KEY_HUD_SWAPPED, false))

    /** Speed on the left and incline on the right (spec 002, R9e). */
    val hudSwapped: StateFlow<Boolean> = _hudSwapped.asStateFlow()

    fun toggleHudSwapped() {
        val next = !_hudSwapped.value
        prefs.edit().putBoolean(KEY_HUD_SWAPPED, next).apply()
        _hudSwapped.value = next
    }

    private val _legalAccepted = MutableStateFlow(globalPrefs.getInt(KEY_LEGAL, 0) >= com.ofit.app.ui.Legal.VERSION)

    /** Whether the safety and legal notice (current version) was accepted on this tablet. */
    val legalAccepted: StateFlow<Boolean> = _legalAccepted.asStateFlow()

    fun acceptLegal() {
        globalPrefs.edit().putInt(KEY_LEGAL, com.ofit.app.ui.Legal.VERSION).apply()
        _legalAccepted.value = true
    }

    private val _fullscreen = MutableStateFlow(globalPrefs.getBoolean(KEY_FULLSCREEN, true))

    /**
     * Whether OpenFit hides the status bar and the tablet's taskbar / navigation bar on every
     * screen. On by default; a swipe from the edge brings the bars back for a moment. Kept for the
     * whole tablet, not per profile.
     */
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()

    fun setFullscreen(on: Boolean) {
        globalPrefs.edit().putBoolean(KEY_FULLSCREEN, on).apply()
        _fullscreen.value = on
    }

    /**
     * A random id for this install, sent with community ratings so that one install gets one vote
     * per workout. It is not tied to a person, an account or a device identifier.
     */
    val installId: String
        get() = globalPrefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            globalPrefs.edit().putString(KEY_INSTALL_ID, it).apply()
        }

    private fun initialUnits(): UnitSystem {
        val stored = prefs.getString(KEY_UNITS, null)
        if (stored != null) return UnitSystem.fromName(stored)
        // Nothing chosen yet: follow the region of the phone. Only a few countries still use miles.
        return when (Locale.getDefault().country) {
            "US", "LR", "MM" -> UnitSystem.IMPERIAL
            else -> UnitSystem.METRIC
        }
    }

    companion object {
        const val BASE_PREFS = "ofit_settings"
        private const val KEY_UNITS = "units"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_HUD_TILES = "hud_tiles"
        private const val KEY_HUD_SWAPPED = "hud_swapped"
        private const val KEY_FULLSCREEN = "fullscreen"
        private const val KEY_LEGAL = "legal_accepted_version"
    }
}
