package com.ofit.app.workout

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Plays a video file that is already on the device.
 *
 * The platform player is used rather than a third party one: it is enough for a local file, and
 * it keeps the app free of extra dependencies. The file is the user's own, picked with the
 * system file chooser; nothing is copied from anywhere and nothing protected is touched.
 *
 * The video is optional. A program runs perfectly well without one.
 *
 * This composable owns the player, and the screen tells it what to do with two plain values:
 * whether the program is running, and where in the program the play head should be. The screen
 * does not hold a player of its own: two objects that each think they own the player is how a
 * video ends up attached and never told to play.
 *
 * The picture is drawn with a texture rather than with a plain video view, because the picture
 * has to be cropped to the shape of the screen rather than stretched to it. A running video is
 * 16:9 and the phone in landscape is wider than that; stretching it would make the horizon
 * bend, and a runner notices that at once. The texture is scaled up until it covers the screen
 * and the overflow is cut off at the edges, which is what a television does with a wide picture.
 */
@Composable
fun LocalVideoPlayer(
    uri: Uri?,
    playing: Boolean,
    positionMillis: () -> Int = { 0 },
    modifier: Modifier = Modifier,
    /** Bump to jump exactly to [positionMillis] now, even while paused (the editor's scrubbing). */
    seekRequest: Int = 0,
) {
    val holder = remember { VideoHolder() }
    androidx.compose.runtime.LaunchedEffect(seekRequest) {
        if (seekRequest > 0) holder.seekExactly(positionMillis())
    }
    val currentPlaying by rememberUpdatedState(playing)
    val currentPosition by rememberUpdatedState(positionMillis)
    val context = LocalContext.current

    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).also { view ->
                holder.view = view
                view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        holder.open(context, texture)
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        holder.cropToScreen()
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        holder.closePlayer()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier,
        update = { view ->
            holder.view = view
            if (uri != null && holder.attachedUri != uri) {
                holder.attachedUri = uri
                holder.pendingUri = uri
                holder.restart(context)
            }
        },
    )

    // The video plays while the program runs, and stops when it does not.
    LaunchedEffect(playing, uri) {
        holder.wantPlaying = playing
        if (playing) holder.play() else holder.pause()
    }

    // Keep the picture in step with the program's clock, so a skipped step skips the video too.
    LaunchedEffect(playing, uri) {
        while (playing) {
            delay(SEEK_INTERVAL_MILLIS)
            holder.syncTo(currentPosition())
        }
    }

    DisposableEffect(Unit) {
        onDispose { holder.closePlayer() }
    }
}

/** How often the play head is put back in step with the program's clock. */
private const val SEEK_INTERVAL_MILLIS = 1000L

/** How far the picture may wander from the program's clock before it is moved. */
private const val MAX_DRIFT_MILLIS = 3000

/** The name used in the log. */
private const val TAG = "LocalVideoPlayer"

/**
 * Holds the platform player so the screen can drive it.
 */
class VideoHolder {

    /** The view the picture is drawn into. */
    var view: TextureView? = null

    /** The player, once the surface exists. */
    var player: MediaPlayer? = null

    /** The file the player is showing. */
    var attachedUri: Uri? = null

    /** The file to open when the surface becomes available. */
    var pendingUri: Uri? = null

    /** Whether the file has been opened and can be played. */
    var ready: Boolean = false

    /** What the screen last asked for. */
    var wantPlaying: Boolean = false

    /** The size of the picture, once the player has reported it. */
    var videoWidth: Int = 0
    var videoHeight: Int = 0

    /**
     * Open the file, now that there is a surface to draw it on.
     *
     * A texture has no surface until the platform has laid it out, so this is called when the
     * surface appears rather than when the file is chosen.
     */
    fun open(context: Context, texture: SurfaceTexture) {
        closePlayer()

        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setSurface(Surface(texture))
        mediaPlayer.setOnPreparedListener { prepared ->
            prepared.isLooping = false
            ready = true
            cropToScreen()
            if (wantPlaying) prepared.start()
        }
        mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
            videoWidth = width
            videoHeight = height
            cropToScreen()
        }
        mediaPlayer.setOnErrorListener { _, what, extra ->
            ready = false
            Log.w(TAG, "The video could not be played (what=$what, extra=$extra).")
            true
        }

        val uri = pendingUri ?: attachedUri
        if (uri == null) return
        try {
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.prepareAsync()
        } catch (error: Exception) {
            ready = false
            Log.w(TAG, "The video could not be opened.", error)
        }
    }

    /** Open the file again, after it has changed. */
    fun restart(context: Context) {
        val texture = view?.surfaceTexture ?: return
        open(context, texture)
    }

    /** Scale the picture up until it covers the screen, and cut off the overflow. */
    fun cropToScreen() {
        val view = view ?: return
        if (videoWidth <= 0 || videoHeight <= 0) return
        val viewWidth = view.width.toFloat()
        val viewHeight = view.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val scale = max(viewWidth / videoWidth, viewHeight / videoHeight)
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            (viewWidth - videoWidth * scale) / 2f,
            (viewHeight - videoHeight * scale) / 2f,
        )
        view.setTransform(matrix)
    }

    /** Start playing, if the file is ready. */
    fun play() {
        if (ready) player?.start()
    }

    /** Stop playing, if the file is ready. */
    fun pause() {
        if (ready) player?.pause()
    }

    /**
     * Put the play head back in step with the program, but only if it has drifted.
     *
     * Seeking on every tick is wrong: a seek lands on a keyframe, and a downloaded video has one
     * every few seconds, so a seek once a second throws the picture back to the last keyframe
     * each time and the first moments of the video repeat. A video that is already playing
     * normally stays in step by itself; it is only moved when it is clearly out (a skipped step,
     * a pause, a resume), and then to the exact frame.
     */
    fun syncTo(millis: Int) {
        val mediaPlayer = player ?: return
        if (!ready) return
        val drift = kotlin.math.abs(mediaPlayer.currentPosition - millis)
        if (drift < MAX_DRIFT_MILLIS) return
        mediaPlayer.seekTo(millis.toLong(), MediaPlayer.SEEK_CLOSEST)
    }

    /** Jump to an exact position, playing or not. */
    fun seekExactly(millis: Int) {
        val mediaPlayer = player ?: return
        if (!ready) return
        mediaPlayer.seekTo(millis.toLong(), MediaPlayer.SEEK_CLOSEST)
    }

    /** Let go of the player. */
    fun closePlayer() {
        ready = false
        player?.let { mediaPlayer ->
            runCatching {
                mediaPlayer.setOnPreparedListener(null)
                mediaPlayer.setOnVideoSizeChangedListener(null)
                mediaPlayer.setOnErrorListener(null)
                mediaPlayer.reset()
                mediaPlayer.release()
            }
        }
        player = null
    }
}