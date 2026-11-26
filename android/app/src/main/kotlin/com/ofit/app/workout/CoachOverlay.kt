package com.ofit.app.workout

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ofit.protocol.CoachTalk
import com.ofit.protocol.WorkoutEngineSnapshot
import kotlinx.coroutines.delay

/** Which coach runs with you, or none. Saved with the video settings. */
enum class CoachChoice(val label: String) {
    OFF("Off"),
    FEMALE("Coach (woman)"),
    MALE("Coach (man)"),
    ;

    companion object {
        fun fromName(name: String?): CoachChoice = entries.firstOrNull { it.name == name } ?: OFF
    }
}

private const val TAG = "OfitCoach"
private const val HOST = "https://ofit.local/coach/"
private const val THREE_HOST = "https://ofit.local/three/"

/**
 * The virtual coach (user idea, 2026-11-24): a 3D runner over the video that walks, jogs or sprints
 * with the belt, stands when paused, and says a short line now and then. It sits between the video
 * and the controls and never takes a touch, so every button works as before.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CoachOverlay(
    choice: CoachChoice,
    snapshot: WorkoutEngineSnapshot,
    speedKph: Double,
    modifier: Modifier = Modifier,
    /** Say the lines out loud as well as showing them. */
    speaks: Boolean = true,
    /** The workout's YouTube video, for facts about the place it was filmed. */
    videoId: String? = null,
    /** The workout's usual pace, to tell how hard a step is. */
    referenceKph: Double? = null,
    /** How fast the legs move against the speed (Coach pace); walking or running follows the speed alone. */
    pace: Float = 1f,
    /** How big the coach is drawn (1 = 360 x 456 dp). */
    scale: Float = 1f,
    /** Outfit, colours, skin and hair, from the coach designer. */
    look: CoachLook = CoachLook(),
    /** Show what the coach says as captions (separate from [speaks]). */
    captions: Boolean = true,
    /** The spoken voice's volume, 0..1 against the media volume. */
    voiceVolume: Float = 1f,
    /** The coach's name and voice, from the coach designer. */
    persona: CoachPersona = CoachPersona(),
    /** Warn about coming speed and incline changes (off with no treadmill). */
    changeTips: Boolean = true,
    /** Told the caption to show ("Name: line"), or null; drawn by the workout screen above its controls. */
    onCaption: (String?) -> Unit = {},
) {
    if (choice == CoachChoice.OFF) return
    val units = com.ofit.app.settings.LocalUnits.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val talk = remember(units) {
        CoachTalk(speedText = { kph ->
            val v = units.speedFromKph(kph)
            "%.1f %s".format(java.util.Locale.ROOT, v, if (units == com.ofit.protocol.UnitSystem.IMPERIAL) "miles per hour" else "kilometres per hour")
        })
    }
    talk.referenceKph = referenceKph
    talk.changeTips = changeTips
    // The spoken voice, matching the coach; let go when the coach or the screen goes away.
    val voice = remember(choice, speaks, persona.voice) {
        if (speaks) CoachVoice(context, male = choice == CoachChoice.MALE, voiceName = persona.voice) else null
    }
    voice?.volume = voiceVolume
    DisposableEffect(voice) { onDispose { voice?.shutdown() } }
    val currentVoice by rememberUpdatedState(voice)
    // Facts about the place in the video, from the AI, kept on the tablet once fetched.
    LaunchedEffect(videoId) {
        if (videoId == null) return@LaunchedEffect
        val prefs = context.getSharedPreferences("ofit_coach_facts20", android.content.Context.MODE_PRIVATE)
        val cached = prefs.getString(videoId, null)
        talk.facts = if (cached != null) {
            cached.split("\n").filter { it.isNotBlank() }
        } else {
            runCatching { (com.ofit.app.library.LibraryApi.shared ?: com.ofit.app.library.LibraryApi(installId = { "coach" })).facts(videoId) }
                .onFailure { Log.w(TAG, "facts: ${it.message}") }
                .getOrDefault(emptyList())
                .also { if (it.isNotEmpty()) prefs.edit().putString(videoId, it.joinToString("\n")).apply() }
        }
        Log.i(TAG, "facts for $videoId: ${talk.facts.size}")
    }
    // Landmarks along the video, from one low-detail AI read of it, kept on the tablet once found.
    LaunchedEffect(videoId) {
        if (videoId == null) return@LaunchedEffect
        val prefs = context.getSharedPreferences("ofit_coach_landmarks", android.content.Context.MODE_PRIVATE)
        val cached = prefs.getString(videoId, null)
        talk.landmarks = if (cached != null) {
            runCatching {
                val arr = org.json.JSONArray(cached)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    com.ofit.protocol.CoachTalk.Landmark(o.getInt("seconds"), o.getString("name"), o.optString("fact"))
                }
            }.getOrDefault(emptyList())
        } else {
            runCatching { (com.ofit.app.library.LibraryApi.shared ?: com.ofit.app.library.LibraryApi(installId = { "coach" })).landmarks(videoId) }
                .onFailure { Log.w(TAG, "landmarks: ${it.message}") }
                .getOrNull()
                ?.also { found ->
                    val arr = org.json.JSONArray()
                    found.forEach { arr.put(org.json.JSONObject().put("seconds", it.seconds).put("name", it.name).put("fact", it.fact)) }
                    prefs.edit().putString(videoId, arr.toString()).apply()
                }
                .orEmpty()
        }
        Log.i(TAG, "landmarks for $videoId: ${talk.landmarks.joinToString { "${it.seconds}s ${it.name}" }}")
    }
    var line by remember { mutableStateOf<String?>(null) }
    val currentSnapshot by rememberUpdatedState(snapshot)
    val currentSpeed by rememberUpdatedState(speedKph)
    val currentPace by rememberUpdatedState(pace)

    // Talk: checked once a second; a line stays up long enough to read (about 6 s, longer for long lines).
    LaunchedEffect(choice) {
        talk.reset()
        var shownAt = 0L
        var showFor = 0L
        while (true) {
            val now = System.currentTimeMillis()
            talk.update(currentSnapshot, now)?.let { said ->
                line = said
                shownAt = now
                showFor = captionMillis(said)
                Log.i(TAG, "says: $said")
                currentVoice?.say(said)
            }
            if (line != null && now - shownAt > showFor) line = null
            delay(1_000)
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Captions are drawn by the workout screen just above its bottom controls (user request
        // 2026-11-24: a bubble over a big coach ran up under the top of the screen).
        val said = line
        val shown = if (captions && said != null) "${persona.name}: $said" else null
        val currentOnCaption by rememberUpdatedState(onCaption)
        LaunchedEffect(shown) { currentOnCaption(shown) }
        DisposableEffect(Unit) { onDispose { currentOnCaption(null) } }
        CoachFigure(
            choice = choice,
            look = look,
            speedKph = { currentSpeed },
            running = { currentSnapshot.isRunning },
            pace = { currentPace },
            modifier = Modifier.size(width = (360 * scale).dp, height = (456 * scale).dp), // sized by the Coach size slider
        )
    }
}

/** How long a line stays on screen: 6 s, plus about a third of a second a word past 15 words, up to 16 s. */
internal fun captionMillis(line: String): Long {
    val words = line.split(Regex("\\s+")).count { it.isNotBlank() }
    return (6_000L + (words - 15).coerceAtLeast(0) * 330L).coerceAtMost(16_000L)
}


/**
 * The 3D coach on its own:a WebView with the coach page, dressed in [look], told the speed, whether
 * to run, and the stride pace twice a second. Used over the workout video and in the coach designer.
 * Keyed on the coach and the look, so a change shows at once.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CoachFigure(
    choice: CoachChoice,
    look: CoachLook,
    speedKph: () -> Double,
    running: () -> Boolean,
    pace: () -> Float = { 1f },
    modifier: Modifier = Modifier,
) {
    if (choice == CoachChoice.OFF) return
    val webView = remember { arrayOfNulls<WebView>(1) }
    val theme = androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF
    val currentSpeed by rememberUpdatedState(speedKph)
    val currentRunning by rememberUpdatedState(running)
    val currentPace by rememberUpdatedState(pace)
    LaunchedEffect(choice, look) {
        while (true) {
            webView[0]?.evaluateJavascript(
                "window.ofitCoach && window.ofitCoach(${currentSpeed()}, ${currentRunning()}, ${currentPace()})",
                null,
            )
            delay(500)
        }
    }
    // The WebView sits in a FrameLayout: on its own inside Compose it was never drawn (seen 2026-11-24).
    androidx.compose.runtime.key(choice, look) {
        AndroidView(
            factory = { context ->
                android.widget.FrameLayout(context).apply { addView(WebView(context).also { view ->
                    webView[0] = view
                    view.setBackgroundColor(AndroidColor.TRANSPARENT)
                    view.isFocusable = false
                    view.settings.javaScriptEnabled = true
                    view.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            Log.i(TAG, message.message())
                            return true
                        }
                    }
                    view.webViewClient = object : WebViewClient() {
                        // The coach's page and models come from the app itself.
                        override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val url = request.url.toString()
                            // three.js is bundled with the app (assets/three/), so the page loads fast and offline.
                            val path = when {
                                url.startsWith(THREE_HOST) -> "three/" + url.removePrefix(THREE_HOST).substringBefore('?')
                                url.startsWith(HOST) -> "coach/" + url.removePrefix(HOST).substringBefore('?')
                                else -> return null
                            }
                            val mime = when {
                                path.endsWith(".html") -> "text/html"
                                path.endsWith(".js") -> "text/javascript"
                                path.endsWith(".gltf") -> "model/gltf+json"
                                path.endsWith(".glb") -> "model/gltf-binary"
                                path.endsWith(".png") -> "image/png"
                                else -> "application/octet-stream"
                            }
                            return runCatching {
                                WebResourceResponse(mime, "utf-8", v.context.assets.open(path)).apply {
                                    responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
                                }
                            }.getOrNull()
                        }
                    }
                    view.loadUrl(HOST + "index.html?who=" + (if (choice == CoachChoice.MALE) "male" else "female") + look.query(theme))
                }, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)) }
            },
            onRelease = { frame ->
                val old = (frame as? android.widget.FrameLayout)?.getChildAt(0) as? WebView
                // Only forget the page being released: when the look changes, the new page is made
                // before the old one goes, and clearing it froze the world (user report 2026-11-25).
                if (webView[0] === old) webView[0] = null
                old?.destroy()
            },
            modifier = modifier,
        )
    }
}
