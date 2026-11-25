package com.ofit.app.workout

import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.automirrored.filled.DirectionsRun

import androidx.compose.foundation.layout.widthIn

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.settings.LocalUnits
import com.ofit.protocol.ConnectionState
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.TreadmillState
import com.ofit.protocol.WorkoutEngineSnapshot
import com.ofit.protocol.WorkoutEngineState
import com.ofit.protocol.WorkoutProgram
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * OpenFit's workout heads-up display:
 *
 *  - Top telemetry bar spanning the header with primary metrics.
 *  - Left column: Incline card with quick nudge targets (+/-) mirroring the machine console.
 *  - Right column: Speed card with quick nudge targets (+/-) mirroring the machine console.
 *  - Center: Open view for landscape video scenery.
 *  - Lower area: Topographical profile ribbon tracking workout progression.
 *  - Bottom: Floating control ribbon (Play/Pause, Skip, End, Video, Preview, Emergency Stop).
 */
private enum class OverrideTarget { SPEED, INCLINE }

@Composable
fun WorkoutHud(
    program: WorkoutProgram,
    snapshot: WorkoutEngineSnapshot,
    state: TreadmillState,
    limits: SafetyLimits = SafetyLimits.DEFAULT,
    preview: Boolean = false,
    hasVideo: Boolean = false,
    engineMessage: String? = null,
    onOverrideSpeed: (Double) -> Unit = {},
    onOverrideIncline: (Double) -> Unit = {},
    onResetToProgram: () -> Unit = {},
    onResetSpeed: () -> Unit = onResetToProgram,
    onResetIncline: () -> Unit = onResetToProgram,
    onStart: () -> Unit = {},
    onPause: () -> Unit = {},
    /** A tap on the video: start, pause or carry on. */
    onVideoTap: () -> Unit = {},
    onResume: () -> Unit = {},
    onSkip: () -> Unit = {},
    onAbandon: () -> Unit = {},
    onSelectVideo: () -> Unit = {},
    onTogglePreview: () -> Unit = {},
    onEmergencyStop: () -> Unit = {},
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
    /** Leave the workout screen (asks first while a workout runs). */
    onBack: () -> Unit = {},
    /** Jump to a point in the workout. */
    onSeek: (Long) -> Unit = {},
    playbackRate: Float = 1f,
    onPlaybackRate: (Float) -> Unit = {},
    chosenRate: Float = playbackRate,
    matchSpeed: Boolean = false,
    onMatchSpeed: (Boolean) -> Unit = {},
    /** Skip intro and outro: null when the workout has no suggested start or end. */
    skipIntro: Boolean? = null,
    onSkipIntro: (Boolean) -> Unit = {},
    /** Video volume 0..100 (0 = muted). */
    volume: Int = 100,
    onVolume: (Int) -> Unit = {},
    /** The video is muted because another app's music is playing; the volume setting is kept. */
    videoMutedForMusic: Boolean = false,
    onMusicStarts: () -> Unit = {},
    /** Give the sound back to the video now, which pauses the music. */
    onVideoSoundInstead: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** The virtual coach shown over the video, and choosing it. */
    runCoach: CoachChoice = CoachChoice.OFF,
    /** The saved coaches to choose from, the chosen one, and choosing (null: no coach). */
    coaches: List<SavedCoach> = emptyList(),
    activeCoachId: String? = null,
    onPickCoach: (SavedCoach?) -> Unit = {},
    coachSpeaks: Boolean = true,
    onCoachSpeaks: (Boolean) -> Unit = {},
    coachCaptions: Boolean = true,
    onCoachCaptions: (Boolean) -> Unit = {},
    /** What the coach is saying, shown as a caption above the bottom controls; null for none. */
    caption: String? = null,
    coachVolume: Float = 1f,
    onCoachVolume: (Float) -> Unit = {},
    /** How fast the coach moves against the speed it is given: 1 = as is (user request 2026-11-24). */
    coachPace: Float = 1f,
    onCoachPace: (Float) -> Unit = {},
    /** How big the coach is drawn: 1 = as designed (user request 2026-11-24). */
    coachSize: Float = 1f,
    onCoachSize: (Float) -> Unit = {},
    /** Open the coach designer over the workout. */
    onDesignCoach: () -> Unit = {},
) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    var editing by remember { mutableStateOf<OverrideTarget?>(null) }
    val units = LocalUnits.current

    // What the belt was actually told, step by step, so manual overrides show on the chart (R8).
    val history = remember(program.name) { androidx.compose.runtime.mutableStateListOf<HudTargetPoint>() }
    androidx.compose.runtime.LaunchedEffect(snapshot.speedKph, snapshot.inclinePercent, snapshot.stepIndex, snapshot.state) {
        if (!snapshot.isActive) return@LaunchedEffect
        val seconds = snapshot.totalElapsedMillis / 1000f
        if (history.isNotEmpty() && seconds < history.last().seconds - 1f) history.clear()
        val last = history.lastOrNull()
        if (last == null || last.speedKph != snapshot.speedKph || last.inclinePercent != snapshot.inclinePercent ||
            last.manual != snapshot.manualOverride
        ) {
            history.add(HudTargetPoint(seconds, snapshot.speedKph, snapshot.inclinePercent, snapshot.manualOverride))
        }
    }

    // The heart-rate zone over the run, for the colour strip under the chart (spec 002 H5).
    val zones = remember(program.name) { com.ofit.protocol.ZoneTimeline() }
    var zoneTick by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(heartRate?.zone, heartRate?.bpm != null, snapshot.state) {
        if (!snapshot.isActive) return@LaunchedEffect
        zones.record(snapshot.totalElapsedMillis / 1000f, heartRate?.zone.takeIf { heartRate?.bpm != null })
        zoneTick++
    }

    // The controls can be hidden to leave the video clear (spec 002 R9a).
    var overlayHidden by remember { mutableStateOf(false) }


    // Dismiss dialog automatically if workout ends or pauses
    if (!snapshot.isRunning && editing != null) {
        editing = null
    }

    if (landscape && overlayHidden) {
        HudHiddenOverlay(
            snapshot = snapshot,
            onShow = { overlayHidden = false },
            onPause = onPause,
            onResume = onResume,
            onAbandon = onAbandon,
            onEmergencyStop = onEmergencyStop,
            coachStatus = coachStatus,
            onCoachStop = onCoachStop,
            noTreadmill = preview,
            modifier = modifier,
        )
    } else if (landscape) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 1. Top Telemetry Ribbon, with Back at the top left.
            Box(Modifier.fillMaxWidth()) {
                HudTopBar(
                    program = program,
                    snapshot = snapshot,
                    rate = playbackRate,
                    state = state,
                    preview = preview,
                    tiles = tiles,
                    onTilesChange = onTilesChange,
                    heartRate = heartRate,
                    heartSummary = heartSummary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Menu first, then back: the same order as every other screen (user report 2026-11-24).
                    com.ofit.app.ui.LocalOpenMenu.current?.let { open ->
                        HudSquareButton(icon = Icons.Filled.Menu, description = "Menu", enabled = true, onClick = open)
                    }
                    HudSquareButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = "Back",
                        enabled = true,
                        onClick = onBack,
                    )
                }
            }

            // 2. Middle: the incline card on the left and the speed card on the right, with +/-
            //    buttons clamped to the machine's range (the user prefers these to
            //    one-tap columns).
            // The middle, with the coach's captions along its bottom edge, just above the bottom
            // controls (user request 2026-11-24: standard captions rather than a speech bubble).
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Tapping the video (its middle, where YouTube shows its play button) starts the
                    // workout, pauses it, or carries on, like the Start/Pause button (user request
                    // 2026-11-24). The cards and buttons on top keep their own taps.
                    .pointerInput(snapshot.state) {
                        detectTapGestures { onVideoTap() }
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Swap sides (R9e) puts speed on the left and incline on the right.
                    val inclineCard: @Composable () -> Unit = {
                        HudInclineCard(
                            incline = state.displayedInclinePercent,
                            targetIncline = snapshot.inclinePercent,
                            manualOverride = snapshot.inclineOverride,
                            limits = limits,
                            isRunning = snapshot.isRunning,
                            onNudge = { delta ->
                                val next = Math.round((snapshot.inclinePercent + delta) * 2) / 2.0
                                onOverrideIncline(
                                    next.coerceIn(limits.machineMinInclinePercent ?: 0.0, limits.effectiveMaxInclinePercent),
                                )
                            },
                            onReset = onResetIncline,
                            onClickValue = if (snapshot.isRunning) ({ editing = OverrideTarget.INCLINE }) else null,
                        )
                    }
                    val speedCard: @Composable () -> Unit = {
                        HudSpeedCard(
                            speed = state.displayedSpeedKph,
                            targetSpeed = snapshot.speedKph,
                            manualOverride = snapshot.speedOverride,
                            limits = limits,
                            isRunning = snapshot.isRunning,
                            onNudge = { delta ->
                                val shown = units.speedFromKph(snapshot.speedKph + delta)
                                val next = units.speedToKph(Math.round(shown * 2) / 2.0)
                                onOverrideSpeed(
                                    next.coerceIn(limits.machineMinSpeedKph ?: 0.0, limits.effectiveMaxSpeedKph),
                                )
                            },
                            onReset = onResetSpeed,
                            onClickValue = if (snapshot.isRunning) ({ editing = OverrideTarget.SPEED }) else null,
                            coachOn = coachStatus != null,
                        )
                    }

                    // No treadmill: before Start, the program's own speed and incline are shown as a
                    // suggestion. Once it runs, the usual +/- cards set the speed the runner is really
                    // going (user request 2026-11-24), which the distance, the video (Match my speed)
                    // and the coach then follow; nothing is sent to a machine.
                    val firstStep = program?.steps?.firstOrNull()
                    val suggestSpeed = if (snapshot.isActive) snapshot.speedKph else firstStep?.speedKph
                    val suggestIncline = if (snapshot.isActive) snapshot.inclinePercent else firstStep?.inclinePercent
                    val nextSpeed = if (snapshot.isActive) snapshot.nextSpeedKph else program?.steps?.getOrNull(1)?.speedKph
                    val nextIncline = if (snapshot.isActive) snapshot.nextInclinePercent else program?.steps?.getOrNull(1)?.inclinePercent
                    val suggestedIncline: @Composable () -> Unit = {
                        HudSuggestedCard(
                            label = "INCLINE",
                            accent = HudColors.incline,
                            value = suggestIncline?.let { "%.1f".format(it) } ?: "--",
                            unit = "%",
                            next = nextIncline?.let { "Next %.1f%%".format(it) },
                        )
                    }
                    val suggestedSpeed: @Composable () -> Unit = {
                        HudSuggestedCard(
                            label = "SPEED",
                            accent = HudColors.speed,
                            value = suggestSpeed?.let { "%.1f".format(units.speedFromKph(it)) } ?: "--",
                            unit = units.speedUnit,
                            next = nextSpeed?.let { "Next %.1f".format(units.speedFromKph(it)) },
                        )
                    }
                    if (!preview || snapshot.isActive) { if (swapSides) speedCard() else inclineCard() } else { if (swapSides) suggestedSpeed() else suggestedIncline() }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        if (engineMessage != null) {
                            Text(
                                text = engineMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        // "Hold zone" (spec 003 S6): always visible while on, one tap turns it off.
                        if (coachStatus != null) {
                            Text(
                                text = "♥ Holding zone ${coachStatus.zone} (${coachStatus.level}) · ${coachStatus.band.first}–${coachStatus.band.last} bpm" +
                                    (coachStatus.note?.let { " · $it" } ?: "") + "   (tap to turn off)",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xCCB71C1C))
                                    .clickable(onClick = onCoachStop)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        if (coachNotice != null && coachStatus == null) {
                            Text(
                                text = coachNotice,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }

                    if (!preview || snapshot.isActive) { if (swapSides) inclineCard() else speedCard() } else { if (swapSides) suggestedIncline() else suggestedSpeed() }
                }
                if (caption != null) {
                    CoachCaption(caption, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                }
            }

            // 3. Lower area: the chart (R8), then the bottom bar.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // No treadmill: no speed/incline chart (nothing to follow), just how far through you are.
                if (preview) {
                    HudProgressBar(snapshot, Modifier.fillMaxWidth(), onSeek = onSeek, rate = playbackRate)
                } else {
                    // Tap the chart to jump to that point.
                    val total = (snapshot.totalElapsedMillis + snapshot.totalRemainingMillis).coerceAtLeast(1)
                    val currentTotal by androidx.compose.runtime.rememberUpdatedState(total)
                    val active by androidx.compose.runtime.rememberUpdatedState(snapshot.isActive)
                    HudProfileRibbon(
                        program = program,
                        snapshot = snapshot,
                        history = history,
                        zones = zones.also { zoneTick },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { o ->
                                    if (active) onSeek((currentTotal * (o.x / size.width)).toLong())
                                }
                            },
                    )
                }

                HudBottomControls(
                    runCoach = runCoach,
                    coaches = coaches,
                    activeCoachId = activeCoachId,
                    onPickCoach = onPickCoach,
                    coachSpeaks = coachSpeaks,
                    onCoachSpeaks = onCoachSpeaks,
                    coachCaptions = coachCaptions,
                    onCoachCaptions = onCoachCaptions,
                    coachVolume = coachVolume,
                    onCoachVolume = onCoachVolume,
                    coachPace = coachPace,
                    onCoachPace = onCoachPace,
                    coachSize = coachSize,
                    onCoachSize = onCoachSize,
                    onDesignCoach = onDesignCoach,
                    onSwapSides = onSwapSides,
                    coachOn = coachStatus != null,
                    coachLevel = coachLevel,
                    onCoachLevel = onCoachLevel,
                    onCoachStart = onCoachStart,
                    onCoachStop = onCoachStop,
                    snapshot = snapshot,
                    state = state,
                    preview = preview,
                    hasVideo = hasVideo,
                    onStart = onStart,
                    onPause = onPause,
                    onResume = onResume,
                    onSkip = onSkip,
                    onAbandon = onAbandon,
                    onSelectVideo = onSelectVideo,
                    onTogglePreview = onTogglePreview,
                    onEmergencyStop = onEmergencyStop,
                    onHide = { overlayHidden = true },
                    onSeek = onSeek,
                    playbackRate = playbackRate,
                    onPlaybackRate = onPlaybackRate,
                    chosenRate = chosenRate,
                    matchSpeed = matchSpeed,
                    onMatchSpeed = onMatchSpeed,
                    skipIntro = skipIntro,
                    onSkipIntro = onSkipIntro,
                    volume = volume,
                    onVolume = onVolume,
                    hasVideoAudio = hasVideo,
                    videoMutedForMusic = videoMutedForMusic,
                    onMusicStarts = onMusicStarts,
                    onVideoSoundInstead = onVideoSoundInstead,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        // Portrait Layout
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HudTopBar(
                program = program,
                snapshot = snapshot,
                rate = playbackRate,
                state = state,
                preview = preview,
                tiles = tiles,
                onTilesChange = onTilesChange,
                heartRate = heartRate,
                heartSummary = heartSummary,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!preview) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (swapSides) {
                    HudSpeedCard(
                        speed = state.displayedSpeedKph,
                        targetSpeed = snapshot.speedKph,
                        manualOverride = snapshot.speedOverride,
                        limits = limits,
                        isRunning = snapshot.isRunning,
                        onNudge = { delta ->
                            val next = ((snapshot.speedKph + delta) * 10.0).roundToInt() / 10.0
                            val clamped = next.coerceIn(
                                limits.machineMinSpeedKph ?: 0.0,
                                limits.effectiveMaxSpeedKph,
                            )
                            onOverrideSpeed(clamped)
                        },
                        onReset = onResetSpeed,
                        onClickValue = if (snapshot.isRunning) ({ editing = OverrideTarget.SPEED }) else null,
                            coachOn = coachStatus != null,
                        modifier = Modifier.weight(1f),
                    )

                    HudInclineCard(
                        incline = state.displayedInclinePercent,
                        targetIncline = snapshot.inclinePercent,
                        manualOverride = snapshot.inclineOverride,
                        limits = limits,
                        isRunning = snapshot.isRunning,
                        onNudge = { delta ->
                            val next = ((snapshot.inclinePercent + delta) * 10.0).roundToInt() / 10.0
                            val clamped = next.coerceIn(
                                limits.machineMinInclinePercent ?: 0.0,
                                limits.effectiveMaxInclinePercent,
                            )
                            onOverrideIncline(clamped)
                        },
                        onReset = onResetIncline,
                        onClickValue = if (snapshot.isRunning) ({ editing = OverrideTarget.INCLINE }) else null,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    HudInclineCard(
                        incline = state.displayedInclinePercent,
                        targetIncline = snapshot.inclinePercent,
                        manualOverride = snapshot.inclineOverride,
                        limits = limits,
                        isRunning = snapshot.isRunning,
                        onNudge = { delta ->
                            val next = ((snapshot.inclinePercent + delta) * 10.0).roundToInt() / 10.0
                            val clamped = next.coerceIn(
                                limits.machineMinInclinePercent ?: 0.0,
                                limits.effectiveMaxInclinePercent,
                            )
                            onOverrideIncline(clamped)
                        },
                        onReset = onResetIncline,
                        onClickValue = if (snapshot.isRunning) ({ editing = OverrideTarget.INCLINE }) else null,
                        modifier = Modifier.weight(1f),
                    )

                    HudSpeedCard(
                        speed = state.displayedSpeedKph,
                        targetSpeed = snapshot.speedKph,
                        manualOverride = snapshot.speedOverride,
                        limits = limits,
                        isRunning = snapshot.isRunning,
                        onNudge = { delta ->
                            val next = ((snapshot.speedKph + delta) * 10.0).roundToInt() / 10.0
                            val clamped = next.coerceIn(
                                limits.machineMinSpeedKph ?: 0.0,
                                limits.effectiveMaxSpeedKph,
                            )
                            onOverrideSpeed(clamped)
                        },
                        onReset = onResetSpeed,
                        onClickValue = if (snapshot.isRunning) ({ editing = OverrideTarget.SPEED }) else null,
                            coachOn = coachStatus != null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (preview) HudProgressBar(snapshot, Modifier.fillMaxWidth()) else HudProfileRibbon(
                program = program,
                snapshot = snapshot,
                history = history,
                zones = zones.also { zoneTick },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 90.dp),
            )

            HudBottomControls(
                snapshot = snapshot,
                state = state,
                preview = preview,
                hasVideo = hasVideo,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onSkip = onSkip,
                onAbandon = onAbandon,
                onSelectVideo = onSelectVideo,
                onTogglePreview = onTogglePreview,
                onEmergencyStop = onEmergencyStop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (editing) {
        OverrideTarget.SPEED -> HudOverrideDialog(
            title = "Adjust speed",
            value = units.speedFromKph(snapshot.speedKph),
            unit = units.speedUnit,
            min = units.speedFromKph(limits.machineMinSpeedKph ?: 0.0),
            max = units.speedFromKph(limits.effectiveMaxSpeedKph),
            deltas = listOf(-0.5, -0.1, 0.1, 0.5),
            manualOverride = snapshot.speedOverride,
            onApply = { shown ->
                // Back to km/h, held inside the ceilings so rounding can never ask for more.
                onOverrideSpeed(
                    units.speedToKph(shown).coerceIn(
                        limits.machineMinSpeedKph ?: 0.0,
                        limits.effectiveMaxSpeedKph,
                    ),
                )
            },
            onReset = {
                onResetSpeed()
                editing = null
            },
            onDismiss = { editing = null },
        )

        OverrideTarget.INCLINE -> HudOverrideDialog(
            title = "Adjust incline",
            value = snapshot.inclinePercent,
            unit = "%",
            // No decline unless the machine says it has one: the T 6.5 refuses anything below 0 %.
            min = limits.machineMinInclinePercent ?: 0.0,
            max = limits.effectiveMaxInclinePercent,
            deltas = listOf(-1.0, -0.5, 0.5, 1.0),
            manualOverride = snapshot.inclineOverride,
            onApply = onOverrideIncline,
            onReset = {
                onResetIncline()
                editing = null
            },
            onDismiss = { editing = null },
        )

        null -> Unit
    }
}

/**
 * Top Telemetry Ribbon spanning across the top of the HUD.
 */
@Composable
private fun HudTopBar(
    program: WorkoutProgram,
    snapshot: WorkoutEngineSnapshot,
    state: TreadmillState,
    /** The playback speed in use, so 'remaining' is real time (1.25x: 10 min of video takes 8). */
    rate: Float = 1f,
    preview: Boolean,
    tiles: List<HudStat> = HudStat.DEFAULT,
    onTilesChange: (List<HudStat>) -> Unit = {},
    heartRate: com.ofit.app.heart.ShownHeartRate? = null,
    heartSummary: com.ofit.app.heart.HeartRateSummary? = null,
    modifier: Modifier = Modifier,
) {
    // The console zeroes its own time and distance when it switches to its results screen, so a
    // finished workout keeps the last values seen while it ran.
    var keptDistance by remember { mutableStateOf(0L) }
    var keptCalories by remember { mutableStateOf<Double?>(null) }
    if (snapshot.isActive) {
        state.distanceMeters?.takeIf { it > 0 }?.let { keptDistance = it }
        state.calories?.takeIf { it > 0 }?.let { keptCalories = it }
    }
    // The workout's own clock. The console's clock restarts at 0:00 when it is paused (seen live,
    // 2026-11-23) and is zeroed on its results screen, so it is not used here.
    val elapsed = (snapshot.totalElapsedMillis / 1000).toInt()
    val remaining = (com.ofit.protocol.MatchSpeed.realMillis(snapshot.totalRemainingMillis, rate) / 1000).toInt()
    val distance = if (snapshot.isFinished) keptDistance else state.distanceMeters ?: 0L
    // OpenFit's own estimate (age, sex, weight, heart rate, speed, incline) when it has one; the
    // console's figure only as a fallback.
    val calories = heartSummary?.calories ?: if (snapshot.isFinished) keptCalories else state.calories
    val units = LocalUnits.current

    // Average pace over the whole run so far (the reference shows "avg. pace"), from the console's
    // own distance and time.
    val averageSpeedKph = if (distance > 0 && elapsed > 0) distance / 1000.0 / (elapsed / 3600.0) else null

    // Vertical gain, worked out here: distance covered times the incline it was covered at. The
    // console's own vertical-meter fields are polled, but their unit is not confirmed.
    var verticalGainMeters by remember(program.name) { mutableStateOf(0.0) }
    var lastDistance by remember(program.name) { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(snapshot.state, state.distanceMeters) {
        if (snapshot.isRunning && snapshot.totalElapsedMillis < 1500) {
            verticalGainMeters = 0.0
            lastDistance = null
        }
        val now = state.distanceMeters
        if (snapshot.isRunning && now != null) {
            val before = lastDistance
            val incline = state.displayedInclinePercent ?: 0.0
            if (before != null && now > before && incline > 0) {
                verticalGainMeters += (now - before) * incline / 100.0
            }
            lastDistance = now
        }
    }
    val imperial = units == com.ofit.protocol.UnitSystem.IMPERIAL
    val verticalText = "%.0f".format(if (imperial) verticalGainMeters * 3.28084 else verticalGainMeters)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The reference's single glass bar of seven tiles, in its order.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(HudColors.glassPanel)
                .border(BorderStroke(1.dp, HudColors.glassBorder), RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Each tile: value, label, icon, and a colour (heart rate is coloured by zone).
            fun tileOf(stat: HudStat): HudTileData = when (stat) {
                HudStat.INCLINE -> HudTileData(state.displayedInclinePercent?.let { "%.2f".format(it) } ?: "--", "incline %", Icons.AutoMirrored.Filled.TrendingUp)
                HudStat.DISTANCE -> HudTileData(units.distanceValueText(distance.toDouble()), units.distanceUnitLong, Icons.AutoMirrored.Filled.DirectionsWalk)
                HudStat.AVG_PACE -> HudTileData(units.paceText(averageSpeedKph), "avg. pace${units.paceUnit}", Icons.Filled.Speed)
                HudStat.REMAINING -> HudTileData(formatClock(remaining), "remaining", Icons.Filled.Timer)
                HudStat.ELAPSED -> HudTileData(formatClock(elapsed), "elapsed", Icons.Filled.Timer)
                HudStat.CALORIES -> HudTileData(calories?.let { "%.0f".format(it) } ?: "--", "cals (est)", Icons.Filled.LocalFireDepartment)
                HudStat.VERT_GAIN -> HudTileData(verticalText, if (imperial) "vert gain (ft)" else "vert gain (m)", Icons.Filled.Landscape)
                HudStat.SPEED -> HudTileData(state.displayedSpeedKph?.let { "%.2f".format(units.speedFromKph(it)) } ?: "--", "speed ${units.speedUnit}", Icons.Filled.Speed)
                HudStat.HEART_RATE -> HudTileData(
                    value = heartRate?.bpm?.toString() ?: "--",
                    label = when (heartRate?.source) {
                        com.ofit.protocol.HeartRateSource.STRAP -> "bpm" + (heartRate.zone?.let { " · zone $it" } ?: "")
                        com.ofit.protocol.HeartRateSource.CONSOLE -> "bpm (grips)"
                        else -> "bpm"
                    },
                    icon = Icons.Filled.Favorite,
                    color = if (heartRate?.bpm != null) com.ofit.app.heart.zoneColor(heartRate.zone) else Color.White,
                )
                HudStat.AVG_HEART_RATE -> HudTileData(
                    value = heartSummary?.averageBpm?.toString() ?: "--",
                    label = "avg bpm" + (heartSummary?.maxBpm?.let { " · max $it" } ?: ""),
                    icon = Icons.Filled.Favorite,
                )
            }

            // Tapping a tile opens the picker under it (R3): swap it for another stat, or remove it.
            var picking by remember { mutableStateOf<Int?>(null) }
            // No treadmill: only stats that mean something without a machine (time, heart rate and the
            // calorie estimate), fixed; the treadmill ones (incline, speed, distance, pace, climb) are hidden.
            val shownTiles = if (preview) {
                listOf(HudStat.REMAINING, HudStat.ELAPSED, HudStat.CALORIES, HudStat.HEART_RATE, HudStat.AVG_HEART_RATE)
            } else {
                tiles
            }
            shownTiles.forEachIndexed { index, stat ->
                if (index > 0) HudStatDivider()
                Box {
                    val tile = tileOf(stat)
                    HudTopStatItem(
                        label = tile.label,
                        value = tile.value,
                        icon = tile.icon,
                        valueColor = tile.color,
                        modifier = if (preview) Modifier else Modifier.clickable { picking = index },
                    )
                    HudStatPicker(
                        expanded = picking == index,
                        current = stat,
                        shown = tiles,
                        canRemove = tiles.size > HudStat.MIN_TILES,
                        onPick = { choice ->
                            picking = null
                            val next = tiles.toMutableList()
                            val other = next.indexOf(choice)
                            // Picking a stat shown elsewhere swaps the two, so nothing is lost.
                            if (other >= 0) next[other] = stat
                            next[index] = choice
                            onTilesChange(next)
                        },
                        onRemove = {
                            picking = null
                            onTilesChange(tiles.filterIndexed { i, _ -> i != index })
                        },
                        onDismiss = { picking = null },
                    )
                }
            }
            if (!preview && tiles.size < HudStat.MAX_TILES) {
                var adding by remember { mutableStateOf(false) }
                Box {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add a stat",
                        tint = Color(0x99FFFFFF),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { adding = true },
                    )
                    HudStatPicker(
                        expanded = adding,
                        current = null,
                        shown = tiles,
                        canRemove = false,
                        onPick = { choice ->
                            adding = false
                            if (choice !in tiles) onTilesChange(tiles + choice)
                        },
                        onRemove = {},
                        onDismiss = { adding = false },
                    )
                }
            }
        }

        // Which workout and where in it: small, under the bar, as the reference has no title.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(HudColors.glassPanel)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = program.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    snapshot.state == WorkoutEngineState.COMPLETED && heartSummary?.averageBpm != null ->
                        "Finished · avg ${heartSummary.averageBpm} bpm, max ${heartSummary.maxBpm} bpm"
                    snapshot.state == WorkoutEngineState.COMPLETED -> "Finished"
                    snapshot.state == WorkoutEngineState.ABANDONED -> "Stopped"
                    snapshot.state == WorkoutEngineState.FAILED -> "Ended: ${snapshot.message ?: "the treadmill stopped answering"}"
                    snapshot.stepCount > 0 -> "Step ${snapshot.stepIndex + 1} of ${snapshot.stepCount} · ${formatClock((snapshot.stepElapsedMillis / 1000).toInt())} / ${formatClock(((snapshot.stepElapsedMillis + snapshot.stepRemainingMillis) / 1000).toInt())}"
                    else -> "Not started"
                },
                fontSize = 12.sp,
                color = Color(0xBBFFFFFF),
                maxLines = 1,
            )
            if (preview) {
                HudPill(text = "NO TREADMILL", tint = HudColors.amber, bg = Color(0x33F59E0B))
            }
        }
    }
}

/** A target the belt was given, and when, for drawing overrides on the chart. */
data class HudTargetPoint(
    val seconds: Float,
    val speedKph: Double,
    val inclinePercent: Double,
    val manual: Boolean,
)

private data class HudTileData(
    val value: String,
    val label: String,
    val icon: ImageVector,
    val color: Color = Color.White,
)

/** The glass drop-down under a tile (R3): every stat, the current one ticked, and Remove. */
@Composable
private fun HudStatPicker(
    expanded: Boolean,
    current: HudStat?,
    shown: List<HudStat>,
    canRemove: Boolean,
    onPick: (HudStat) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.ofit.app.ui.theme.OfitDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        com.ofit.app.ui.theme.OfitMenuHeader(if (current == null) "Add a stat" else "Show in this tile")
        HudStat.entries.forEach { stat ->
            val isCurrent = stat == current
            com.ofit.app.ui.theme.OfitMenuItem(
                text = stat.pickerLabel,
                detail = if (!isCurrent && stat in shown) "shown" else null,
                selected = isCurrent,
                onClick = { if (isCurrent) onDismiss() else onPick(stat) },
            )
        }
        if (canRemove) {
            com.ofit.app.ui.theme.OfitMenuDivider()
            com.ofit.app.ui.theme.OfitMenuItem(text = "Remove this tile", onClick = onRemove, destructive = true, icon = Icons.Filled.Stop)
        }
    }
}

