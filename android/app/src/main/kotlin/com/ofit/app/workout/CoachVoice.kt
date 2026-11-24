package com.ofit.app.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * The coach's spoken voice (user request, 2026-11-24): the tablet's own text-to-speech, a female or
 * male voice to match the coach, and music ducked (made quieter) while it speaks.
 */
class CoachVoice(context: Context, private val male: Boolean) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) setUp()
    }

    private fun setUp() {
        tts.setAudioAttributes(attributes)
        runCatching { tts.language = Locale.getDefault() }
        pickVoice()?.let { tts.voice = it }
        // No voice of the right kind installed: a lower or higher pitch still tells them apart.
        tts.setPitch(if (male) 0.9f else 1.08f)
        tts.setSpeechRate(1.02f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { audio.abandonAudioFocusRequest(focus) }
            @Deprecated("old callback") override fun onError(id: String?) { audio.abandonAudioFocusRequest(focus) }
        })
        ready = true
        Log.i(TAG, "voice ready: ${tts.voice?.name}")
    }

    /** A voice of the asked-for kind in the device language, preferring ones that need no network. */
    private fun pickVoice(): Voice? {
        val language = Locale.getDefault().language
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == language && !it.isNetworkConnectionRequired }
        val names = if (male) MALE_HINTS else FEMALE_HINTS
        return voices.firstOrNull { v -> names.any { v.name.contains(it, ignoreCase = true) } }
            ?: voices.firstOrNull { v -> v.name.contains(if (male) "male" else "female", ignoreCase = true) && !(male && v.name.contains("female", true)) }
    }

    fun say(line: String) {
        if (!ready) return
        audio.requestAudioFocus(focus)
        // Queued, never cut off or spoken over: a line waits for the one before it to finish.
        tts.speak(line, TextToSpeech.QUEUE_ADD, null, "coach-" + System.nanoTime())
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        audio.abandonAudioFocusRequest(focus)
    }

    private companion object {
        const val TAG = "OfitCoach"
        // Google's speech engine names its English voices with these codes.
        val FEMALE_HINTS = listOf("-sfg-", "-tpc-", "-tpf-", "-iob-", "-iog-", "-gbc-", "-gba-")
        val MALE_HINTS = listOf("-iom-", "-iol-", "-tpd-", "-gbd-", "-rjs-", "-gbb-")
    }
}
