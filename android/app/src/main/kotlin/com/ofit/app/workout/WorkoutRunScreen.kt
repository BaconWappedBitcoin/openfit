package com.ofit.app.workout

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ofit.app.data.SavedWorkout
import com.ofit.protocol.ConnectionState
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.TreadmillState
import com.ofit.protocol.WorkoutEngineSnapshot
import com.ofit.protocol.WorkoutEngineState

/**
 * Run a workout.
 *
 * The program drives the treadmill; the optional video is the user's own file, played alongside.
 * There is nothing to download and nothing to sign in to: the program is on the phone and the
 * machine is over Bluetooth.
 *
 * The screen is the heads-up display: the video fills it and the numbers sit on top of it, so the
 * runner sees the scenery and the pace at the same time. Everything on it is drawn by [WorkoutHud].
 *
 * A preview runs the same program without a machine, so the display and the video can be seen
 * before stepping onto the treadmill. It is offered only when there is no machine to run on, and
 * it is labelled as a preview wherever it is shown.
 */
@Composable
fun WorkoutRunScreen(
    saved: SavedWorkout,
    snapshot: WorkoutEngineSnapshot,
    state: TreadmillState,
    limits: SafetyLimits = SafetyLimits.DEFAULT,
    preview: Boolean,
    onPreviewChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onAbandon: () -> Unit,
    onOverrideSpeed: (Double) -> Unit = {},
    onOverrideIncline: (Double) -> Unit = {},
    onResetToProgram: () -> Unit = {},
    onResetSpeed: () -> Unit = onResetToProgram,
    onResetIncline: () -> Unit = onResetToProgram,
    onEmergencyStop: () -> Unit,
    onBack: () -> Unit,
    heartRate: com.ofit.app.heart.ShownHeartRate? = null,
    heartSummary: com.ofit.app.heart.HeartRateSummary? = null,
    tiles: List<HudStat> = HudStat.DEFAULT,
    onTilesChange: (List<HudStat>) -> Unit = {},
    swapSides: Boolean = false,
    onSwapSides: () -> Unit = {},
    coachStatus: com.ofit.app.heart.CoachStatus? = null,
    coachNotice: String? = null,
    onCoachStart: (Int) -> Unit = {},
    onCoachStop: () -> Unit = {},
    coachLevel: String = "Normal",
    onCoachLevel: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val videoPrefs = remember { context.getSharedPreferences("ofit_videos", android.content.Context.MODE_PRIVATE) }

    // A YouTube video, if one was chosen instead of a file. It plays in YouTube's own player.
    var youtubeId by remember(saved.id) { mutableStateOf(videoPrefs.getString("yt:" + saved.id, null)) }
    var choosingVideo by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf(false) }

    // The video chosen for this workout last time is loaded again, so it only has to be picked once.
    var videoUri by remember(saved.id) {
        mutableStateOf(videoPrefs.getString(saved.id, null)?.let(Uri::parse))
    }
    val current by rememberUpdatedState(snapshot)
    val engineMessage = snapshot.message

    // Whether the program is running right now. The video follows this: it plays while the
    // program runs and stops when it does not.
    val running = snapshot.state == WorkoutEngineState.RUNNING

    // In a preview there is no machine, so the numbers shown are the program's own. The machine's
    // own state is left alone: a preview never pretends that a machine is running (FR-013).
    val displayState = if (preview) previewMachineState(saved.program, snapshot) else state

    // The workout screen turns the phone sideways, and only while it is showing. The rest of the
    // app stays upright, where its lists read well, but a workout is watched from a phone propped
    // up on the console, and the video is wider than it is tall.
    SideEffect {
        (context as? Activity)?.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // A workout is watched from a tablet on the console and nobody touches it: keep it awake
    // while this screen shows.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Fullscreen: the video goes edge to edge, and the navigation
        // strip is gone, so a tap near the bottom can no longer send the tablet Home mid-workout
        // (it did in testing). A swipe from the edge shows the bars for a moment.
        val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        bars?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            // The bars are not shown again here: MainActivity sets them for the next screen from
            // the user's fullscreen setting.
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // With no machine connected the only thing to run is the preview, so start in it.
    LaunchedEffect(saved.id) {
        if (state.connectionState == ConnectionState.DISCONNECTED && !preview) onPreviewChange(true)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { android.util.Log.w("WorkoutRunScreen", "Video access will not survive a restart.", it) }
            videoPrefs.edit().putString(saved.id, uri.toString()).remove("yt:" + saved.id).apply()
            videoUri = uri
            youtubeId = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The video is the background. Without one, the screen is a plain dark field, and the
        // display still works: a workout does not need a video, only a program.
        if (youtubeId != null) {
            YouTubePlayer(
                videoId = youtubeId!!,
                playing = running,
                positionMillis = { current.totalElapsedMillis.toInt() },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (videoUri != null) {
            LocalVideoPlayer(
                uri = videoUri,
                playing = running,
                positionMillis = { current.totalElapsedMillis.toInt() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Cinematic vignette scrim: darker at top/bottom for readability, clear in center for scenery
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x9905080C),
                        0.15f to Color(0x3305080C),
                        0.70f to Color(0x3305080C),
                        1.0f to Color(0xCC05080C),
                    ),
                ),
        )

        WorkoutHud(
            program = saved.program,
            snapshot = snapshot,
            state = displayState,
            limits = limits,
            preview = preview,
            hasVideo = videoUri != null || youtubeId != null,
            engineMessage = engineMessage,
            onOverrideSpeed = onOverrideSpeed,
            onOverrideIncline = onOverrideIncline,
            onResetToProgram = onResetToProgram,
            onResetSpeed = onResetSpeed,
            onResetIncline = onResetIncline,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onSkip = onSkip,
            onAbandon = onAbandon,
            onSelectVideo = { choosingVideo = true },
            onTogglePreview = { onPreviewChange(!preview) },
            onEmergencyStop = onEmergencyStop,
            heartRate = heartRate,
            heartSummary = heartSummary,
            tiles = tiles,
            onTilesChange = onTilesChange,
            swapSides = swapSides,
            onSwapSides = onSwapSides,
            coachStatus = coachStatus,
            coachNotice = coachNotice,
            onCoachStart = onCoachStart,
            onCoachStop = onCoachStop,
            coachLevel = coachLevel,
            onCoachLevel = onCoachLevel,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )

        if (choosingVideo) {
            AlertDialog(
                onDismissRequest = { choosingVideo = false },
                title = { Text("Background video") },
                text = {
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it; linkError = false },
                        label = { Text("YouTube link") },
                        isError = linkError,
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val id = parseYouTubeId(linkText)
                        if (id == null) {
                            linkError = true
                        } else {
                            videoPrefs.edit().putString("yt:" + saved.id, id).remove(saved.id).apply()
                            youtubeId = id
                            videoUri = null
                            choosingVideo = false
                        }
                    }) { Text("Use link") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        choosingVideo = false
                        picker.launch(arrayOf("video/*"))
                    }) { Text("Pick a file instead") }
                },
            )
        }
    }
}