/** One tile of the top bar: icon at the left, big value, small label under it (as the reference). */
@Composable
private fun HudTopStatItem(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
) {
    Row(
        modifier = modifier.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xCCFFFFFF),
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                text = value,
                fontSize = 22.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xAAFFFFFF),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HudStatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(Color(0x26FFFFFF)),
    )
}

/**
 * Left: the incline card.
 */
@Composable
private fun HudInclineCard(
    incline: Double?,
    targetIncline: Double,
    manualOverride: Boolean,
    limits: SafetyLimits,
    isRunning: Boolean,
    onNudge: (Double) -> Unit,
    onReset: () -> Unit,
    onClickValue: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val displayValue = incline?.let { "%.1f".format(it) } ?: "--"
    val borderCol = if (manualOverride) HudColors.incline else HudColors.glassBorder

    Column(
        modifier = modifier
            // Just wider than the 72 dp square buttons: a narrow card, not a wide box.
            .width(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HudColors.glassPanel)
            .border(BorderStroke(1.dp, borderCol), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            inclineIcon(incline)?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = HudColors.incline,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = "INCLINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = HudColors.incline,
            )
        }

        // + above the value and - below it, each the full width of the card: up is up.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HudNudgeButton(
                text = "+0.5",
                accentColor = HudColors.incline,
                enabled = isRunning && targetIncline < limits.effectiveMaxInclinePercent - 0.01,
                onClick = { onNudge(0.5) },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .let { if (onClickValue != null) it.clickable(onClick = onClickValue) else it }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = displayValue,
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xCCFFFFFF),
                )
            }
            HudNudgeButton(
                text = "-0.5",
                accentColor = HudColors.incline,
                enabled = isRunning && targetIncline > (limits.machineMinInclinePercent ?: 0.0) + 0.01,
                onClick = { onNudge(-0.5) },
            )
        }

        // Footer Target or Follow Program
        if (manualOverride) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HudPill(text = "MANUAL", tint = HudColors.incline, bg = Color(0x33FB923C))
                // The manual value holds across steps until this is tapped (user request 2026-11-24).
                Text(
                    "Rejoin program",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D0D0D),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(HudColors.incline)
                        .clickable(onClick = onReset)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        } else {
            Text(
                text = if (targetIncline != 0.0) "Target %.1f%%".format(targetIncline) else "Program 0.0%",
                fontSize = 11.sp,
                color = Color(0x99FFFFFF),
                maxLines = 1,
            )
        }
    }
}

