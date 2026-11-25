package com.ofit.app.workout

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One saved coach (user request 2026-11-24): its name, woman or man, look and voice. */
data class SavedCoach(
    val id: String,
    val name: String = CoachPersona.DEFAULT_NAME,
    val male: Boolean = false,
    val look: CoachLook = CoachLook(),
    val voice: String? = null,
) {
    val choice: CoachChoice get() = if (male) CoachChoice.MALE else CoachChoice.FEMALE
    val persona: CoachPersona get() = CoachPersona(name, voice)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("male", male)
        put("style", look.style.key)
        look.top?.let { put("top", it) }
        look.bottom?.let { put("bottom", it) }
        put("skin", look.skin)
        put("hair", look.hair)
        voice?.let { put("voice", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = SavedCoach(
            id = o.getString("id"),
            name = CoachPersona.clean(o.optString("name")),
            male = o.optBoolean("male"),
            look = CoachLook(
                style = CoachLook.Style.from(o.optString("style")),
                top = if (o.has("top")) o.getInt("top") else null,
                bottom = if (o.has("bottom")) o.getInt("bottom") else null,
                skin = o.optInt("skin", CoachLook.SKIN_TONES[2]),
                hair = o.optInt("hair", CoachLook.HAIR_COLOURS[0]),
            ),
            voice = o.optString("voice").takeIf { it.isNotBlank() },
        )
    }
}

/**
 * The saved coaches and which one runs with you, kept on the tablet. The first time, the coach set
 * up before saved coaches existed becomes the first one, so nothing is lost.
 */
object CoachRoster {
    private const val PREFS = "ofit_videos"
    private const val KEY_LIST = "coaches"
    private const val KEY_ACTIVE = "coach_active"

    fun newId(): String = java.util.UUID.randomUUID().toString().take(8)

    fun list(context: Context): List<SavedCoach> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = p.getString(KEY_LIST, null)
        if (raw != null) {
            val parsed = runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { SavedCoach.fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) return parsed
        }
        // Before saved coaches: the one coach there was, from its look and the name and voice
        // given to the woman or man coach.
        val choice = CoachLook.choice(context).let { if (it == CoachChoice.OFF) CoachChoice.FEMALE else it }
        val persona = CoachLook.persona(context, choice)
        val first = SavedCoach(newId(), persona.name, choice == CoachChoice.MALE, CoachLook.load(context), persona.voice)
        save(context, listOf(first))
        setActive(context, first.id)
        return listOf(first)
    }

    fun save(context: Context, coaches: List<SavedCoach>) {
        val arr = JSONArray()
        coaches.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    /** Add or replace [coach] (by id) and make it the one that runs with you. */
    fun upsert(context: Context, coach: SavedCoach): List<SavedCoach> {
        val all = list(context)
        val next = if (all.any { it.id == coach.id }) all.map { if (it.id == coach.id) coach else it } else all + coach
        save(context, next)
        setActive(context, coach.id)
        return next
    }

    fun delete(context: Context, id: String): List<SavedCoach> {
        val next = list(context).filterNot { it.id == id }
        if (next.isEmpty()) return list(context)
        save(context, next)
        if (activeId(context) == id) setActive(context, next.first().id)
        return next
    }

    fun activeId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, null)

    fun setActive(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE, id).apply()
    }

    /** The coach that runs with you: the chosen one, or the first. */
    fun active(context: Context): SavedCoach {
        val all = list(context)
        return all.firstOrNull { it.id == activeId(context) } ?: all.first()
    }
}
