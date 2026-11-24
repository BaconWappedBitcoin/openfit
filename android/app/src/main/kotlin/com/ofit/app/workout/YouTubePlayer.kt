package com.ofit.app.workout

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * Plays a YouTube video with YouTube's own embedded player.
 *
 * Nothing is downloaded and no stream address is extracted: the page is YouTube's published
 * IFrame player, loaded as YouTube documents it, so the video arrives with whatever YouTube
 * puts around it (including adverts, and the owner's choice of whether it may be embedded).
 *
 * It follows the same two values as [LocalVideoPlayer]: whether the program is running, and
 * where in the program the play head should be. The picture is scaled to cover the screen and
 * cropped, never stretched.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    videoId: String,
    playing: Boolean,
    positionMillis: () -> Int = { 0 },
    modifier: Modifier = Modifier,
    /** Bump to jump exactly to [positionMillis] now, even while paused (the editor's scrubbing). */
    seekRequest: Int = 0,
    /** The main video's own position in seconds, about twice a second (it stands still during ads). */
    onVideoPosition: ((Double) -> Unit)? = null,
    /** Playback speed (YouTube allows 0.25 to 2). */
    rate: Float = 1f,
    /** Volume 0..100; 0 is muted. */
    volume: Int = 100,
    /**
     * Something outside the page paused the video while it should play: another app (a music app)
     * took the audio. The page has already muted the video and set it playing again, so the
     * workout's picture keeps moving and the music keeps playing; this lets the screen show muted.
     */
    onTakenOver: (() -> Unit)? = null,
) {
    val currentPosition by rememberUpdatedState(positionMillis)
    val currentOnPosition by rememberUpdatedState(onVideoPosition)
    val currentPlaying by rememberUpdatedState(playing)
    val currentOnTakenOver by rememberUpdatedState(onTakenOver)
    val initialVolume = remember { volume }
    val webView = remember { arrayOfNulls<WebView>(1) }

    AndroidView(
        factory = { context ->
            WebView(context).also { view ->
                webView[0] = view
                view.setBackgroundColor(Color.BLACK)
                view.settings.javaScriptEnabled = true
                view.settings.mediaPlaybackRequiresUserGesture = false
                if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }
                view.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                        Log.w(TAG, "load failed: ${request.url} ${error.errorCode} ${error.description}")
                    }
                    override fun onPageFinished(v: WebView, url: String?) {
                        Log.i(TAG, "page finished: $url")
                        v.evaluateJavascript("ofitPlaying($currentPlaying)", null)
                    }
                }
                view.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.i(TAG, message.message())
                        return true
                    }
                }
                // The page reports the video's own position, so an ad (when it stands still) can hold the workout.
                view.addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun position(seconds: Double) {
                            view.post { currentOnPosition?.invoke(seconds) }
                        }

                        @android.webkit.JavascriptInterface
                        fun takenOver() {
                            Log.i(TAG, "video paused from outside (audio taken): muted and resumed")
                            view.post { currentOnTakenOver?.invoke() }
                        }
                    },
                    "OfitBridge",
                )
                // YouTube wants to know who is embedding it: an https origin of our own, sent as the referrer.
                view.loadDataWithBaseURL(EMBEDDER_ORIGIN, playerPage(videoId, initialVolume), "text/html", "utf-8", null)
            }
        },
        modifier = modifier,
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val currentVolume by rememberUpdatedState(volume)
    LaunchedEffect(playing) {
        // Starting the video makes Android pause the runner's music, even with the video muted
        // (audio focus log, 2026-11-24). With the video's sound off, set the music playing again:
        // the video then loses the audio and carries on muted (see onStateChange in the page).
        val musicWasPlaying = playing && currentVolume <= 0 && runCatching { OutsideMusic.isPlaying(context) }.getOrDefault(false)
        webView[0]?.evaluateJavascript("ofitPlaying($playing)", null)
        if (musicWasPlaying) {
            repeat(3) {
                delay(1_200)
                if (!OutsideMusic.isPlaying(context)) OutsideMusic.resume(context)
            }
        }
    }

    // Keep the picture in step with the program's clock. The page only moves the video when it
    // has drifted, for the same reason the local player does: a seek per tick makes it stutter.
    LaunchedEffect(playing) {
        while (playing) {
            delay(SYNC_INTERVAL_MILLIS)
            webView[0]?.evaluateJavascript("ofitSync(${currentPosition() / 1000.0})", null)
        }
    }

    LaunchedEffect(rate) { webView[0]?.evaluateJavascript("ofitRate($rate)", null) }
    LaunchedEffect(volume) { webView[0]?.evaluateJavascript("ofitVolume($volume)", null) }
    LaunchedEffect(seekRequest) {
        if (seekRequest > 0) webView[0]?.evaluateJavascript("ofitSeek(${currentPosition() / 1000.0})", null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView[0]?.destroy()
            webView[0] = null
        }
    }
}