/**
 * No treadmill: what the program asks for now, to match by hand on any treadmill. Read only.
 */
@Composable
private fun HudSuggestedCard(
    label: String,
    accent: Color,
    value: String,
    unit: String,
    next: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HudColors.glassPanel)
            .border(BorderStroke(1.dp, HudColors.glassBorder), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = accent)
        Text("SUGGESTED", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Color(0x99FFFFFF))
        Text(
            text = value,
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
        )
        Text(unit, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xCCFFFFFF))
        if (next != null) Text(next, fontSize = 11.sp, color = Color(0x99FFFFFF), maxLines = 1)
    }
}

/**
 * Right: the speed card.
 */
@Composable
private fun HudSpeedCard(
    speed: Double?,
    targetSpeed: Double,
    manualOverride: Boolean,
    limits: SafetyLimits,
    isRunning: Boolean,
    onNudge: (Double) -> Unit,
    onReset: () -> Unit,
    onClickValue: (() -> Unit)?,
    modifier: Modifier = Modifier,
    coachOn: Boolean = false,
) {
    val units = LocalUnits.current
    val displayValue = speed?.let { "%.1f".format(units.speedFromKph(it)) } ?: "--"
    val borderCol = if (manualOverride) HudColors.speed else HudColors.glassBorder

    Column(
        modifier = modifier
            // Just wider than the 72 dp square buttons: a narrow card, not a wide box.
            .width(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HudColors.glassPanel)
            .border(BorderStroke(1.dp, borderCol), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                tint = HudColors.speed,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "SPEED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = HudColors.speed,
            )
        }

        // + above the value and - below it, each the full width of the card: up is up.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HudNudgeButton(
                text = "+0.5",
                accentColor = HudColors.speed,
                enabled = isRunning && targetSpeed < limits.effectiveMaxSpeedKph - 0.01,
                onClick = { onNudge(units.speedToKph(0.5)) },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .let { if (onClickValue != null) it.clickable(onClick = onClickValue) else it }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = displayValue,
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = units.speedUnit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xCCFFFFFF),
                )
            }
            HudNudgeButton(
                text = "-0.5",
                accentColor = HudColors.speed,
                enabled = isRunning && targetSpeed > (limits.machineMinSpeedKph ?: 0.0) + 0.01,
                onClick = { onNudge(-units.speedToKph(0.5)) },
            )
        }

        // Footer Target or Follow Program
        if (manualOverride) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HudPill(
                    text = if (coachOn) "♥ COACH" else "MANUAL",
                    tint = if (coachOn) Color(0xFFFF8A80) else HudColors.speed,
                    bg = if (coachOn) Color(0x33B71C1C) else Color(0x3338BDF8),
                )
                // The manual value holds across steps until this is tapped (user request 2026-11-24).
                Text(
                    "Rejoin program",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D0D0D),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(HudColors.speed)
                        .clickable(onClick = onReset)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        } else {
            Text(
                text = if (targetSpeed > 0.0) "Target ${units.speedText(targetSpeed)}" else "Program 0.0",
                fontSize = 11.sp,
                color = Color(0x99FFFFFF),
                maxLines = 1,
            )
        }
    }
}

