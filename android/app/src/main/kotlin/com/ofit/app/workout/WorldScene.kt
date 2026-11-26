package com.ofit.app.workout

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val TAG = "OfitWorld"
private const val HOST = "https://ofit.local/world/"
private const val THREE_HOST = "https://ofit.local/three/"

/**
 * The 3D trail (user idea 2026-11-24): an endless forest path drawn behind a workout that has no
 * video. The path ahead climbs with [inclinePercent] and the world moves at [speedKph] while
 * [running]. [seed] picks the forest, so a workout always gets the same one. The page and its
 * models come from the app's own assets (world/), and it never takes a touch.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldScene(
    speedKph: () -> Double,
    inclinePercent: () -> Double,
    running: () -> Boolean,
    seed: Int,
    modifier: Modifier = Modifier,
    /** Landscape, time of day and weather (user request 2026-11-25). */
    look: WorldLook = WorldLook("forest", "day", "clear"),
    /** The workout's distance in metres, for the trail map, and whether to show miles. */
    distanceMeters: Double = 5000.0,
    miles: Boolean = false,
    /** A GPX workout's real route outline ("x,y;x,y", see GpxRoute.outline) for the map, or null. */
    route: String? = null,
) {
    val webView = remember { arrayOfNulls<WebView>(1) }
    val currentSpeed by rememberUpdatedState(speedKph)
    val currentIncline by rememberUpdatedState(inclinePercent)
    val currentRunning by rememberUpdatedState(running)
    // Metres covered so far, kept here (outside the page) so that changing the scenery, which reloads the
    // page, does not send the trail map back to the start.
    var covered by remember { mutableStateOf(0.0) }
    LaunchedEffect(seed) {
        while (true) {
            if (currentRunning()) covered += currentSpeed() / 3.6 * 0.5
            webView[0]?.evaluateJavascript(
                "window.ofitWorld && window.ofitWorld(${currentSpeed()}, ${currentIncline()}, ${currentRunning()})",
                null,
            )
            delay(500)
        }
    }
    androidx.compose.runtime.key(seed, look) {
        AndroidView(
            factory = { context ->
                // In a FrameLayout, as the coach's WebView: on its own inside Compose it was not drawn.
                android.widget.FrameLayout(context).apply {
                    addView(
                        WebView(context).also { view ->
                            webView[0] = view
                            view.isFocusable = false
                            view.isClickable = false
                            view.settings.javaScriptEnabled = true
                            view.setBackgroundColor(0xFF9CC3E0.toInt())
                            view.webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                    Log.i(TAG, message.message())
                                    return true
                                }
                            }
                            view.webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val url = request.url.toString()
                                    // three.js is bundled with the app (assets/three/), so the page loads fast and offline.
                                    val path = when {
                                        url.startsWith(THREE_HOST) -> "three/" + url.removePrefix(THREE_HOST).substringBefore('?')
                                        url.startsWith(HOST) -> "world/" + url.removePrefix(HOST).substringBefore('?')
                                        else -> return null
                                    }
                                    val mime = when {
                                        path.endsWith(".html") -> "text/html"
                                        path.endsWith(".js") -> "text/javascript"
                                        path.endsWith(".glb") -> "model/gltf-binary"
                                        path.endsWith(".jpg") -> "image/jpeg"
                                        path.endsWith(".png") -> "image/png"
                                        path.endsWith(".hdr") -> "image/vnd.radiance"
                                        else -> "application/octet-stream"
                                    }
                                    return runCatching {
                                        WebResourceResponse(mime, "utf-8", v.context.assets.open(path)).apply {
                                            responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
                                        }
                                    }.getOrNull()
                                }
                            }
                            view.loadUrl(
                                HOST + "index.html?seed=$seed&land=${look.land}&time=${look.time}&weather=${look.weather}" +
                                    "&dist=${distanceMeters.toInt()}&units=${if (miles) "mi" else "km"}&at=${covered.toInt()}" +
                                    (route?.let { "&route=" + android.net.Uri.encode(it) } ?: "") +
                                    // Developer only (debug builds): effect overrides for tuning, e.g. "sh0,pr1".
                                    debugEffects(context),
                            )
                        },
                        android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
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

/** Debug builds only: the "world_fx" setting (adb) is passed on as the page's `fx` list, to try effect settings on a device. */
private fun debugEffects(context: android.content.Context): String {
    if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return ""
    val fx = context.getSharedPreferences("ofit_videos", android.content.Context.MODE_PRIVATE).getString("world_fx", null)
    return if (fx.isNullOrBlank()) "" else "&fx=" + android.net.Uri.encode(fx)
}
