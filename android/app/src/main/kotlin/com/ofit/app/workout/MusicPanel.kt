package com.ofit.app.workout

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Music from other apps during a workout (user request): open a music app, play / pause / skip it,
 * and set its volume. Nothing is read from those apps and no account is needed.
 */
object OutsideMusic {

    /** Well-known music apps, by package, shown first when installed. */
    private val KNOWN = listOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.amazon.mp3" to "Amazon Music",
        "com.pandora.android" to "Pandora",
        "com.apple.android.music" to "Apple Music",
        "deezer.android.app" to "Deezer",
        "com.aspiro.tidal" to "Tidal",
        "com.soundcloud.android" to "SoundCloud",
        "com.audible.application" to "Audible",
    )

    private const val PREFS = "ofit_music"
    private const val KEY_APP = "app"

    data class App(val packageName: String, val label: String)

    /** Installed music apps: the known ones, then any other app that says it is a music player. */
    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        val known = KNOWN.filter { (pkg, _) -> pm.getLaunchIntentForPackage(pkg) != null }.map { (pkg, label) -> App(pkg, label) }
        val others = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), 0)
                .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        }.getOrDefault(emptyList())
        return (known + others).distinctBy { it.packageName }.filterNot { it.packageName == context.packageName }
    }

    /** The music app last opened from here, or null. */
    fun lastApp(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APP, null)

    fun open(context: Context, app: App) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_APP, app.packageName).apply()
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(it) }
        }
    }

    /**
     * Send a media key (play/pause, next, previous) to the music app. Sent straight to the app last
     * opened from here: a general media key goes to whichever player Android thinks is current,
     * which during a workout can be the workout's own video, so play/pause seemed not to work
     * (user report, 2026-11-24). With no app chosen yet, it falls back to the general key.
     */
    fun key(context: Context, keyCode: Int) {
        // Best: the playing app's own controls, when the runner has allowed OpenFit to see them.
        controller(context)?.let { c ->
            val controls = c.transportControls
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                    if (c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
                KeyEvent.KEYCODE_MEDIA_NEXT -> controls.skipToNext()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> controls.skipToPrevious()
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        val pkg = lastApp(context)
        if (pkg != null) {
            listOf(down, up).forEach { event ->
                val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg).putExtra(Intent.EXTRA_KEY_EVENT, event)
                runCatching { context.sendOrderedBroadcast(intent, null) }
            }
            return
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.dispatchMediaKeyEvent(down)
        audio.dispatchMediaKeyEvent(up)
    }

    /** Media volume 0..1 (the music app's volume; the video's own slider sits on top of it). */
    fun volume(context: Context): Float {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0f
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(context: Context, share: Float) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val index = Math.round(share.coerceIn(0f, 1f) * max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
    }

    /** Whether the runner has let OpenFit see what is playing (Android notification access). */
    fun hasAccess(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Android's page where the runner can allow it. */
    fun openAccessSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** The music app's session: the one playing, else the app opened from here, else any. */
    fun controller(context: Context): android.media.session.MediaController? {
        if (!hasAccess(context)) return null
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager ?: return null
        val sessions = runCatching {
            manager.getActiveSessions(android.content.ComponentName(context, MusicListenerService::class.java))
        }.getOrNull().orEmpty().filter { it.packageName != context.packageName }
        return sessions.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.packageName == lastApp(context) }
            ?: sessions.firstOrNull()
    }

    /** Whether the music app is playing right now (needs notification access; false without it). */
    fun isPlaying(context: Context): Boolean =
        controller(context)?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

    /** Set the music app playing again. */
    fun resume(context: Context) {
        controller(context)?.transportControls?.play()
    }

    /**
     * Pause the music app if it is playing (user request 2026-11-24: music stops when the workout
     * does). With access to the playing app's controls, its own pause; otherwise a media pause key,
     * which pauses and never starts anything.
     */
    fun pause(context: Context) {
        val c = controller(context)
        if (c != null) {
            if (c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) c.transportControls.pause()
            return
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        if (!audio.isMusicActive) return
        val now = android.os.SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
    }

    data class NowPlaying(
        val title: String,
        val artist: String?,
        val app: String,
        val playing: Boolean,
        val art: android.graphics.Bitmap?,
    )

    /** The song in the music app, or null when nothing is known (no access, or no music app). */
    fun nowPlaying(context: Context): NowPlaying? {
        val c = controller(context) ?: return null
        val meta = c.metadata ?: return null
        val title = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val art = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val label = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)
        return NowPlaying(title, artist, label, c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING, art)
    }
}

/** What is playing in the music app, checked every second and a half while shown. */
@androidx.compose.runtime.Composable
fun rememberNowPlaying(): OutsideMusic.NowPlaying? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<OutsideMusic.NowPlaying?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            state.value = runCatching { OutsideMusic.nowPlaying(context) }.getOrNull()
            kotlinx.coroutines.delay(1500)
        }
    }
    return state.value
}