/**
 * Reusable Nudge button for +/- on the control cards.
 */
@Composable
private fun HudNudgeButton(
    text: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Square, 72 x 72 dp, stacked above and below the readout: a big target for a thumb, + where
    // "up" is, and not stretched wide (the user asked for squared shapes, 2026-11-23).
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) accentColor.copy(alpha = 0.22f) else Color(0x1AFFFFFF))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .border(
                BorderStroke(1.dp, if (enabled) accentColor.copy(alpha = 0.55f) else Color(0x22FFFFFF)),
                RoundedCornerShape(14.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color(0x66FFFFFF),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Elevation and Speed Profile Ribbon (Topography Chart).
 */
/** A slim glass bar showing how far through the workout you are (No treadmill mode). */
@Composable
private fun HudProgressBar(snapshot: WorkoutEngineSnapshot, modifier: Modifier = Modifier, onSeek: (Long) -> Unit = {}, rate: Float = 1f) {
    val total = (snapshot.totalElapsedMillis + snapshot.totalRemainingMillis).coerceAtLeast(1)
    // While dragging, the bar follows the finger; the jump happens when the finger lifts.
    var dragShare by remember { mutableStateOf<Float?>(null) }
    val share = dragShare ?: (snapshot.totalElapsedMillis.toFloat() / total).coerceIn(0f, 1f)
    val currentTotal by androidx.compose.runtime.rememberUpdatedState(total)
    val active by androidx.compose.runtime.rememberUpdatedState(snapshot.isActive)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HudColors.glassPanel)
            .border(BorderStroke(1.dp, HudColors.glassBorder), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            formatClock(((dragShare?.let { it * total } ?: snapshot.totalElapsedMillis.toFloat()) / 1000).toInt()),
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Box(
            Modifier
                .weight(1f)
                .height(36.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        if (active) onSeek((currentTotal * (o.x / size.width).coerceIn(0f, 1f)).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> if (active) dragShare = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragShare?.let { onSeek((currentTotal * it).toLong()) }
                            dragShare = null
                        },
                        onDragCancel = { dragShare = null },
                    ) { change, _ ->
                        if (active) dragShare = (change.position.x / size.width).coerceIn(0f, 1f)
                        change.consume()
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0x33FFFFFF)),
            )
            Box(
                Modifier
                    .fillMaxWidth(share)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text("−" + formatClock((com.ofit.protocol.MatchSpeed.realMillis(snapshot.totalRemainingMillis, rate) / 1000).toInt()), color = Color(0xCCFFFFFF), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HudProfileRibbon(
    program: WorkoutProgram,
    snapshot: WorkoutEngineSnapshot,
    history: List<HudTargetPoint> = emptyList(),
    /** The heart-rate zone over the run: drawn as a colour strip along the bottom (spec 002 H5). */
    zones: com.ofit.protocol.ZoneTimeline? = null,
    modifier: Modifier = Modifier,
) {
    val units = LocalUnits.current
    val steps = program.steps
    val totalSeconds = program.totalDurationSeconds.coerceAtLeast(1)
    val progress = (snapshot.totalElapsedMillis / 1000.0 / totalSeconds).coerceIn(0.0, 1.0).toFloat()

    // Axes as in the reference: incline on the left, speed on the right, each with its range.
    val inclineMin = minOf(0.0, steps.minOfOrNull { it.inclinePercent } ?: 0.0)
    val inclineMax = maxOf(inclineMin + 1.0, steps.maxOfOrNull { it.inclinePercent } ?: 1.0)
    val speedMax = maxOf(units.speedFromKph(steps.maxOfOrNull { it.speedKph } ?: 1.0), 1.0)
    fun axis(value: Double, suffix: String) =
        if (value == Math.rint(value)) "%.0f%s".format(value, suffix) else "%.1f%s".format(value, suffix)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HudColors.glassPanel)
            .border(BorderStroke(1.dp, HudColors.glassBorder), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.fillMaxHeight().width(44.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                listOf(inclineMax, (inclineMax + inclineMin) / 2, inclineMin).forEach {
                    Text(axis(it, " %"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HudColors.incline)
                }
            }
            Spacer(Modifier.width(8.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val top = 6.dp.toPx()
                val bottom = size.height - 6.dp.toPx()
                val h = bottom - top
                val w = size.width
                fun xAt(seconds: Int) = w * (seconds.toFloat() / totalSeconds)
                fun inclineY(p: Double) = (bottom - h * ((p - inclineMin) / (inclineMax - inclineMin))).toFloat()
                fun speedY(kph: Double) = (bottom - h * (units.speedFromKph(kph) / speedMax)).toFloat()

                // Faint grid at the three label heights.
                for (y in listOf(top, top + h / 2, bottom)) {
                    drawLine(Color(0x1FFFFFFF), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }

                // Incline: orange step line. Speed: blue line. Both over the whole workout.
                val inclinePath = Path()
                val speedPath = Path()
                var cursor = 0
                steps.forEachIndexed { index, step ->
                    val x0 = xAt(cursor)
                    val x1 = xAt(cursor + step.durationSeconds)
                    if (index == 0) {
                        inclinePath.moveTo(x0, inclineY(step.inclinePercent))
                        speedPath.moveTo(x0, speedY(step.speedKph))
                    } else {
                        inclinePath.lineTo(x0, inclineY(step.inclinePercent))
                        speedPath.lineTo(x0, speedY(step.speedKph))
                    }
                    inclinePath.lineTo(x1, inclineY(step.inclinePercent))
                    speedPath.lineTo(x1, speedY(step.speedKph))
                    cursor += step.durationSeconds
                }
                drawPath(inclinePath, HudColors.incline, style = Stroke(width = 2.5.dp.toPx()))
                drawPath(speedPath, HudColors.speed, style = Stroke(width = 2.5.dp.toPx()))

                // What is already run is dimmed a little, and the playhead marks "now".
                val markerX = w * progress
                if (markerX > 0f) {
                    drawRect(Color(0x55000000), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(markerX, size.height))
                }
                // Manual overrides, drawn as they happened: what the belt was actually told, over the
                // part already run. Only drawn once the runner has overridden something, since
                // otherwise it is the program line itself.
                if (history.any { it.manual }) {
                    val nowSeconds = snapshot.totalElapsedMillis / 1000f
                    val actualIncline = Path()
                    val actualSpeed = Path()
                    history.forEachIndexed { index, point ->
                        val x0 = w * (point.seconds / totalSeconds)
                        val endSeconds = history.getOrNull(index + 1)?.seconds ?: nowSeconds
                        val x1 = w * (endSeconds.coerceAtMost(totalSeconds.toFloat()) / totalSeconds)
                        val yi = inclineY(point.inclinePercent)
                        val ys = speedY(point.speedKph)
                        if (index == 0) {
                            actualIncline.moveTo(x0, yi)
                            actualSpeed.moveTo(x0, ys)
                        } else {
                            actualIncline.lineTo(x0, yi)
                            actualSpeed.lineTo(x0, ys)
                        }
                        actualIncline.lineTo(x1, yi)
                        actualSpeed.lineTo(x1, ys)
                    }
                    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    drawPath(actualIncline, Color(0xFFFFD199), style = Stroke(width = 2.dp.toPx(), pathEffect = dash))
                    drawPath(actualSpeed, Color(0xFFB3E5FF), style = Stroke(width = 2.dp.toPx(), pathEffect = dash))
                }

                // Heart-rate zone strip along the bottom, over the part already run.
                if (zones != null && !zones.isEmpty) {
                    val strip = 5.dp.toPx()
                    zones.segments(snapshot.totalElapsedMillis / 1000f).forEach { seg ->
                        val x0 = w * (seg.fromSeconds / totalSeconds)
                        val x1 = w * (seg.toSeconds.coerceAtMost(totalSeconds.toFloat()) / totalSeconds)
                        drawRect(
                            com.ofit.app.heart.zoneColor(seg.zone),
                            topLeft = Offset(x0, size.height - strip),
                            size = androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(1f), strip),
                        )
                    }
                }

                drawLine(Color.White, Offset(markerX, 0f), Offset(markerX, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(markerX, 4.dp.toPx()))
            }
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.fillMaxHeight().width(64.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start,
            ) {
                listOf(speedMax, speedMax / 2, 0.0).forEach {
                    Text(axis(it, " ${units.speedUnit}"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HudColors.speed)
                }
            }
        }

        // What comes next, kept small and centred so it never covers an axis label.
        val nextText = if (snapshot.nextSpeedKph != null) {
            "Next: %s · %.1f%%".format(units.speedText(snapshot.nextSpeedKph!!), snapshot.nextInclinePercent ?: 0.0)
        } else {
            "Last step"
        }
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            HudPill(text = nextText, tint = HudColors.speed, bg = Color(0x3338BDF8))
        }
    }
}

/** Height of the buttons in the bottom bar: tall enough to press without looking. */
private val HudButtonHeight = 54.dp

/**
 * Bottom Floating Action & Control Bar.
 */
@Composable
private fun HudBottomControls(
    runCoach: CoachChoice = CoachChoice.OFF,
    /** The saved coaches to choose from, the chosen one, and choosing (null: no coach). */
    coaches: List<SavedCoach> = emptyList(),
    activeCoachId: String? = null,
    onPickCoach: (SavedCoach?) -> Unit = {},
    coachSpeaks: Boolean = true,
    onCoachSpeaks: (Boolean) -> Unit = {},
    coachCaptions: Boolean = true,
    onCoachCaptions: (Boolean) -> Unit = {},
    coachVolume: Float = 1f,
    onCoachVolume: (Float) -> Unit = {},
    /** How fast the coach moves against the speed it is given: 1 = as is (user request 2026-11-24). */
    coachPace: Float = 1f,
    onCoachPace: (Float) -> Unit = {},
    /** How big the coach is drawn: 1 = as designed (user request 2026-11-24). */
    coachSize: Float = 1f,
    onCoachSize: (Float) -> Unit = {},
    /** Open the coach designer over the workout. */
    onDesignCoach: () -> Unit = {},
    snapshot: WorkoutEngineSnapshot,
    state: TreadmillState,
    preview: Boolean,
    hasVideo: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onAbandon: () -> Unit,
    onSelectVideo: () -> Unit,
    onTogglePreview: () -> Unit,
    onEmergencyStop: () -> Unit,
    onHide: () -> Unit = {},
    onSwapSides: (() -> Unit)? = null,
    coachOn: Boolean = false,
    coachLevel: String = "Normal",
    onCoachLevel: (String) -> Unit = {},
    onCoachStart: ((Int) -> Unit)? = null,
    onCoachStop: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    playbackRate: Float = 1f,
    onPlaybackRate: (Float) -> Unit = {},
    chosenRate: Float = playbackRate,
    matchSpeed: Boolean = false,
    onMatchSpeed: (Boolean) -> Unit = {},
    /** Skip intro and outro: null when the workout has no suggested start or end. */
    skipIntro: Boolean? = null,
    onSkipIntro: (Boolean) -> Unit = {},
    volume: Int = 100,
    onVolume: (Int) -> Unit = {},
    /** The video is muted because another app's music is playing; the volume setting is kept. */
    videoMutedForMusic: Boolean = false,
    onMusicStarts: () -> Unit = {},
    /** Give the sound back to the video now, which pauses the music. */
    onVideoSoundInstead: () -> Unit = {},
    hasVideoAudio: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HudColors.glassPanel)
            .border(BorderStroke(1.dp, HudColors.glassBorder), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left side: hide the overlay (R9a), then video and preview.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudSquareButton(
                icon = Icons.Filled.KeyboardArrowDown,
                description = "Hide the controls. Tap the video to bring them back.",
                enabled = true,
                onClick = onHide,
            )
            if (onSwapSides != null && !preview) {
                HudSquareButton(
                    icon = Icons.Filled.SwapHoriz,
                    description = "Swap speed and incline sides",
                    enabled = true,
                    onClick = onSwapSides,
                )
            }
            // The virtual coach: off, or a woman or a man running with you over the video.
            var coachMenu by remember { mutableStateOf(false) }
            // While a coach slider is dragged the menu turns see-through, so the coach shows clearly.
            var adjustingCoach by remember { mutableStateOf(false) }
            Box {
                HudSquareButton(
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    description = if (runCoach == CoachChoice.OFF) "Run with a coach" else "Coach is on",
                    enabled = true,
                    onClick = { coachMenu = true },
                )
                if (runCoach != CoachChoice.OFF) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF4ADE80)),
                    )
                }
                com.ofit.app.ui.theme.OfitDropdownMenu(
                    expanded = coachMenu,
                    onDismissRequest = { coachMenu = false; adjustingCoach = false },
                    seeThrough = adjustingCoach,
                ) {
                    val others = Modifier.alpha(if (adjustingCoach) 0f else 1f)
                    Column(others) {
                    com.ofit.app.ui.theme.OfitMenuHeader("Run with a coach")
                    // On/off switches (user request 2026-11-24).
                    CoachSwitchRow("Coach voice", "changes coming up, encouragement and facts about the place, out loud", coachSpeaks, onCoachSpeaks)
                    CoachSwitchRow("Captions", "what the coach says, written above the controls", coachCaptions, onCoachCaptions)
                    }
                    // The voice's own volume, always shown (user report 2026-11-24: it could not be
                    // found); it plays over the video and music without turning them down.
                    Column(Modifier.width(340.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            if (coachSpeaks) "Coach voice volume  ${(coachVolume * 100).toInt()} %" else "Coach voice volume (voice is off)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("the voice against the video and music", color = Color(0x99FFFFFF), fontSize = 12.sp)
                        androidx.compose.material3.Slider(
                            value = coachVolume,
                            onValueChange = { onCoachVolume((Math.round(it * 20) / 20f).coerceIn(0f, CoachVoice.MAX_VOLUME)) },
                            valueRange = 0f..CoachVoice.MAX_VOLUME,
                            steps = 49,
                            enabled = coachSpeaks,
                        )
                    }
                    if (runCoach != CoachChoice.OFF) {
                        // Pace: some videos are filmed faster or slower than they read, so the runner
                        // can match the coach's stride to the picture. Kept for this workout.
                        Column(Modifier.width(340.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "Coach pace  ${"%.2f".format(java.util.Locale.ROOT, coachPace)}x",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("match the coach's stride to the video", color = Color(0x99FFFFFF), fontSize = 12.sp)
                            androidx.compose.material3.Slider(
                                value = coachPace,
                                onValueChange = { adjustingCoach = true; onCoachPace((Math.round(it * 20) / 20f).coerceIn(0.5f, 2f)) },
                                onValueChangeFinished = { adjustingCoach = false },
                                valueRange = 0.5f..2f,
                                steps = 29,
                            )
                            Text(
                                "Coach size  ${(coachSize * 100).toInt()} %",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                            androidx.compose.material3.Slider(
                                value = coachSize,
                                onValueChange = { adjustingCoach = true; onCoachSize((Math.round(it * 20) / 20f).coerceIn(0.6f, 1.6f)) },
                                onValueChangeFinished = { adjustingCoach = false },
                                valueRange = 0.6f..1.6f,
                                steps = 19,
                            )
                        }
                    }
                    Column(others) {
                    com.ofit.app.ui.theme.OfitMenuItem(
                        text = "Design your coach",
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        detail = "make and save coaches: name, voice, outfit and looks",
                        onClick = { coachMenu = false; onDesignCoach() },
                    )
                    com.ofit.app.ui.theme.OfitMenuDivider()
                    // No coach, or one of the saved coaches (made in the coach designer).
                    com.ofit.app.ui.theme.OfitMenuItem(
                        text = "No coach" + if (runCoach == CoachChoice.OFF) "  ✓" else "",
                        icon = Icons.Filled.Close,
                        detail = "no coach on the video",
                        onClick = { coachMenu = false; onPickCoach(null) },
                    )
                    coaches.forEach { c ->
                        val chosen = runCoach != CoachChoice.OFF && c.id == activeCoachId
                        com.ofit.app.ui.theme.OfitMenuItem(
                            text = c.name + if (chosen) "  ✓" else "",
                            icon = Icons.AutoMirrored.Filled.DirectionsRun,
                            detail = (if (c.male) "man" else "woman") + " · runs ahead at your speed and cheers you on",
                            onClick = { coachMenu = false; onPickCoach(c) },
                        )
                    }
                    }
                }
            }
            if (onCoachStart != null && !preview) {
                // Hold a heart-rate zone (spec 003): pick a zone, or turn it off.
                var zoneMenu by remember { mutableStateOf(false) }
                Box {
                    HudSquareButton(
                        icon = Icons.Filled.Favorite,
                        description = if (coachOn) "Hold zone is on" else "Hold a heart-rate zone",
                        enabled = snapshot.isRunning || coachOn,
                        onClick = { zoneMenu = true },
                    )
                    com.ofit.app.ui.theme.OfitDropdownMenu(expanded = zoneMenu, onDismissRequest = { zoneMenu = false }) {
                        com.ofit.app.ui.theme.OfitMenuHeader("How hard to push")
                        com.ofit.app.ui.theme.OfitMenuSegments(listOf("Gentle", "Normal", "Strong"), coachLevel, onCoachLevel)
                        com.ofit.app.ui.theme.OfitMenuDivider()
                        com.ofit.app.ui.theme.OfitMenuHeader("Hold a heart-rate zone")
                        listOf("Easy", "Fat burn", "Aerobic", "Threshold", "Max").forEachIndexed { i, name ->
                            val zone = i + 1
                            com.ofit.app.ui.theme.OfitMenuItem(
                                text = "Zone $zone",
                                detail = name,
                                icon = Icons.Filled.Favorite,
                                onClick = {
                                    zoneMenu = false
                                    onCoachStart(zone)
                                },
                            )
                        }
                        if (coachOn) {
                            com.ofit.app.ui.theme.OfitMenuDivider()
                            com.ofit.app.ui.theme.OfitMenuItem(
                                text = "Turn off Hold zone",
                                icon = Icons.Filled.Stop,
                                destructive = true,
                                onClick = {
                                    zoneMenu = false
                                    onCoachStop()
                                },
                            )
                        }
                    }
                }
            }
            // Videos are attached in the editor or from video search, not here (user request).

            if (state.connectionState != ConnectionState.CONNECTED || preview) {
                // Switching mode ends a running workout, so it is only offered before Start.
                HudActionButton(
                    icon = if (preview) Icons.Filled.Check else Icons.Filled.Visibility,
                    label = "No treadmill",
                    background = if (preview) Color(0x33F59E0B) else Color(0x22FFFFFF),
                    tint = if (preview) HudColors.amber else Color.White,
                    enabled = !snapshot.isActive,
                    onClick = onTogglePreview,
                )
            }
        }

        // Right side: music, rewind, fast forward, playback speed and volume; then Emergency Stop.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Sound: music from other apps (play / pause / skip, volume) and the video's own sound, in one panel.
        val context = androidx.compose.ui.platform.LocalContext.current
        var musicOpen by remember { mutableStateOf(false) }
        val nowPlaying = rememberNowPlaying()
        // The song playing in the music app, beside the music button; tap opens the panel.
        nowPlaying?.let { NowPlayingChip(it, onClick = { musicOpen = true }) }
        Box {
            HudSquareButton(
                // One button for all sound (user request): your music and the video's own sound.
                icon = Icons.Filled.MusicNote,
                description = "Sound: your music and the video",
                enabled = true,
                onClick = { musicOpen = true },
            )
            com.ofit.app.ui.theme.OfitDropdownMenu(expanded = musicOpen, onDismissRequest = { musicOpen = false }) {
                val apps = remember { OutsideMusic.installed(context) }
                var target by remember { mutableStateOf(OutsideMusic.lastApp(context)) }
                val targetLabel = apps.firstOrNull { it.packageName == target }?.label
                com.ofit.app.ui.theme.OfitMenuHeader(
                    nowPlaying?.let { "Sound · ${it.app}" } ?: if (targetLabel != null) "Sound · $targetLabel" else "Sound",
                )
                if (nowPlaying != null) {
                    NowPlayingCard(nowPlaying, Modifier.padding(horizontal = 12.dp, vertical = 4.dp).width(420.dp))
                } else if (!OutsideMusic.hasAccess(context)) {
                    com.ofit.app.ui.theme.OfitMenuItem(
                        text = "Show the song playing",
                        icon = Icons.Filled.MusicNote,
                        detail = "allow OpenFit music in Android's notification access",
                        onClick = {
                            musicOpen = false
                            OutsideMusic.openAccessSettings(context)
                        },
                    )
                }
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HudSquareButton(Icons.Filled.SkipPrevious, "Previous track", true, {
                        OutsideMusic.key(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    })
                    HudSquareButton(if (nowPlaying?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause your music", true, {
                        OutsideMusic.key(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    })
                    HudSquareButton(Icons.Filled.SkipNext, "Next track", true, {
                        OutsideMusic.key(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                    })
                }
                // The coach's voice volume here too, with the other sounds (user report 2026-11-24).
                if (runCoach != CoachChoice.OFF && coachSpeaks) {
                    // With a mute button like the music and video rows (user request 2026-11-24).
                    var coachBeforeMute by remember { mutableStateOf(1f) }
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 2.dp).width(420.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HudSquareButton(
                            if (coachVolume <= 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            if (coachVolume <= 0f) "Unmute the coach" else "Mute the coach",
                            true,
                            {
                                if (coachVolume > 0f) {
                                    coachBeforeMute = coachVolume
                                    onCoachVolume(0f)
                                } else {
                                    onCoachVolume(coachBeforeMute.coerceAtLeast(0.5f))
                                }
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (coachVolume <= 0f) "Coach voice muted" else "Coach voice  ${(coachVolume * 100).toInt()} %",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                            androidx.compose.material3.Slider(
                                value = coachVolume,
                                onValueChange = { onCoachVolume((Math.round(it * 20) / 20f).coerceIn(0f, CoachVoice.MAX_VOLUME)) },
                                valueRange = 0f..CoachVoice.MAX_VOLUME,
                                steps = 49,
                            )
                        }
                    }
                }
                // Music volume: the tablet's media volume, which the music app plays at.
                var musicVolume by remember { mutableStateOf(OutsideMusic.volume(context)) }
                var beforeMute by remember { mutableStateOf(0.5f) }
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp).width(420.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudSquareButton(
                        if (musicVolume <= 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        if (musicVolume <= 0f) "Unmute music" else "Mute music",
                        true,
                        {
                            if (musicVolume > 0f) {
                                beforeMute = musicVolume
                                musicVolume = 0f
                            } else {
                                musicVolume = beforeMute.coerceAtLeast(0.2f)
                            }
                            OutsideMusic.setVolume(context, musicVolume)
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Music volume  ${(musicVolume * 100).toInt()} %", color = Color.White, fontWeight = FontWeight.SemiBold)
                        androidx.compose.material3.Slider(
                            value = musicVolume,
                            onValueChange = {
                                musicVolume = it
                                OutsideMusic.setVolume(context, it)
                            },
                        )
                    }
                }
                // The workout video's own sound, separate from the music.
                if (hasVideoAudio) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 2.dp).width(420.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val silent = volume <= 0 || videoMutedForMusic
                        HudSquareButton(
                            if (silent) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            if (silent) "Turn the video's sound on" else "Mute the video",
                            true,
                            {
                                when {
                                    videoMutedForMusic -> onVideoSoundInstead()
                                    volume <= 0 -> onVolume(60)
                                    else -> onVolume(0)
                                }
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    videoMutedForMusic -> "Video sound $volume %: muted while your music plays"
                                    volume <= 0 -> "Video sound off"
                                    else -> "Video sound  $volume %"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                            androidx.compose.material3.Slider(
                                value = volume / 100f,
                                onValueChange = { onVolume((it * 100).toInt()) },
                            )
                        }
                    }
                    // Android gives sound to one media app at a time: YouTube's player and a music app
                    // pause each other, so the video carries on muted while music plays (user report 2026-11-24).
                    Text(
                        if (videoMutedForMusic) {
                            "Your music has the sound. The slider sets the video's level for when the music stops; " +
                                "tap the speaker to hear the video instead (Android then pauses your music)."
                        } else {
                            "Android plays one app's sound at a time: while your music plays, the video runs muted. The coach's voice plays over both."
                        },
                        color = Color(0x99FFFFFF),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp).width(400.dp),
                    )
                }
                com.ofit.app.ui.theme.OfitMenuDivider()
                if (apps.isEmpty()) {
                    com.ofit.app.ui.theme.OfitMenuHeader("No music app found")
                } else {
                    // Small pills in a row (user request 2026-11-24), not a full-width item each.
                    com.ofit.app.ui.theme.OfitMenuHeader("Open a music app")
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.width(420.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        apps.forEach { app ->
                            val current = app.installed && app.packageName == target
                            Row(
                                Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (current) MaterialTheme.colorScheme.primary else Color(0x26FFFFFF))
                                    .clickable {
                                        musicOpen = false
                                        if (app.installed) {
                                            target = app.packageName
                                            // Mute the video so the music is heard; the workout carries on.
                                            if (hasVideoAudio) onMusicStarts()
                                        }
                                        OutsideMusic.open(context, app)
                                    }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (app.installed) Icons.Filled.MusicNote else Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = if (current) Color(0xFF111111) else Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (app.installed) app.label else "Get ${app.label}",
                                    color = if (current) Color(0xFF111111) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
        HudSquareButton(
            icon = Icons.Filled.FastRewind,
            description = "Back 30 seconds",
            enabled = snapshot.isActive,
            onClick = { onSeek(snapshot.totalElapsedMillis - 30_000) },
        )
        HudSquareButton(
            icon = Icons.Filled.FastForward,
            description = "Forward 30 seconds",
            enabled = snapshot.isActive,
            onClick = { onSeek(snapshot.totalElapsedMillis + 30_000) },
        )
        // Playback speed: a menu of rates, and "Match my speed" with a treadmill.
        var rateOpen by remember { mutableStateOf(false) }
        Box {
            Column(
                Modifier
                    .size(HudButtonHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (matchSpeed || playbackRate != 1f) Color(0x4438BDF8) else Color(0x22FFFFFF))
                    .border(BorderStroke(1.dp, Color(0x44FFFFFF)), RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Playback speed") { rateOpen = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(rateText(playbackRate), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (matchSpeed) Text("match", color = Color(0xCCFFFFFF), fontSize = 9.sp, lineHeight = 9.sp)
            }
            com.ofit.app.ui.theme.OfitDropdownMenu(expanded = rateOpen, onDismissRequest = { rateOpen = false }) {
                com.ofit.app.ui.theme.OfitMenuHeader("Video speed")
                com.ofit.app.ui.theme.OfitMenuSegments(
                    listOf("0.75×", "1×", "1.25×", "1.5×", "2×"),
                    rateText(chosenRate),
                ) { label -> onPlaybackRate(label.removeSuffix("×").toFloat()) }
                com.ofit.app.ui.theme.OfitMenuDivider()
                com.ofit.app.ui.theme.OfitMenuItem(
                    text = if (matchSpeed) "Match my speed: on" else "Match my speed",
                    detail = if (preview) "1× on plan; set a faster or slower speed with + and − to speed it up or down" else "1× on plan; faster or slower belt speeds it up or down",
                    icon = Icons.Filled.Speed,
                    onClick = {
                        onMatchSpeed(!matchSpeed)
                        rateOpen = false
                    },
                )
                if (skipIntro != null) {
                    com.ofit.app.ui.theme.OfitMenuItem(
                        text = if (skipIntro) "Skip intro and outro: on" else "Skip intro and outro: off",
                        detail = "start where the run starts and end where it ends",
                        icon = Icons.Filled.FastForward,
                        onClick = {
                            onSkipIntro(!skipIntro)
                            rateOpen = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
            // Start / Pause, Skip and End, right before E-STOP: anchored to the right edge so they never
            // move when the song chip or other buttons come and go (user request, 2026-11-24).
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val running = snapshot.state == WorkoutEngineState.RUNNING
                HudActionButton(
                    icon = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = when (snapshot.state) {
                        WorkoutEngineState.RUNNING -> "Pause"
                        WorkoutEngineState.PAUSED -> "Resume"
                        WorkoutEngineState.IDLE -> "Start"
                        // In the cool-down the belt is winding down: no restart from here (seen on
                        // the tablet 2026-11-24, it read "Again"); End or E-STOP stop it.
                        WorkoutEngineState.COOLING_DOWN -> "Cool-down"
                        else -> "Again"
                    },
                    background = if (running || snapshot.state == WorkoutEngineState.COOLING_DOWN) Color(0x33FFFFFF) else Color(0xFF2563EB),
                    enabled = snapshot.state != WorkoutEngineState.COOLING_DOWN,
                    onClick = when (snapshot.state) {
                        WorkoutEngineState.RUNNING -> onPause
                        WorkoutEngineState.PAUSED -> onResume
                        else -> onStart
                    },
                )
                HudActionButton(
                    icon = Icons.Filled.SkipNext,
                    label = "Skip",
                    enabled = snapshot.isActive,
                    onClick = onSkip,
                )
                HudActionButton(
                    icon = Icons.Filled.Stop,
                    label = "End",
                    background = Color(0x33F87171),
                    enabled = snapshot.isActive,
                    onClick = onAbandon,
                )
            }
        if (!preview) {
            HudActionButton(
                icon = Icons.Filled.Stop,
                label = "E-STOP",
                background = Color(0xFFDC2626),
                enabled = true,
                onClick = onEmergencyStop,
            )
        } else {
            // Keep E-STOP's place, so Start, Skip and End sit in the same spot with no treadmill too.
            Spacer(Modifier.size(HudActionSize))
        }
        }
    }
}

/** A square button with an icon and a short word under it: easy to hit on a moving belt. */
@Composable
private fun HudActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    background: Color = Color(0x22FFFFFF),
    tint: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .size(HudActionSize)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) background else Color(0x14FFFFFF))
            .border(BorderStroke(1.dp, Color(0x44FFFFFF)), RoundedCornerShape(14.dp))
            .let { if (enabled) it.clickable(onClickLabel = label, onClick = onClick) else it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (enabled) tint else Color(0x55FFFFFF)
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Text(
            label,
            color = color,
            fontSize = if (label.length > 8) 10.sp else 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** The size of the bar's main square buttons (the user wants about 72 dp, square). */
private val HudActionSize = 72.dp

/** A square glass button, the bottom bar's size, for an icon-only action. */
@Composable
private fun HudSquareButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(HudButtonHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
            .border(BorderStroke(1.dp, Color(0x44FFFFFF)), RoundedCornerShape(12.dp))
            .let { if (enabled) it.clickable(onClickLabel = description, onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color(0x55FFFFFF),
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * The overlay hidden (R9a): only the video, a small button to bring the controls back, and -
 * always - End and Emergency Stop (safety rule S1). Tapping the video anywhere also brings them back.
 */
@Composable
private fun HudHiddenOverlay(
    snapshot: WorkoutEngineSnapshot,
    onShow: () -> Unit,
    onAbandon: () -> Unit,
    onEmergencyStop: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    coachStatus: com.ofit.app.heart.CoachStatus? = null,
    onCoachStop: () -> Unit = {},
    noTreadmill: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClickLabel = "Show the controls",
                onClick = onShow,
            ),
    ) {
        // Spec 003 S6: the runner can always see that heart rate is driving the belt, and stop it.
        if (coachStatus != null) {
            Text(
                text = "♥ Holding zone ${coachStatus.zone} (tap to turn off)",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCCB71C1C))
                    .clickable(onClick = onCoachStop)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        HudSquareButton(
            icon = Icons.Filled.KeyboardArrowUp,
            description = "Show the controls",
            enabled = true,
            onClick = onShow,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Fullscreen keeps one workout button: pause or resume (End is in the full controls).
            if (snapshot.isActive) {
                val running = snapshot.state == WorkoutEngineState.RUNNING
                HudSquareButton(
                    icon = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    description = if (running) "Pause workout" else "Resume workout",
                    enabled = true,
                    onClick = if (running) onPause else onResume,
                )
            }
            if (!noTreadmill) {
                HudActionButton(
                    icon = Icons.Filled.Stop,
                    label = "E-STOP",
                    background = Color(0xFFDC2626),
                    enabled = true,
                    onClick = onEmergencyStop,
                )
            }
        }
    }
}

/**
 * Translucent Pill Badge.
 */
@Composable
private fun HudPill(
    text: String,
    tint: Color,
    bg: Color = Color(0x22FFFFFF),
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = tint,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Arrow icon indicating incline direction.
 */
@Composable
private fun inclineIcon(incline: Double?): ImageVector? = when {
    incline == null -> null
    incline > 0.4 -> Icons.AutoMirrored.Filled.TrendingUp
    incline < -0.4 -> Icons.AutoMirrored.Filled.TrendingDown
    else -> Icons.AutoMirrored.Filled.TrendingFlat
}

/**
 * Modal dialog for fine-grained speed/incline adjustments when tapping the value.
 */
@Composable
private fun HudOverrideDialog(
    title: String,
    value: Double,
    unit: String,
    min: Double,
    max: Double,
    deltas: List<Double>,
    manualOverride: Boolean,
    onApply: (Double) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "%.1f %s".format(value, unit),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    deltas.forEach { delta ->
                        val next = ((value + delta) * 10.0).roundToInt() / 10.0
                        val clamped = next.coerceIn(min, max)
                        OutlinedButton(
                            onClick = { onApply(clamped) },
                            enabled = abs(clamped - value) > 0.001,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (delta > 0) "+%.1f".format(delta) else "%.1f".format(delta))
                        }
                    }
                }
                Text(
                    text = "Range %.1f to %.1f %s".format(min, max, unit),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            if (manualOverride) {
                TextButton(onClick = onReset) {
                    Text("Rejoin program")
                }
            }
        },
    )
}

/**
 * Design tokens and colours for the workout screen.
 */
object HudColors {
    /** Translucent dark glass panel with subtle tint */
    val glassPanel = Color(0xB30A0E17) // ~70% opaque slate-950

    /** Subtle frosted glass border */
    val glassBorder = Color(0x2EFFFFFF) // 18% white

    /** Electric cyan accent for Speed & Pace */
    val speed = Color(0xFF38BDF8) // sky-400

    /** Vibrant amber accent for Incline & Topography */
    val incline = Color(0xFFFB923C) // orange-400

    /** Warning/Preview amber */
    val amber = Color(0xFFFBBF24) // amber-400
}

/** "1×", "1.25×", "0.75×" … for a playback rate. */
private fun rateText(rate: Float): String {
    val r = Math.round(rate * 100) / 100.0
    val text = if (r % 1.0 == 0.0) "%.0f".format(r) else if ((r * 10) % 1.0 == 0.0) "%.1f".format(r) else "%.2f".format(r)
    return text + "×"
}

/** A small pill with the album art and the song, for the workout bar. */
@Composable
private fun NowPlayingChip(now: OutsideMusic.NowPlaying, onClick: () -> Unit) {
    Row(
        Modifier
            .height(HudButtonHeight)
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
            .border(BorderStroke(1.dp, Color(0x44FFFFFF)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(now, Modifier.size(HudButtonHeight - 12.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.padding(end = 6.dp)) {
            Text(now.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            now.artist?.let {
                Text(it, color = Color(0xB3FFFFFF), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

/** The song in the music panel: big art, title, artist, the app. */
@Composable
private fun NowPlayingCard(now: OutsideMusic.NowPlaying, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AlbumArt(now, Modifier.size(72.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(now.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            now.artist?.let { Text(it, color = Color(0xCCFFFFFF), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            Text(if (now.playing) "Playing in ${now.app}" else "Paused in ${now.app}", color = Color(0x99FFFFFF), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlbumArt(now: OutsideMusic.NowPlaying, modifier: Modifier) {
    val art = now.art
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
        if (art != null) {
            androidx.compose.foundation.Image(
                art.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White)
        }
    }
}

/** A standard caption: the coach's name and line, white on a dark band, at most three lines. */
@Composable
fun CoachCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 26.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        maxLines = 3,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 900.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun CoachSwitchRow(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .width(340.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color(0x99FFFFFF), fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}
