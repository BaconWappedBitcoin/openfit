package com.ofit.app.profile

import android.content.Context
import com.ofit.app.ui.theme.OfitAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One person who uses the treadmill. */
data class Profile(
    val id: Int,
    val name: String,
    /** Avatar colour, ARGB. */
    val color: Long,
    val accent: OfitAccent,
    /** An icon from ProfileIcons, or null for the initial. */
    val icon: String? = null,
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

/**
 * The people who share this tablet (the "Who's running?" pattern of media apps such as NuvioTV).
 *
 * Each profile has its own settings, heart-rate strap and zones, HUD layout, theme and history.
 * The treadmill, its unlock and the saved workouts are shared. Profile 1 uses the storage names the
 * app had before profiles existed, so nothing already saved is lost.
 */
class ProfileManager(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = context.applicationContext.getSharedPreferences("ofit_profiles", Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _current = MutableStateFlow(
        _profiles.value.firstOrNull { it.id == prefs.getInt(KEY_CURRENT, 1) } ?: _profiles.value.first(),
    )
    val current: StateFlow<Profile> = _current.asStateFlow()

    /** Called after the current profile changes, so the other managers can reload. */
    var onSwitch: (Profile) -> Unit = {}

    /** Whether to ask "Who's running?" at start-up. */
    val shouldAsk: Boolean get() = _profiles.value.size > 1

    fun select(profile: Profile) {
        prefs.edit().putInt(KEY_CURRENT, profile.id).apply()
        val changed = profile.id != _current.value.id
        _current.value = profile
        if (changed) onSwitch(profile)
    }

    /** Add a profile; returns it. */
    fun add(name: String, color: Long, accent: OfitAccent, icon: String? = null): Profile {
        val id = (_profiles.value.maxOfOrNull { it.id } ?: 0) + 1
        val profile = Profile(id, name.trim().ifBlank { "Runner $id" }, color, accent, icon)
        save(_profiles.value + profile)
        return profile
    }

    fun update(profile: Profile) {
        save(_profiles.value.map { if (it.id == profile.id) profile else it })
        if (profile.id == _current.value.id) _current.value = profile
    }

    /** Remove a profile. The last one cannot be removed; removing the current one switches to another. */
    fun remove(profile: Profile, deleteData: Boolean = false) {
        if (_profiles.value.size <= 1) return
        val rest = _profiles.value.filterNot { it.id == profile.id }
        save(rest)
        if (profile.id == _current.value.id) select(rest.first())
        if (deleteData) {
            // Its history, strap and zones. Profile 1's settings file also holds tablet-wide values
            // (install id, fullscreen), so for profile 1 only its history and strap go.
            runCatching { java.io.File(appContext.filesDir, scopedFileName("history", "json", profile.id)).delete() }
            runCatching { appContext.deleteSharedPreferences(scopedPrefsName("ofit_hr", profile.id)) }
            if (profile.id != 1) runCatching { appContext.deleteSharedPreferences(scopedPrefsName("ofit_settings", profile.id)) }
        }
    }

    /** The name of a SharedPreferences file for the current profile. */
    fun prefsName(base: String, profile: Profile = _current.value): String = scopedPrefsName(base, profile.id)

    /** The name of a private file for the current profile. */
    fun fileName(base: String, ext: String, profile: Profile = _current.value): String =
        scopedFileName(base, ext, profile.id)

    private fun save(list: List<Profile>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("color", it.color)
                    .put("accent", it.accent.name)
                    .put("icon", it.icon ?: ""),
            )
        }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
        _profiles.value = list
    }

    private fun load(): List<Profile> {
        val list = runCatching {
            val array = JSONArray(prefs.getString(KEY_LIST, "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Profile(
                    o.getInt("id"),
                    o.getString("name"),
                    o.getLong("color"),
                    OfitAccent.fromName(o.optString("accent")),
                    o.optString("icon").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
        return list.ifEmpty { listOf(Profile(1, "Me", AVATAR_COLORS.first(), OfitAccent.LAVENDER)) }
    }

    companion object {
        private const val KEY_LIST = "profiles"
        private const val KEY_CURRENT = "current"

        /** Profile 1 keeps the names used before profiles existed, so no data is lost. */
        fun scopedPrefsName(base: String, profileId: Int): String =
            if (profileId == 1) base else "${base}_p$profileId"

        fun scopedFileName(base: String, ext: String, profileId: Int): String =
            if (profileId == 1) "$base.$ext" else "${base}_p$profileId.$ext"

        /** Avatar colours to choose from. */
        val AVATAR_COLORS: List<Long> = listOf(
            0xFF7C4DFF, 0xFF2196F3, 0xFF00BFA5, 0xFF4CAF50,
            0xFFFFB300, 0xFFFF7043, 0xFFE91E63, 0xFF8D6E63,
        )
    }
}
