package com.ofit.app.workout

import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.material3.Icon

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.filled.AcUnit

import androidx.compose.foundation.layout.size

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.border

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    /** Picking up an interrupted workout (P4): jump here once it has started. */
    resumeAtMillis: Long = 0,
    /** Intensity (P7): the level this run is at, a suggestion from the last rating, and a way to change it before Start. */
    intensity: com.ofit.protocol.Intensity.Level = com.ofit.protocol.Intensity.Level.STANDARD,
    intensitySuggestion: com.ofit.protocol.Intensity.Suggestion? = null,
    onIntensity: ((com.ofit.protocol.Intensity.Level) -> Unit)? = null,
    snapshot: WorkoutEngineSnapshot,
    state: TreadmillState,
    limits: SafetyLimits = SafetyLimits.DEFAULT,
    preview: Boolean,
    onPreviewChange: (Boolean) -> Unit,
    /** The runner's own choice to work out with no treadmill (sticks; see TreadmillController.setNoTreadmill). */
    onNoTreadmill: (Boolean) -> Unit = onPreviewChange,
    /** The video is stuck (an ad): hold the program's clock. */
    onVideoHeld: (Boolean) -> Unit = {},
    /** Why Start was refused, if it was. */
    startProblem: String? = null,
    /** Jump to a point in the workout. */
    onSeek: (Long) -> Unit = {},
    playbackRate: Float = 1f,
    onPlaybackRate: (Float) -> Unit = {},
    chosenRate: Float = playbackRate,
    matchSpeed: Boolean = false,
    onMatchSpeed: (Boolean) -> Unit = {},
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
    // Video volume, remembered for next time. Seeking bumps the video to the new point at once.
    var volume by remember { mutableStateOf(videoPrefs.getInt("volume", 100)) }
    // The virtual coach: off unless chosen, remembered for next time.
    var coach by remember { mutableStateOf(CoachChoice.fromName(videoPrefs.getString("coach", null))) }
    // The coach's look from the coach designer, and whether the designer is open over the workout.
    var coachLook by remember { mutableStateOf(CoachLook.load(context)) }
    var designingCoach by remember { mutableStateOf(false) }
    var coachSpeaks by remember { mutableStateOf(videoPrefs.getBoolean("coach_speaks", true)) }
    // Captions and the voice are separate switches, and the voice has its own volume (user request 2026-11-24).
    var coachCaptions by remember { mutableStateOf(videoPrefs.getBoolean("coach_captions", true)) }
    var coachVolume by remember { mutableStateOf(videoPrefs.getFloat("coach_volume", 1f)) }
    var coachPersona by remember(coach) { mutableStateOf(CoachLook.persona(context, coach)) }
    // The coach's pace against the speed it is given, kept per workout (user request 2026-11-24).
    var coachPace by remember(saved.id) { mutableStateOf(videoPrefs.getFloat("coach_pace:" + saved.id, 1f)) }
    // The coach's size, one setting for every workout (user request 2026-11-24).
    var coachSize by remember { mutableStateOf(videoPrefs.getFloat("coach_size", 1f)) }
    var videoSeek by remember(saved.id) { mutableStateOf(0) }
    val seekAndFollow: (Long) -> Unit = { ms ->
        onSeek(ms)
        videoSeek++
    }
    // Ad hold: while the YouTube video stands still (an ad, or loading), the workout holds.
    val videoHold = remember(saved.id) { com.ofit.protocol.VideoHold() }
    var videoHeld by remember(saved.id) { mutableStateOf(false) }
    var lastVideoSeconds by remember(saved.id) { mutableStateOf(0.0) }
    // The workout itself, not the cool-down: the video is paused in the cool-down, and a paused
    // video there must not look like an ad and hold the cool-down's clock.
    // Start countdown (user request 2026-11-24): the belt takes a moment to get going, so Start
    // shows 3, 2, 1 once the workout has started. The belt speeds up meanwhile, but the video and
    // the program's clock hold until the countdown ends, so no time is lost to the belt's delay.
    var awaitingStart by remember(saved.id) { mutableStateOf(false) }
    var countdown by remember(saved.id) { mutableStateOf(0) }
    var problemAtPress by remember(saved.id) { mutableStateOf<String?>(null) }
    val startWithCountdown: () -> Unit = {
        problemAtPress = startProblem
        awaitingStart = true
        onStart()
    }
    val runningNow = snapshot.state == WorkoutEngineState.RUNNING && countdown == 0
    fun checkHold(seconds: Double) {
        val held = videoHold.update(android.os.SystemClock.elapsedRealtime(), seconds, runningNow && youtubeId != null)
        if (held != videoHeld) {
            videoHeld = held
            onVideoHeld(held || countdown > 0)
        }
    }
    LaunchedEffect(snapshot.state, awaitingStart) {
        if (awaitingStart && snapshot.state == WorkoutEngineState.RUNNING) {
            awaitingStart = false
            countdown = 3
        } else if (awaitingStart && snapshot.isFinished) {
            awaitingStart = false
        }
    }
    // P4: where the workout is, kept every few seconds while it runs, so it can be resumed if
    // OpenFit closes mid-run; cleared when it ends normally (cool-down, finish, End, E-STOP).
    LaunchedEffect(saved.id, snapshot.state) {
        when {
            snapshot.state == WorkoutEngineState.RUNNING -> while (true) {
                val s = current
                if (s.state != WorkoutEngineState.RUNNING) break
                com.ofit.app.workout.ResumeStore.save(
                    context,
                    com.ofit.protocol.ResumePoint(saved.id, saved.program.name, s.totalElapsedMillis,
                        s.totalElapsedMillis + s.totalRemainingMillis, System.currentTimeMillis()),
                )
                kotlinx.coroutines.delay(com.ofit.protocol.ResumePoint.SAVE_EVERY_MILLIS)
            }
            snapshot.isCoolingDown || snapshot.isFinished || snapshot.state == WorkoutEngineState.ABANDONED ->
                com.ofit.app.workout.ResumeStore.clear(context)
        }
    }
    // P4: once the resumed workout is running (after the countdown), jump to where it stopped.
    var resumed by remember(saved.id) { mutableStateOf(resumeAtMillis <= 0) }
    LaunchedEffect(snapshot.state, countdown) {
        if (!resumed && snapshot.state == WorkoutEngineState.RUNNING && countdown == 0 && !awaitingStart) {
            resumed = true
            seekAndFollow(resumeAtMillis)
        }
    }

    // Skip intro and outro (user request 2026-11-24), on by default: a video workout with a
    // suggested start jumps there as it starts, and ends at the suggested end. Nothing is cut; the
    // runner can turn it off in the video menu, and seeking back into the intro is left alone.
    var skipIntro by remember { mutableStateOf(videoPrefs.getBoolean("skip_intro_outro", true)) }
    val hasBounds = saved.program.introEndSeconds != null || saved.program.outroStartSeconds != null
    var introDone by remember(saved.id) { mutableStateOf(false) }
    LaunchedEffect(snapshot.state, countdown) {
        if (introDone || resumeAtMillis > 0 || snapshot.state != WorkoutEngineState.RUNNING) return@LaunchedEffect
        introDone = true
        val start = com.ofit.protocol.VideoBounds.startSeconds(saved.program, skipIntro) ?: return@LaunchedEffect
        if (snapshot.totalElapsedMillis < start * 1000L) seekAndFollow(start * 1000L)
    }
    LaunchedEffect(snapshot.totalElapsedMillis / 1000, skipIntro) {
        if (snapshot.state == WorkoutEngineState.RUNNING &&
            com.ofit.protocol.VideoBounds.reachedOutro(saved.program, skipIntro, snapshot.totalElapsedMillis / 1000)
        ) {
            // To the program's end: it finishes as usual, with its cool-down and summary.
            seekAndFollow(saved.program.totalDurationSeconds * 1000L)
        }
    }

    // A start that never happens (treadmill off, rejected) must not leave the "…" up.
    LaunchedEffect(awaitingStart, startProblem) {
        if (awaitingStart && startProblem != null && startProblem != problemAtPress) awaitingStart = false
        if (awaitingStart) {
            kotlinx.coroutines.delay(35_000)
            awaitingStart = false
        }
    }
    LaunchedEffect(countdown) {
        onVideoHeld(videoHeld || countdown > 0)
        if (countdown > 0) {
            kotlinx.coroutines.delay(1_000)
            countdown -= 1
        }
    }
    // A watchdog too, so a hold still ends (2 min cap) if the player stops reporting at all.
    androidx.compose.runtime.LaunchedEffect(saved.id) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            checkHold(lastVideoSeconds)
        }
    }
    androidx.compose.runtime.DisposableEffect(saved.id) {
        onDispose { onVideoHeld(false) }
    }
    val engineMessage = when {
        videoHeld -> "Video paused (an ad, or loading). Your workout is holding at the current speed and incline."
        !snapshot.isActive && startProblem != null -> startProblem
        else -> snapshot.message
    }

    // Whether the program is running right now. The video follows this: it plays while the
    // program runs and stops when it does not, so it is paused in the cool-down (user request).
    val running = snapshot.state == WorkoutEngineState.RUNNING && countdown == 0

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
                seekRequest = videoSeek,
                rate = playbackRate,
                volume = volume,
                // Another app took the audio (music): the video carries on muted; show it muted.
                onTakenOver = {
                    volume = 0
                    videoPrefs.edit().putInt("volume", 0).apply()
                },
                onVideoPosition = { seconds ->
                    lastVideoSeconds = seconds
                    checkHold(seconds)
                },
            )
        } else if (videoUri != null) {
            LocalVideoPlayer(
                uri = videoUri,
                playing = running,
                positionMillis = { current.totalElapsedMillis.toInt() },
                modifier = Modifier.fillMaxSize(),
                seekRequest = videoSeek,
                rate = playbackRate,
                volume = volume,
            )
        }

        // Cool-down: the video stops, and a drawn path at the belt's speed takes its place (user request 2026-11-24).
        if (snapshot.isCoolingDown) {
            CoolDownScene(speedKph = displayState.displayedSpeedKph ?: snapshot.speedKph, modifier = Modifier.fillMaxSize())
        }

        // A shield over the video: YouTube's own play button and links cannot be tapped, so the video
        // only ever plays and pauses with the workout (user report 2026-11-24). Before the workout
        // starts, tapping the video (its big play button) starts the workout, like the Start button.
        if (youtubeId != null || videoUri != null) {
            val notStarted = !snapshot.isActive && !snapshot.isFinished
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { if (notStarted) startWithCountdown() },
                    ),
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

        // Before Start the engine has no program loaded, so it reports 0:00 left and no next step.
        // Show the workout as it will begin instead: its whole length and its second step as "next".
        val shownSnapshot = if (!snapshot.isActive && !snapshot.isFinished) {
            val first = saved.program.steps.firstOrNull()
            val second = saved.program.steps.getOrNull(1)
            snapshot.copy(
                programName = saved.program.name,
                stepCount = saved.program.steps.size,
                stepRemainingMillis = first?.durationMillis ?: 0,
                totalRemainingMillis = saved.program.totalDurationSeconds * 1000L,
                nextSpeedKph = second?.speedKph,
                nextInclinePercent = second?.inclinePercent,
            )
        } else {
            snapshot
        }
        // The coach runs ahead in the lower part of the picture, under the controls. With a video
        // playing, it keeps pace with the scenery (the video's own pace times its playback rate,
        // which never goes below 0.5x) rather than the belt, so it never looks slow against the
        // picture (user report 2026-11-24); otherwise, and in the cool-down, with the belt.
        val referenceKph = remember(saved.program) { com.ofit.protocol.MatchSpeed.referenceKph(saved.program) }
        val beltKph = displayState.displayedSpeedKph ?: snapshot.speedKph
        // No treadmill with a speed set on screen: the coach runs at that speed, the runner's own
        // (user request 2026-11-24); with Match my speed on the video follows it too.
        val sceneryKph = referenceKph?.takeIf { (youtubeId != null || videoUri != null) && snapshot.state == WorkoutEngineState.RUNNING }
            ?.takeIf { !(preview && snapshot.speedOverride) }
            ?.let { it * playbackRate }
        CoachOverlay(
            choice = coach,
            snapshot = snapshot,
            speedKph = sceneryKph ?: beltKph,
            pace = coachPace,
            speaks = coachSpeaks,
            videoId = youtubeId,
            referenceKph = referenceKph,
            scale = coachSize,
            look = coachLook,
            captions = coachCaptions,
            voiceVolume = coachVolume,
            persona = coachPersona,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Feet at the bottom edge of the screen (user request 2026-11-24): the feet sit about
                // 35 dp above the bottom of the coach's view, so the view hangs that far below the edge.
                .offset(x = (-90).dp, y = (35 * coachSize).dp),
        )

        WorkoutHud(
            runCoach = coach,
            coachSpeaks = coachSpeaks,
            onDesignCoach = { designingCoach = true },
            coachSize = coachSize,
            onCoachSize = { size ->
                coachSize = size
                videoPrefs.edit().putFloat("coach_size", size).apply()
            },
            coachPace = coachPace,
            onCoachPace = { pace ->
                coachPace = pace
                videoPrefs.edit().putFloat("coach_pace:" + saved.id, pace).apply()
            },
            onCoachSpeaks = { on ->
                coachSpeaks = on
                videoPrefs.edit().putBoolean("coach_speaks", on).apply()
            },
            coachCaptions = coachCaptions,
            onCoachCaptions = { on ->
                coachCaptions = on
                videoPrefs.edit().putBoolean("coach_captions", on).apply()
            },
            coachVolume = coachVolume,
            onCoachVolume = { v ->
                coachVolume = v
                videoPrefs.edit().putFloat("coach_volume", v).apply()
            },
            onRunCoach = { choice ->
                coach = choice
                videoPrefs.edit().putString("coach", choice.name).apply()
            },
            program = saved.program,
            snapshot = shownSnapshot,
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
            onStart = startWithCountdown,
            onPause = onPause,
            onResume = onResume,
            onSkip = onSkip,
            onAbandon = onAbandon,
            onSelectVideo = { choosingVideo = true },
            onTogglePreview = { onNoTreadmill(!preview) },
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
            onBack = onBack,
            onSeek = seekAndFollow,
            playbackRate = playbackRate,
            onPlaybackRate = onPlaybackRate,
            chosenRate = chosenRate,
            matchSpeed = matchSpeed,
            onMatchSpeed = onMatchSpeed,
            skipIntro = if (hasBounds) skipIntro else null,
            onSkipIntro = { on ->
                skipIntro = on
                videoPrefs.edit().putBoolean("skip_intro_outro", on).apply()
            },
            volume = volume,
            onVolume = { v ->
                volume = v.coerceIn(0, 100)
                videoPrefs.edit().putInt("volume", volume).apply()
            },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )

        // The coach designer over the workout: the workout carries on underneath.
        if (designingCoach) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { designingCoach = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                CoachDesigner(
                    choice = if (coach == CoachChoice.OFF) CoachChoice.FEMALE else coach,
                    look = coachLook,
                    onChoice = { c ->
                        coach = c
                        videoPrefs.edit().putString("coach", c.name).apply()
                    },
                    onLook = { l ->
                        coachLook = l
                        CoachLook.save(context, l)
                    },
                    persona = coachPersona,
                    onPersona = { p ->
                        coachPersona = p
                        CoachLook.savePersona(context, if (coach == CoachChoice.OFF) CoachChoice.FEMALE else coach, p)
                    },
                    onDone = { designingCoach = false },
                )
            }
        }

        // Intensity before Start (P7): the same workout, easier or harder.
        if (onIntensity != null && !snapshot.isActive && !snapshot.isFinished && !awaitingStart && countdown == 0) {
            IntensityPicker(
                level = intensity,
                suggestion = intensitySuggestion,
                onPick = onIntensity,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 120.dp),
            )
        }

        // 3, 2, 1 in the middle of the screen while the belt gets going.
        if (awaitingStart || countdown > 0) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(220.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xB3000000)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (countdown > 0) countdown.toString() else "…",
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // The cool-down after the workout: what is happening, the time left, and a way to stop now.
        if (snapshot.isCoolingDown) {
            CoolDownBanner(
                snapshot = snapshot,
                onEnd = onAbandon,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 120.dp),
            )
        }

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