private const val TAG = "YouTubePlayer"
private const val EMBEDDER_ORIGIN = "https://com.ofit.app"
private const val SYNC_INTERVAL_MILLIS = 1000L

/** The page that hosts the player. Scaled to cover the screen, controls hidden. */
private fun playerPage(videoId: String, initialVolume: Int = 100): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  html,body{margin:0;height:100%;background:#000;overflow:hidden}
  #p{position:absolute;left:0;top:0;border:0;pointer-events:none}
</style></head><body><div id="p"></div>
<script>window.onerror=function(m,u,l){console.log("js error: "+m+" "+u+":"+l);};</script>
<script>var player=null,ready=false,wantPlaying=false,DRIFT=3,wantRate=1,wantVol=$initialVolume,lastResume=0;
function ofitRate(r){wantRate=r;if(ready)player.setPlaybackRate(r);}
function ofitVolume(v){wantVol=v;if(!ready)return;if(v<=0){player.mute();}else{player.unMute();player.setVolume(v);}}
function ofitFit(){var el=document.getElementById('p');if(!el)return;
  var w=window.innerWidth,h=window.innerHeight,cw=Math.max(w,h*16/9),ch=Math.max(h,w*9/16);
  el.style.width=cw+'px';el.style.height=ch+'px';el.style.left=((w-cw)/2)+'px';el.style.top=((h-ch)/2)+'px';}
window.addEventListener('resize',ofitFit);
function ofitPlaying(p){wantPlaying=p;if(!ready)return;if(p)player.playVideo();else player.pauseVideo();}
function ofitSync(sec){if(!ready)return;if(Math.abs(player.getCurrentTime()-sec)>DRIFT)player.seekTo(sec,true);}
function ofitSeek(sec){if(!ready)return;player.seekTo(sec,true);}
setInterval(function(){if(ready&&window.OfitBridge){try{OfitBridge.position(player.getCurrentTime()||0);}catch(e){}}},500);
// Watchdog (user report 2026-11-24: starting music froze the video): a music app can pause the
// video twice in a row, and the second pause fell inside onStateChange's 3 s guard and stuck.
// Every 2 s, a video that should play but sits paused is muted and played again.
setInterval(function(){if(!ready||!wantPlaying)return;try{if(player.getPlayerState()===2){
  player.mute();wantVol=0;player.playVideo();if(window.OfitBridge){try{OfitBridge.takenOver();}catch(x){}}}}catch(e){}},2000);</script>
<script src="https://www.youtube.com/iframe_api" onerror="console.log('iframe_api failed to load')"></script>
<script>
function onYouTubeIframeAPIReady(){console.log('api ready');ofitFit();
  player=new YT.Player('p',{videoId:'$videoId',
    playerVars:{mute:(wantVol<=0?1:0),controls:0,disablekb:1,fs:0,modestbranding:1,playsinline:1,rel:0,iv_load_policy:3,origin:'$EMBEDDER_ORIGIN'},
    events:{onReady:function(){console.log('player ready');ofitFit();ready=true;ofitRate(wantRate);ofitVolume(wantVol);if(wantPlaying)player.playVideo();},
            onError:function(e){console.log('player error '+e.data);},
            onStateChange:function(e){
              // Paused, but not by us: Android paused it because another app (music) took the
              // audio. Mute it (so it never takes the audio back) and play on, at most every 3 s.
              if(e.data===2&&wantPlaying&&Date.now()-lastResume>3000){lastResume=Date.now();
                player.mute();wantVol=0;player.playVideo();
                if(window.OfitBridge){try{OfitBridge.takenOver();}catch(x){}}}}}});
}
</script></body></html>
""".trimIndent()

/**
 * The video id in whatever the user pasted: a full address, a short one, or the bare id.
 * Returns null when it is not one, so the caller can say so instead of loading nothing.
 */
fun parseYouTubeId(text: String): String? {
    val input = text.trim()
    if (Regex("[A-Za-z0-9_-]{11}").matches(input)) return input
    val match = Regex("""(?:v=|youtu\.be/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})""").find(input)
    return match?.groupValues?.get(1)
}
