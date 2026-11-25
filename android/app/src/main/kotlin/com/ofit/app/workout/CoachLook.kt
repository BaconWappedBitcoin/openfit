package com.ofit.app.workout

import android.content.Context

/**
 * How the virtual coach looks, set in the coach designer (user request 2026-11-24) and kept on the
 * tablet: the outfit and its colours, skin tone and hair. Colours are 0xRRGGBB; a null top uses the
 * profile's theme colour, a null bottom the outfit's own (charcoal shorts, or the top colour for the
 * race kit).
 */
data class CoachLook(
    val style: Style = Style.RACE,
    val top: Int? = null,
    val bottom: Int? = null,
    val skin: Int = SKIN_TONES[2],
    val hair: Int = HAIR_COLOURS[0],
) {
    enum class Style(val key: String, val label: String) {
        RACE("race", "Race kit"),
        CLASSIC("classic", "Classic tee"),
        TANK("tank", "Tank"),
        TIGHTS("tights", "Long sleeve + tights"),
        ;

        companion object {
            fun from(key: String?) = entries.firstOrNull { it.key == key } ?: RACE
        }
    }

    /** The coach page's URL parameters for this look, with [theme] standing in for a null top. */
    fun query(theme: Int): String = buildString {
        append("&style=").append(style.key)
        append("&color=").append(hex(top ?: theme))
        bottom?.let { append("&bottom=").append(hex(it)) }
        append("&skin=").append(hex(skin))
        append("&hair=").append(hex(hair))
    }

    companion object {
        private const val PREFS = "ofit_videos"
        val SKIN_TONES = listOf(0xF3D3BD, 0xE0AC8A, 0xC68C6A, 0xA0694B, 0x7A4B32, 0x4E3022)
        val HAIR_COLOURS = listOf(0x2B2018, 0x111111, 0x6B4423, 0xA86B32, 0xD9B46C, 0x9A9A9A)
        val CLOTH_COLOURS = listOf(
            0xF4F4F4, 0x24272E, 0xE53935, 0xFB8C00, 0xFDD835, 0x43A047,
            0x1E88E5, 0x3949AB, 0x8E24AA, 0xEC407A, 0x00ACC1, 0x6D4C41,
        )

        fun hex(rgb: Int) = String.format("%06x", rgb and 0xFFFFFF)

        fun load(context: Context): CoachLook {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun colour(key: String): Int? = if (p.contains(key)) p.getInt(key, 0) else null
            return CoachLook(
                style = Style.from(p.getString("coach_style", null)),
                top = colour("coach_top"),
                bottom = colour("coach_bottom"),
                skin = colour("coach_skin") ?: SKIN_TONES[2],
                hair = colour("coach_hair") ?: HAIR_COLOURS[0],
            )
        }

        fun save(context: Context, look: CoachLook) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                putString("coach_style", look.style.key)
                if (look.top == null) remove("coach_top") else putInt("coach_top", look.top)
                if (look.bottom == null) remove("coach_bottom") else putInt("coach_bottom", look.bottom)
                putInt("coach_skin", look.skin)
                putInt("coach_hair", look.hair)
            }.apply()
        }

        /** The name and voice given to the woman or man coach in the designer. */
        fun persona(context: Context, choice: CoachChoice): CoachPersona {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val who = if (choice == CoachChoice.MALE) "male" else "female"
            return CoachPersona(
                name = p.getString("coach_name_$who", null)?.takeIf { it.isNotBlank() } ?: CoachPersona.DEFAULT_NAME,
                voice = p.getString("coach_voice_$who", null),
            )
        }

        fun savePersona(context: Context, choice: CoachChoice, persona: CoachPersona) {
            val who = if (choice == CoachChoice.MALE) "male" else "female"
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                putString("coach_name_$who", CoachPersona.clean(persona.name))
                if (persona.voice == null) remove("coach_voice_$who") else putString("coach_voice_$who", persona.voice)
            }.apply()
        }

        /** Which coach (woman, man or off), the same setting the workout screen's coach menu uses. */
        fun choice(context: Context): CoachChoice =
            CoachChoice.fromName(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("coach", null))

        fun saveChoice(context: Context, choice: CoachChoice) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("coach", choice.name).apply()
        }
    }
}