/** "Cooling down · 8:41 left", the easy pace, and End cool-down (the belt stops). */
@Composable
private fun CoolDownBanner(snapshot: com.ofit.protocol.WorkoutEngineSnapshot, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val units = com.ofit.app.settings.LocalUnits.current
    androidx.compose.foundation.layout.Row(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(androidx.compose.ui.graphics.Color(0xF0123B5A), androidx.compose.ui.graphics.Color(0xF0102030)),
                ),
            )
            .border(1.dp, androidx.compose.ui.graphics.Color(0x5538BDF8), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Icon(
            androidx.compose.material.icons.Icons.Filled.AcUnit,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color(0xFF7DD3FC),
            modifier = Modifier.size(44.dp),
        )
        androidx.compose.foundation.layout.Column {
            Text("COOLING DOWN", color = androidx.compose.ui.graphics.Color(0xFF7DD3FC), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(
                formatClock((snapshot.totalRemainingMillis / 1000).toInt()) + " left",
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
            )
            Text(
                "Easy walk at ${units.speedText(snapshot.speedKph)}, flat. Slows each minute, then the belt stops.",
                color = androidx.compose.ui.graphics.Color(0xCCFFFFFF),
            )
        }
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(width = 150.dp, height = 72.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFF5F7F8))
                .clickable(onClick = onEnd),
            contentAlignment = Alignment.Center,
        ) {
            Text("End cool-down", color = androidx.compose.ui.graphics.Color(0xFF0D0D0D), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}


/** Gentle to Hardest, as a row of chips over the video, with the suggestion after a rating. */
@Composable
private fun IntensityPicker(
    level: com.ofit.protocol.Intensity.Level,
    suggestion: com.ofit.protocol.Intensity.Suggestion?,
    onPick: (com.ofit.protocol.Intensity.Level) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xB3000000))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("INTENSITY", color = Color(0xCCFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.ofit.protocol.Intensity.Level.values().forEach { l ->
                val on = l == level
                val suggested = suggestion?.level == l
                Column(
                    Modifier
                        .size(width = 112.dp, height = 72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) accent else Color(0x22FFFFFF))
                        .then(if (suggested && !on) Modifier.border(2.dp, accent, RoundedCornerShape(18.dp)) else Modifier)
                        .clickable { onPick(l) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(l.label, color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (l.speedFactor == 1.0) "as made" else "%+d%% pace".format(((l.speedFactor - 1) * 100).roundToInt()),
                        color = if (on) Color(0xCC000000) else Color(0x99FFFFFF),
                        fontSize = 12.sp,
                    )
                }
            }
        }
        suggestion?.takeIf { it.level != level }?.let {
            Text("${it.reason}: try ${it.level.label}", color = Color(0xCCFFFFFF), fontSize = 14.sp)
        }
    }
}
