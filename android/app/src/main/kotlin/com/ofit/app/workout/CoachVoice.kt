package com.ofit.app.workout

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * The coach's spoken voice (user request, 2026-11-24): the tablet's own text-to-speech, in the voice
 * picked in the coach designer (or a woman's or man's voice to match the coach), at the coach's own
 * volume. It plays over the video and the music without making them quieter: the old ducking was
 * too much (user report 2026-11-24), so the coach volume slider sets the balance instead.
 */
class CoachVoice(
    context: Context,
    private val male: Boolean,
    /** A voice name from [CoachVoices], or null for the best match for the coach. */
    private val voiceName: String? = null,
) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    @Volatile private var ready = false

    /** The coach's volume, 0..1, against the tablet's media volume. */
    @Volatile var volume: Float = 1f

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) setUp()
    }

    private fun setUp() {
        tts.setAudioAttributes(attributes)
        runCatching { tts.language = Locale.getDefault() }
        val all = runCatching { tts.voices }.getOrNull().orEmpty()
        val chosen = voiceName?.let { name -> all.firstOrNull { it.name == name } }
        val voice = chosen ?: CoachVoices.bestFor(all, male)
        voice?.let { tts.voice = it }
        // A chosen or matching voice is left as it sounds; without one, a lower or higher pitch
        // still tells the two coaches apart.
        tts.setPitch(if (voice != null) 1f else if (male) 0.9f else 1.08f)
        tts.setSpeechRate(1f)
        ready = true
        Log.i(TAG, "voice ready: ${tts.voice?.name}")
    }

    fun say(line: String) {
        if (!ready) return
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
        // Queued, never cut off or spoken over: a line waits for the one before it to finish.
        tts.speak(line, TextToSpeech.QUEUE_ADD, params, "coach-" + System.nanoTime())
    }

    /** Say [line] now, dropping anything still queued (the designer's "Hear it"). */
    fun sayNow(line: String): Boolean {
        if (!ready) return false
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
        tts.speak(line, TextToSpeech.QUEUE_FLUSH, params, "coach-" + System.nanoTime())
        return true
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }

    private companion object {
        const val TAG = "OfitCoach"
    }
}

/** The tablet's voices for the coach, with plain names for the coach designer. */
object CoachVoices {
    // Google's speech engine names its English voices with these codes.
    private val FEMALE_HINTS = listOf("-sfg-", "-tpc-", "-tpf-", "-iob-", "-iog-", "-gbc-", "-gba-", "-cxx-", "-fis-")
    private val MALE_HINTS = listOf("-iom-", "-iol-", "-tpd-", "-gbd-", "-rjs-", "-gbb-", "-jod-", "-gbg-")

    data class Option(val name: String, val label: String, val detail: String, val male: Boolean?)

    fun isMale(v: Voice): Boolean? = when {
        MALE_HINTS.any { v.name.contains(it, ignoreCase = true) } -> true
        FEMALE_HINTS.any { v.name.contains(it, ignoreCase = true) } -> false
        v.name.contains("female", true) -> false
        v.name.contains("male", true) -> true
        else -> null
    }

    /** Voices in the tablet's language, best sounding first, the coach's own kind before the others. */
    fun forCoach(voices: Collection<Voice>, male: Boolean): List<Voice> {
        val language = Locale.getDefault().language
        return voices
            .filter { it.locale.language == language && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(
                compareBy<Voice>({ if (isMale(it) == male) 0 else if (isMale(it) == null) 1 else 2 })
                    .thenByDescending { it.quality }
                    .thenBy { if (it.locale.country == Locale.getDefault().country) 0 else 1 }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenBy { it.name },
            )
    }

    /** The voice used when none is picked: the best of the coach's kind that works offline. */
    fun bestFor(voices: Collection<Voice>, male: Boolean): Voice? =
        forCoach(voices, male).firstOrNull { isMale(it) == male && !it.isNetworkConnectionRequired }
            ?: forCoach(voices, male).firstOrNull { isMale(it) == male }

    /** Plain names for the designer: "Woman 1 · US", with how it sounds and whether it needs the internet. */
    fun options(voices: Collection<Voice>, male: Boolean): List<Option> {
        val counts = mutableMapOf<String, Int>()
        return forCoach(voices, male).map { v ->
            val kind = when (isMale(v)) { true -> "Man"; false -> "Woman"; null -> "Voice" }
            val n = (counts[kind] ?: 0) + 1
            counts[kind] = n
            val country = v.locale.country.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            val quality = when {
                v.quality >= Voice.QUALITY_VERY_HIGH -> "most natural"
                v.quality >= Voice.QUALITY_HIGH -> "natural"
                else -> "standard"
            }
            Option(v.name, "$kind $n$country", quality + if (v.isNetworkConnectionRequired) ", needs internet" else "", isMale(v))
        }
    }
}
