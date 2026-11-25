package com.ofit.app.workout

/**
 * Who the coach is (user request 2026-11-24): the name the runner gave it, shown on its speech
 * bubble, and the tablet voice it speaks with (null: the best match for a woman or man coach).
 * Kept for the woman and the man coach separately.
 */
data class CoachPersona(
    val name: String = DEFAULT_NAME,
    val voice: String? = null,
) {
    companion object {
        const val DEFAULT_NAME = "Coach"
        const val MAX_NAME = 20

        /** A name as typed, tidied: trimmed, single spaces, at most [MAX_NAME] characters. */
        fun clean(typed: String): String =
            typed.replace(Regex("\\s+"), " ").trim().take(MAX_NAME).trim().ifEmpty { DEFAULT_NAME }
    }
}
