package com.ofit.app.ui

import androidx.compose.material3.Icon

import androidx.compose.material.icons.automirrored.filled.DirectionsRun

import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected

import androidx.compose.material.icons.filled.Menu
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ofit.app.CrashLog
import com.ofit.app.TreadmillController
import com.ofit.app.library.LibraryScreen
import com.ofit.app.library.VideoSearchScreen
import com.ofit.app.settings.LocalUnits
import com.ofit.protocol.UnitSystem
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.CompositionLocalProvider
import com.ofit.app.workout.AppScreen
import com.ofit.app.workout.WorkoutEditorScreen
import com.ofit.app.workout.VisualEditorScreen
import com.ofit.app.workout.WorkoutListScreen
import com.ofit.app.workout.WorkoutRunScreen
import com.ofit.protocol.ConnectionState
import com.ofit.protocol.MachineReadiness
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.TreadmillState
import com.ofit.protocol.WorkoutMode
import com.ofit.transport.Scanner

/**
 * The whole application.
 *
 * The app is deliberately small, so a plain screen model is clearer than a navigation graph.
 * The controller is the single source of truth: it owns the connection, the workout programs, and
 * the running program.
 */
@Composable
fun OfitApp(
    controller: TreadmillController,
    screen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    onScan: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val message by controller.message.collectAsStateWithLifecycle()
    val devices by controller.devices.collectAsStateWithLifecycle()
    val scanning by controller.scanning.collectAsStateWithLifecycle()
    val limits by controller.limits.collectAsStateWithLifecycle()
    val workouts by controller.workouts.workouts.collectAsStateWithLifecycle()
    val engine by controller.workouts.snapshot.collectAsStateWithLifecycle()
    val preview by controller.workouts.preview.collectAsStateWithLifecycle()
    val units by controller.settings.units.collectAsStateWithLifecycle()
    val videoResults by controller.library.results.collectAsStateWithLifecycle()
    val videoSearching by controller.library.searching.collectAsStateWithLifecycle()
    val videoSearchError by controller.library.searchError.collectAsStateWithLifecycle()
    val analysis by controller.library.analysis.collectAsStateWithLifecycle()
    val libraryEntries by controller.library.entries.collectAsStateWithLifecycle()
    val libraryLoading by controller.library.loading.collectAsStateWithLifecycle()
    val libraryError by controller.library.libraryError.collectAsStateWithLifecycle()
    val heartRate by controller.heart.shown.collectAsStateWithLifecycle()
    val heartSummaryOnly by controller.heart.summary.collectAsStateWithLifecycle()
    val estimatedCalories by controller.heart.calories.collectAsStateWithLifecycle()
    val playbackRate by controller.workouts.effectiveRate.collectAsStateWithLifecycle()
    val chosenRate by controller.workouts.playbackRate.collectAsStateWithLifecycle()
    val matchSpeed by controller.workouts.matchSpeed.collectAsStateWithLifecycle()
    val heartSummary = heartSummaryOnly.copy(calories = estimatedCalories)
    val hudTiles by controller.settings.hudTiles.collectAsStateWithLifecycle()
    val hudSwapped by controller.settings.hudSwapped.collectAsStateWithLifecycle()
    val historyEntries by controller.history.entries.collectAsStateWithLifecycle()
    val coachStatus by controller.coach.status.collectAsStateWithLifecycle()
    val coachNotice by controller.coach.notice.collectAsStateWithLifecycle()
    val coachLevel by controller.heart.coachLevel.collectAsStateWithLifecycle()
    val hasGeminiKey by controller.secrets.hasGeminiKey.collectAsStateWithLifecycle()
    val startProblem by controller.workouts.startProblem.collectAsStateWithLifecycle()
    val profileList by controller.profiles.profiles.collectAsStateWithLifecycle()
    val currentProfile by controller.profiles.current.collectAsStateWithLifecycle()
    val heartAge by controller.heart.age.collectAsStateWithLifecycle()
    val heartMaxHr by controller.heart.maxHr.collectAsStateWithLifecycle()
    val heartWeight by controller.heart.weightKg.collectAsStateWithLifecycle()
    val heartSex by controller.heart.sex.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    // A topic chosen on Browse, so the search screen opens with it filled in.
    var videoTopic by remember { mutableStateOf("") }
    // Where the Gemini key screen was opened from, so Back returns there.
    var aiKeyFrom by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    // Where the video search was opened from, so Back returns there (Browse, Home or My workouts).
    var searchFrom by remember { mutableStateOf<AppScreen>(AppScreen.Workouts) }
    fun openSearch() {
        if (screen != AppScreen.VideoSearch) searchFrom = screen
        onNavigate(AppScreen.VideoSearch)
    }

    // "Quit workout?": asked before a running workout is ended, from End, Back or the screen's back.
    var askQuit by remember { mutableStateOf(false) }

    // System Back goes up one level instead of closing the app. While a workout is running it does
    // nothing: leaving the screen would hide the controls of a moving belt, so the workout has to
    // be ended (or paused) on screen first.
    BackHandler(enabled = screen != AppScreen.Home) {
        when (screen) {
            AppScreen.Home -> Unit
            AppScreen.Workouts -> onNavigate(AppScreen.Home)
            AppScreen.HeartRate -> {
                controller.heart.stopScan()
                onNavigate(AppScreen.Home)
            }
            AppScreen.History -> onNavigate(AppScreen.Home)
            AppScreen.ManualControl -> onNavigate(AppScreen.Home)
            AppScreen.Legal -> onNavigate(AppScreen.Home)
            AppScreen.QuickStart -> onNavigate(AppScreen.Home)
            AppScreen.AiKey -> onNavigate(AppScreen.Home)
            AppScreen.Profiles -> onNavigate(AppScreen.Home)
            AppScreen.Browse -> onNavigate(AppScreen.Home)
            AppScreen.CoachDesigner -> onNavigate(AppScreen.Home)
            AppScreen.Plans -> onNavigate(AppScreen.Home)
            AppScreen.VideoSearch -> {
                controller.library.clearAnalysis()
                onNavigate(searchFrom)
            }
            is AppScreen.Run -> if (engine.isActive) askQuit = true else onNavigate(AppScreen.Workouts)
            is AppScreen.Summary -> onNavigate(AppScreen.Home)
            else -> onNavigate(AppScreen.Workouts)
        }
    }

    // Re-read the saved workouts each time the list is shown, so a file added or changed outside
    // the app (or by another screen) appears without a restart.
    androidx.compose.runtime.LaunchedEffect(screen) {
        if (screen == AppScreen.Workouts) controller.workouts.refresh()
    }

    // The safety and legal notice comes before anything else, until accepted.
    val legalAccepted by controller.settings.legalAccepted.collectAsStateWithLifecycle()
    if (!legalAccepted) {
        DisclaimerGate(onAccept = controller.settings::acceptLegal)
        return
    }

    CompositionLocalProvider(LocalUnits provides units) {
    // OpenFit was closed during a workout and the belt is still going: offer to stop it.
    val leftRunning by controller.leftRunning.collectAsStateWithLifecycle()
    if (leftRunning && state.connectionState == com.ofit.protocol.ConnectionState.CONNECTED && controller.beltMoving(state)) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("The treadmill is still running") },
            text = {
                Text(
                    "OpenFit closed during a workout and the belt kept going at " +
                        units.speedText(state.displayedSpeedKph ?: 0.0) + ". Stop it now?",
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { controller.stopLeftRunning() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFDC2626)),
                ) { Text("Stop the belt") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { controller.clearLeftRunning() }) { Text("Leave it running") }
            },
        )
    }
    // The menu opens from every screen except the workout itself and "Who's running?".
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val drawerScope = androidx.compose.runtime.rememberCoroutineScope()
    val fullscreenSetting by controller.settings.fullscreen.collectAsStateWithLifecycle()
    // The menu is on every screen (user request). From a running workout, leaving asks first.
    val menuAllowed = true
    val openMenu: (() -> Unit)? = if (menuAllowed) ({ drawerScope.launch { drawerState.open() } }) else null
    // A menu left open while going to the workout screen (which has no menu) drew nothing but still
    // caught every tap, so Start, the menu and Hide did nothing. Shut it whenever the screen changes.
    androidx.compose.runtime.LaunchedEffect(screen) {
        if (drawerState.currentValue != androidx.compose.material3.DrawerValue.Closed) {
            drawerState.snapTo(androidx.compose.material3.DrawerValue.Closed)
        }
    }
    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = screen !is AppScreen.Run, // no edge swipe over the workout controls
        drawerContent = {
            if (menuAllowed) {
                AppDrawerContent(
                    current = screen,
                    profile = currentProfile,
                    units = units,
                    fullscreen = fullscreenSetting,
                    onGo = { destination ->
                        drawerScope.launch { drawerState.close() }
                        when {
                            screen is AppScreen.Run && engine.isActive && destination != screen -> askQuit = true
                            destination == screen -> Unit
                            destination == AppScreen.VideoSearch -> {
                                videoTopic = ""
                                openSearch()
                            }
                            else -> {
                                if (destination == AppScreen.Workouts) controller.workouts.refresh()
                                onNavigate(destination)
                            }
                        }
                    },
                    onUnits = controller.settings::setUnits,
                    onFullscreen = controller.settings::setFullscreen,
                )
            }
        },
    ) {
    CompositionLocalProvider(LocalOpenMenu provides openMenu) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.Home -> HomeScreen(
                state = state,
                message = message,
                devices = devices,
                scanning = scanning,
                limits = limits,
                controller = controller,
                crash = crash,
                onDismissCrash = {
                    crash = null
                    CrashLog.clear(context)
                },
                onScan = onScan,
                onOpenWorkouts = { onNavigate(AppScreen.Workouts) },
                units = units,
                onUnits = controller.settings::setUnits,
                onHeartRate = { onNavigate(AppScreen.HeartRate) },
                onHistory = { onNavigate(AppScreen.History) },
                profile = currentProfile,
                onProfiles = { onNavigate(AppScreen.Profiles) },
                onBrowse = { onNavigate(AppScreen.Browse) },
                onNavigate = { destination ->
                    if (destination == AppScreen.Workouts) controller.workouts.refresh()
                    if (destination == AppScreen.VideoSearch) {
                        videoTopic = ""
                        openSearch()
                    } else {
                        onNavigate(destination)
                    }
                },
            )

            AppScreen.CoachDesigner -> com.ofit.app.workout.CoachDesignerScreen(onBack = { onNavigate(AppScreen.Home) })

            AppScreen.AiKey -> com.ofit.app.settings.AiKeyScreen(
                secrets = controller.secrets,
                onBack = {
                    onNavigate(aiKeyFrom)
                    aiKeyFrom = AppScreen.Home
                },
            )

            AppScreen.Plans -> com.ofit.app.workout.PlansScreen(
                profileKey = controller.profiles.fileName("plan", "json"),
                history = controller.history.entries.collectAsState().value,
                onStart = { program ->
                    controller.workouts.clearFinished()
                    onNavigate(AppScreen.Run(planOneOff(program, limits)))
                },
                onBack = { onNavigate(AppScreen.Home) },
            )

            AppScreen.QuickStart -> com.ofit.app.workout.QuickStartScreen(
                limits = limits,
                body = controller.heart.body,
                onGo = { program ->
                    // Not saved to My workouts: a one-off. History still records it when run.
                    val oneOff = com.ofit.app.data.SavedWorkout(
                        id = "quick-start",
                        program = program,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                    controller.workouts.clearFinished()
                    onNavigate(AppScreen.Run(oneOff))
                },
                onBack = { onNavigate(AppScreen.Home) },
            )

            AppScreen.Legal -> LegalScreen(
                versionName = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "",
                onBack = { onNavigate(AppScreen.Home) },
            )

            AppScreen.Profiles -> com.ofit.app.profile.ProfileSelectionScreen(
                profiles = profileList,
                current = currentProfile,
                onSelect = { profile ->
                    controller.profiles.select(profile)
                    onNavigate(AppScreen.Home)
                },
                onAdd = { name, color, accent, icon -> controller.profiles.add(name, color, accent, icon) },
                onUpdate = controller.profiles::update,
                onRemove = controller.profiles::remove,
                health = com.ofit.app.profile.HealthDetails(
                    ageYears = heartAge,
                    maxHr = heartMaxHr,
                    weightKg = heartWeight,
                    sex = heartSex,
                    units = units,
                ),
                onHealth = { h ->
                    controller.heart.setAge(h.ageYears)
                    controller.heart.setMaxHr(h.maxHr)
                    controller.heart.setWeightKg(h.weightKg)
                    controller.heart.setSex(h.sex)
                    controller.settings.setUnits(h.units)
                },
            )

            AppScreen.Browse -> com.ofit.app.browse.BrowseScreen(
                workouts = workouts,
                community = libraryEntries,
                communityLoading = libraryLoading,
                history = historyEntries,
                youtubeIdFor = controller.workouts::youtubeIdFor,
                onLoadCommunity = { controller.library.loadLibrary(true) },
                onRun = { saved ->
                    controller.workouts.clearFinished()
                    onNavigate(AppScreen.Run(saved))
                },
                onSaveCommunity = controller.library::save,
                onFindVideo = {
                    videoTopic = ""
                    openSearch()
                },
                onBack = { onNavigate(AppScreen.Home) },
                onFindTopic = { query ->
                    videoTopic = query
                    controller.library.search(query)
                    openSearch()
                },
            )

            AppScreen.ManualControl -> {
                // Opening manual control connects, like a workout screen (spec 007).
                androidx.compose.runtime.DisposableEffect(Unit) {
                    controller.setWorkoutScreenOpen(true)
                    onDispose { controller.setWorkoutScreenOpen(false) }
                }
                ManualControlScreen(
                    state = state,
                    message = message,
                    devices = devices,
                    scanning = scanning,
                    limits = limits,
                    controller = controller,
                    onScan = onScan,
                    onOpenWorkouts = { onNavigate(AppScreen.Workouts) },
                    onBack = { onNavigate(AppScreen.Home) },
                )
            }

            AppScreen.History -> com.ofit.app.history.HistoryScreen(
                onRate = { e, stars, feel -> controller.history.rate(e.startedAtMillis, stars, feel) },
                entries = historyEntries,
                onDelete = controller.history::delete,
                onBack = { onNavigate(AppScreen.Home) },
                workouts = workouts,
                youtubeIdFor = controller.workouts::youtubeIdFor,
                onRepeat = { saved ->
                    controller.workouts.clearFinished()
                    onNavigate(AppScreen.Run(saved))
                },
            )

            // Devices: the treadmill and the heart-rate strap in one place (user request).
            AppScreen.HeartRate -> DevicesScreen(
                state = state,
                devices = devices,
                scanning = scanning,
                controller = controller,
                onScan = onScan,
                onBack = {
                    controller.heart.stopScan()
                    onNavigate(AppScreen.Home)
                },
            )

            AppScreen.Workouts -> WorkoutListScreen(
                workouts = workouts,
                onNew = { template ->
                    onNavigate(
                        AppScreen.VisualEditor(
                            savedId = null,
                            name = if (template.name == "Blank") "New workout" else template.name,
                            steps = template.steps,
                            videoId = null,
                        ),
                    )
                },
                onDuplicate = { saved -> controller.workouts.duplicate(saved) },
                youtubeIdFor = controller.workouts::youtubeIdFor,
                onEdit = { saved ->
                    onNavigate(
                        AppScreen.VisualEditor(
                            savedId = saved.id,
                            name = saved.program.name,
                            steps = saved.program.steps,
                            videoId = controller.workouts.youtubeIdFor(saved.id),
                            program = saved.program,
                        ),
                    )
                },
                onDelete = { saved -> controller.workouts.delete(saved.id) },
                onRun = { saved ->
                    controller.workouts.clearFinished()
                    onNavigate(AppScreen.Run(saved))
                },
                onFindVideo = { openSearch() },
                onLibrary = { onNavigate(AppScreen.Library) },
                onShare = { saved -> controller.library.share(saved) },
                onBack = { onNavigate(AppScreen.Home) },
            )

            AppScreen.VideoSearch -> VideoSearchScreen(
                results = videoResults,
                searching = videoSearching,
                error = videoSearchError,
                analysis = analysis,
                onSearch = controller.library::search,
                onMakeWorkout = controller.library::analyse,
                onKeep = { done, share ->
                    controller.library.keep(done, share)
                    onNavigate(AppScreen.Workouts)
                },
                onDiscard = controller.library::clearAnalysis,
                initialQuery = videoTopic,
                hasOwnKey = hasGeminiKey,
                onMakeManual = { video ->
                    controller.library.clearAnalysis()
                    onNavigate(
                        AppScreen.VisualEditor(
                            savedId = null,
                            name = video.title.take(60),
                            steps = com.ofit.protocol.ProgramEdit.forVideo(video.durationSeconds, 4.8, 0.0),
                            videoId = video.id,
                        ),
                    )
                },
                onAddKey = {
                    aiKeyFrom = AppScreen.VideoSearch
                    onNavigate(AppScreen.AiKey)
                },
                onBack = {
                    controller.library.clearAnalysis()
                    onNavigate(searchFrom)
                },
            )

            AppScreen.Library -> LibraryScreen(
                entries = libraryEntries,
                loading = libraryLoading,
                error = libraryError,
                onLoad = controller.library::loadLibrary,
                onSave = controller.library::save,
                onRate = controller.library::rate,
                onReport = controller.library::report,
                onBack = { onNavigate(AppScreen.Workouts) },
                savedNames = workouts.map { it.program.name }.toSet(),
            )

            is AppScreen.Editor -> WorkoutEditorScreen(
                saved = screen.saved,
                limits = limits,
                onSave = { program ->
                    controller.workouts.save(program)
                    onNavigate(AppScreen.Workouts)
                },
                onCancel = { onNavigate(AppScreen.Workouts) },
            )

            is AppScreen.VisualEditor -> VisualEditorScreen(
                initialName = screen.name,
                initialSteps = screen.steps,
                videoId = screen.videoId,
                initialVideoFile = screen.savedId?.let(controller.workouts::videoFileFor),
                initialProgram = screen.program,
                limits = limits,
                onSave = { program, videoId, videoFile ->
                    controller.workouts.saveEdited(screen.savedId, program, videoId, videoFile)
                    onNavigate(AppScreen.Workouts)
                },
                onBack = { onNavigate(AppScreen.Workouts) },
            )

            is AppScreen.Summary -> WorkoutSummaryScreen(
                entry = com.ofit.protocol.SummaryLookup.find(historyEntries, screen.name, screen.startedAfterMillis),
                onRate = { e, stars, feel -> controller.history.rate(e.startedAtMillis, stars, feel) },
                name = screen.name,
                onRepeat = workouts.firstOrNull { it.program.name == screen.name }?.let { saved ->
                    {
                        controller.workouts.clearFinished()
                        onNavigate(AppScreen.Run(saved))
                    }
                },
                onDone = { onNavigate(AppScreen.Home) },
                onHistory = { onNavigate(AppScreen.History) },
                // Saved by default; "Don't save" takes it out of History (and Health Connect) again.
                onDiscard = { e ->
                    controller.history.delete(e)
                    onNavigate(AppScreen.Home)
                },
            )

            is AppScreen.Run -> {
                if (askQuit) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { askQuit = false },
                        title = { Text(if (engine.isCoolingDown) "End the cool-down?" else "End workout?") },
                        text = {
                            Text(
                                when {
                                    engine.isCoolingDown -> "The belt stops now. The workout is saved to History."
                                    engine.isRunning && !preview -> "A 10-minute cool-down starts: the belt slows to an easy walk, then stops. " +
                                        "For an instant stop, use E-STOP."
                                    else -> "The workout ends here and is saved to History if it ran at least a minute."
                                },
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.Button(
                                onClick = { askQuit = false; controller.workouts.end() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFDC2626)),
                            ) {
                                Text(
                                    when {
                                        engine.isCoolingDown -> "Stop the belt"
                                        engine.isRunning && !preview -> "Start cool-down"
                                        else -> "End workout"
                                    },
                                )
                            }
                        },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { askQuit = false }) { Text("Keep going") } },
                    )
                }
                // When a workout ends (finished or quit), go to its summary.
                var wasActive by remember(screen.saved.id) { mutableStateOf(false) }
                // When the workout began, by the wall clock (not worked out from the program clock,
                // which runs slower or faster than real time at a matched video rate).
                var startedWall by remember(screen.saved.id) { mutableStateOf(0L) }
                androidx.compose.runtime.LaunchedEffect(engine.isActive, engine.isFinished) {
                    if (engine.isActive) {
                        if (!wasActive) startedWall = System.currentTimeMillis() - engine.totalElapsedMillis
                        wasActive = true
                    } else if (wasActive && (engine.isFinished || engine.state == com.ofit.protocol.WorkoutEngineState.ABANDONED)) {
                        wasActive = false
                        // The workout has stopped (finished, ended after the cool-down, or E-STOP):
                        // the runner's music stops with it (user request 2026-11-24).
                        com.ofit.app.workout.OutsideMusic.pause(context)
                        val since = startedWall.takeIf { it > 0 } ?: (System.currentTimeMillis() - engine.totalElapsedMillis)
                        onNavigate(AppScreen.Summary(since, screen.saved.program.name))
                    }
                }
                // Spec 007: the treadmill is connected while the workout screen is open, and let go
                // of when it closes and nothing is running.
                androidx.compose.runtime.DisposableEffect(Unit) {
                    controller.setWorkoutScreenOpen(true)
                    onDispose { controller.setWorkoutScreenOpen(false) }
                }
                // Intensity (P7): the profile's level, changeable before Start; the workout keeps its name.
                val intensityKey = controller.profiles.fileName("intensity", "json")
                var intensity by remember(screen.saved.id, intensityKey) { mutableStateOf(com.ofit.app.workout.IntensityStore.load(context, intensityKey)) }
                val intensitySuggestion = remember(screen.saved.id, intensity) {
                    com.ofit.protocol.Intensity.suggest(controller.history.entries.value, screen.saved.program.name, intensity)
                }
                val runSaved = remember(screen.saved, intensity, limits) {
                    if (intensity == com.ofit.protocol.Intensity.Level.STANDARD) screen.saved
                    else screen.saved.copy(program = com.ofit.protocol.Intensity.apply(screen.saved.program, intensity).fitTo(limits))
                }
                WorkoutRunScreen(
                saved = runSaved,
                intensity = intensity,
                intensitySuggestion = intensitySuggestion,
                onIntensity = { level ->
                    intensity = level
                    com.ofit.app.workout.IntensityStore.save(context, intensityKey, level)
                },
                resumeAtMillis = screen.resumeAtMillis,
                snapshot = engine,
                state = state,
                limits = limits,
                preview = preview,
                onPreviewChange = { on -> controller.workouts.setPreview(on) },
                onNoTreadmill = controller::setNoTreadmill,
                onVideoHeld = controller.workouts::setVideoHeld,
                startProblem = startProblem,
                onStart = {
                    if (preview) controller.workouts.start(runSaved.program)
                    else controller.whenConnected { controller.workouts.start(runSaved.program) }
                },
                onPause = { controller.workouts.pause() },
                onResume = { controller.workouts.resume() },
                onSkip = { controller.workouts.skip() },
                // Ending the cool-down needs no question: the belt is already at an easy walk (user
                // request 2026-11-24). Ending the workout itself still asks.
                onAbandon = { if (engine.isCoolingDown) controller.workouts.end() else askQuit = true },
                onSeek = controller.workouts::seekTo,
                playbackRate = playbackRate,
                onPlaybackRate = controller.workouts::setPlaybackRate,
                chosenRate = chosenRate,
                matchSpeed = matchSpeed,
                onMatchSpeed = controller.workouts::setMatchSpeed,
                onOverrideSpeed = controller.workouts::overrideSpeed,
                onOverrideIncline = controller.workouts::overrideIncline,
                onResetToProgram = controller.workouts::resetToProgram,
                onResetSpeed = controller.workouts::resetSpeed,
                onResetIncline = controller.workouts::resetIncline,
                // E-STOP: stop the belt at once and end the program (no cool-down).
                onEmergencyStop = {
                    controller.stopWorkout()
                    controller.workouts.abandon()
                },
                onBack = { if (engine.isActive) askQuit = true else onNavigate(AppScreen.Workouts) },
                heartRate = heartRate,
                heartSummary = heartSummary,
                tiles = hudTiles,
                onTilesChange = controller.settings::setHudTiles,
                swapSides = hudSwapped,
                onSwapSides = controller.settings::toggleHudSwapped,
                coachStatus = coachStatus,
                coachNotice = coachNotice,
                onCoachStart = controller.coach::start,
                onCoachStop = { controller.coach.stop("Hold zone is off.") },
                coachLevel = coachLevel.label,
                onCoachLevel = { label ->
                    com.ofit.protocol.HeartRateCoach.Aggressiveness.entries.firstOrNull { it.label == label }
                        ?.let(controller.heart::setCoachLevel)
                },
            )
            }
        }
    }
    }
    }
    }
}

/**
 * The home screen: find the treadmill and drive it by hand.
 */
@Composable
private fun HomeScreen(
    state: TreadmillState,
    message: String?,
    devices: List<Scanner.Device>,
    scanning: Boolean,
    limits: SafetyLimits,
    controller: TreadmillController,
    crash: String?,
    onDismissCrash: () -> Unit,
    onScan: () -> Unit,
    onOpenWorkouts: () -> Unit,
    units: UnitSystem,
    onUnits: (UnitSystem) -> Unit,
    onHeartRate: () -> Unit,
    onHistory: () -> Unit,
    profile: com.ofit.app.profile.Profile,
    onProfiles: () -> Unit,
    onBrowse: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Below the status bar: the title was drawn under the clock.
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The hamburger menu: every screen, units and fullscreen.
            MenuButton()
            Spacer(Modifier.padding(start = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "OpenFit",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Hi, ${profile.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color(0x99FFFFFF),
                )
            }
            Button(onClick = onBrowse) { Text("Browse workouts") }
            Spacer(Modifier.padding(start = 12.dp))
            // Tap the avatar to switch who is running.
            androidx.compose.foundation.layout.Box(
                Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onProfiles),
            ) {
                com.ofit.app.profile.ProfileAvatar(profile, 48.dp)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (crash != null) {
            CrashCard(crash = crash, onDismiss = onDismissCrash)
            Spacer(Modifier.height(12.dp))
        }

        if (message != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        val historyEntries by controller.history.entries.collectAsStateWithLifecycle()
        val savedWorkouts by controller.workouts.workouts.collectAsStateWithLifecycle()
        val communityEntries by controller.library.entries.collectAsStateWithLifecycle()
        // A workout OpenFit was running when it closed (P4), if worth picking up again.
        val homeContext = androidx.compose.ui.platform.LocalContext.current
        var resumeOffer by remember(savedWorkouts) {
            mutableStateOf(
                com.ofit.app.workout.ResumeStore.load(homeContext)
                    ?.takeIf { it.worthOffering(System.currentTimeMillis()) }
                    ?.let { point -> savedWorkouts.firstOrNull { it.id == point.workoutId }?.let { it to point } },
            )
        }
        val planKey = controller.profiles.fileName("plan", "json")
        HomeDashboard(
            state = state,
            treadmillName = if (controller.demo.collectAsState().value) "Demo treadmill" else controller.savedDevice()?.name,
            history = historyEntries,
            workouts = savedWorkouts,
            onConnect = controller::connectToSaved,
            onFindTreadmill = { onNavigate(AppScreen.HeartRate) },
            onOpenWorkout = { saved ->
                controller.workouts.clearFinished()
                onNavigate(AppScreen.Run(saved))
            },
            resume = resumeOffer,
            plan = remember(historyEntries, planKey) {
                com.ofit.app.workout.PlanStore.load(homeContext, planKey)?.let { com.ofit.protocol.TrainingPlans.status(it, historyEntries) }
            },
            onPlanStart = { program ->
                controller.workouts.clearFinished()
                onNavigate(AppScreen.Run(planOneOff(program, limits)))
            },
            onPlans = { onNavigate(AppScreen.Plans) },
            onResume = { saved, at ->
                resumeOffer = null
                controller.workouts.clearFinished()
                onNavigate(AppScreen.Run(saved, resumeAtMillis = at))
            },
            onDismissResume = {
                resumeOffer = null
                com.ofit.app.workout.ResumeStore.clear(homeContext)
            },
            onBrowse = onBrowse,
            onMyWorkouts = onOpenWorkouts,
            onFindVideo = { onNavigate(AppScreen.VideoSearch) },
            onHistory = { onNavigate(AppScreen.History) },
            onManual = { onNavigate(AppScreen.ManualControl) },
            onQuickStart = { onNavigate(AppScreen.QuickStart) },
            youtubeIdFor = controller.workouts::youtubeIdFor,
            onEdit = { saved ->
                onNavigate(
                    AppScreen.VisualEditor(
                        savedId = saved.id,
                        name = saved.program.name,
                        steps = saved.program.steps,
                        videoId = controller.workouts.youtubeIdFor(saved.id),
                        program = saved.program,
                    ),
                )
            },
            community = communityEntries,
            onLoadCommunity = { controller.library.loadLibrary(false) },
            onTemplate = { template ->
                onNavigate(
                    AppScreen.VisualEditor(
                        savedId = null,
                        name = if (template.name == "Blank") "New workout" else template.name,
                        steps = template.steps,
                        videoId = null,
                    ),
                )
            },
        )
    }
}

/**
 * The last crash, shown on the home screen.
 *
 * The trace is selectable so that it can be copied and sent on, which is the only practical way
 * to diagnose a crash on a phone with no debugger attached.
 */
@Composable
private fun CrashCard(crash: String, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "The app closed unexpectedly last time.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = crash,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

/**
 * The connect screen: find the treadmill and take control of it.
 */
@Composable
private fun ConnectScreen(
    devices: List<Scanner.Device>,
    scanning: Boolean,
    onScan: () -> Unit,
    onConnect: (Scanner.Device) -> Unit,
    onOpenWorkouts: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onScan, enabled = !scanning) {
            Text(if (scanning) "Looking for the treadmill..." else "Look for the treadmill")
        }

        Spacer(Modifier.height(12.dp))

        if (devices.isEmpty()) {
            Text(
                text = "Turn the treadmill on and close any other fitness app that may be connected to it, then look for " +
                    "it. Every Bluetooth device nearby is listed, so choose the one named I_TL. " +
                    "No pairing is needed.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    Card(
                        onClick = { onConnect(device) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (device.isConsole) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = buildString {
                                    if (device.isConsole) append("iFit console.  ")
                                    append(device.address)
                                    append("  signal ").append(device.rssi).append(" dBm")
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

    }
}

/**
 * The control screen: live metrics, speed and incline, and the emergency stop.
 */
@Composable
private fun ControlScreen(
    state: TreadmillState,
    limits: SafetyLimits,
    controller: TreadmillController,
    onOpenWorkouts: () -> Unit,
) {
    var speed by remember { mutableStateOf(state.targetSpeedKph ?: 0.0) }
    var incline by remember { mutableStateOf(state.targetInclinePercent ?: 0.0) }
    var unlockHex by remember { mutableStateOf("") }
    val units = LocalUnits.current

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(state, limits)

        // Start, stop and the emergency stop sit at the top, so they are on screen on every
        // device; at the bottom of a long screen they fell off a landscape tablet.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { controller.startWorkout(speed, incline) }) {
                Text("Start")
            }
            OutlinedButton(onClick = { controller.stopWorkout() }) {
                Text("Stop")
            }
            Button(
                onClick = { controller.stopWorkout() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text("EMERGENCY STOP", fontWeight = FontWeight.Bold)
            }
        }

        MetricsRow(state)

        // The unlock is worked out from the handshake now, so typing one is a rare fallback: kept
        // out of the way under Advanced.
        var showAdvanced by remember { mutableStateOf(false) }
        if (state.readiness == MachineReadiness.LOCKED || state.readiness == MachineReadiness.UNKNOWN) {
            androidx.compose.material3.TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide advanced" else "Advanced: enter an unlock code by hand")
            }
        }
        if (showAdvanced && (state.readiness == MachineReadiness.LOCKED || state.readiness == MachineReadiness.UNKNOWN)) {
            OutlinedTextField(
                value = unlockHex,
                onValueChange = { unlockHex = it },
                label = { Text("Unlock code (72 hex characters)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { controller.setUnlock(unlockHex) },
                enabled = unlockHex.isNotBlank(),
            ) {
                Text("Save unlock")
            }
        }

        Button(
            onClick = { controller.takeControl() },
            enabled = state.readiness != MachineReadiness.ARMED,
        ) {
            Text(
                when (state.readiness) {
                    MachineReadiness.ARMED -> "Ready to accept commands"
                    MachineReadiness.LOCKED -> "Unlock the treadmill"
                    else -> "Take control"
                },
            )
        }

        SliderRow(
            label = "Speed",
            value = speed,
            range = 0.0..limits.effectiveMaxSpeedKph,
            unit = units.speedUnit,
            shown = { units.speedFromKph(it) },
            // Whole tenths in the user's unit, so 1.0 mph is exactly that.
            onValueChange = { speed = units.speedToKph(Math.round(units.speedFromKph(it) * 10) / 10.0) },
            // Only a running belt is sent a new speed; before Start the slider just picks one.
            onCommit = {
                if (state.workoutMode == WorkoutMode.RUNNING) {
                    controller.setSpeed(units.speedToKph(Math.round(units.speedFromKph(it) * 10) / 10.0))
                }
            },
        )

        SliderRow(
            label = "Incline",
            value = incline,
            // The machine's own lowest incline (0 % on the T 6.5, which has no decline), in half-percent
            // steps: a slider value of -0.008 % was refused by the console.
            range = (limits.machineMinInclinePercent ?: 0.0)..limits.effectiveMaxInclinePercent,
            unit = "%",
            shown = { it },
            onValueChange = { incline = Math.round(it * 2) / 2.0 },
            // The same half-percent steps the slider shows (it sent a raw 1.49 for "1.5").
            onCommit = { if (state.workoutMode == WorkoutMode.RUNNING) controller.setIncline(Math.round(it * 2) / 2.0) },
        )

        CeilingRow(
            label = "Speed ceiling",
            value = units.speedFromKph(limits.maxSpeedKph),
            unit = units.speedUnit,
            onChange = { controller.setMaxSpeed(units.speedToKph(it)) },
        )

        CeilingRow(
            label = "Incline ceiling",
            value = limits.maxInclinePercent,
            unit = "%",
            onChange = { controller.setMaxIncline(it) },
        )

        OutlinedButton(onClick = { controller.disconnect() }, modifier = Modifier.fillMaxWidth()) {
            Text("Disconnect")
        }
    }
}

@Composable
private fun StatusCard(state: TreadmillState, limits: SafetyLimits) {
    val units = LocalUnits.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = when (state.readiness) {
                    MachineReadiness.ARMED -> "Ready to accept commands"
                    MachineReadiness.READ_ONLY -> "Watching only - the buttons still work"
                    MachineReadiness.LOCKED -> "Locked - the treadmill needs an unlock"
                    MachineReadiness.UNLOCKED -> "Unlocked, not yet ready"
                    MachineReadiness.BUSY_LOCAL -> "The treadmill is running its own workout"
                    MachineReadiness.ERRORED -> "The treadmill reported an error"
                    // Linked but the handshake is still running: "Not connected" here contradicted
                    // the "Connected" message above it (seen on the tablet).
                    MachineReadiness.UNKNOWN ->
                        if (state.connectionState == ConnectionState.CONNECTED) "Connected - checking the treadmill…" else "Not connected"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            state.deviceInfo?.serialNumber?.let {
                Text("Serial $it", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "Ceiling: ${units.speedText(limits.effectiveMaxSpeedKph)}, " +
                    "${limits.effectiveMaxInclinePercent} % incline",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MetricsRow(state: TreadmillState) {
    val units = LocalUnits.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("Speed", state.displayedSpeedKph?.let { units.speedText(it) } ?: "not reported")
                Metric("Incline", state.displayedInclinePercent?.let { "%.1f %%".format(it) } ?: "not reported")
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("Time", state.elapsedSeconds?.let { formatDuration(it) } ?: "not reported")
                Metric("Distance", state.distanceMeters?.let { units.distanceText(it.toDouble()) } ?: "not reported")
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("Calories", state.calories?.let { "%.0f".format(it) } ?: "not reported")
                Metric("Heart rate", state.heartRateBpm?.let { "$it bpm" } ?: "not reported")
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    unit: String,
    shown: (Double) -> Double,
    onValueChange: (Double) -> Unit,
    onCommit: (Double) -> Unit,
) {
    // The finish callback is created in an earlier composition, so it must read the latest value
    // from here, not from `value` (which sent 0 when the slider was let go).
    val latest = remember { DoubleArray(1) }
    latest[0] = value
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label: %.1f %s".format(shown(value), unit), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive).toFloat(),
            onValueChange = {
                latest[0] = it.toDouble()
                onValueChange(it.toDouble())
            },
            onValueChangeFinished = { onCommit(latest[0]) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        )
    }
}

@Composable
private fun CeilingRow(
    label: String,
    value: Double,
    unit: String,
    onChange: (Double) -> Unit,
) {
    var text by remember(unit) { mutableStateOf("%.1f".format(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let(onChange)
        },
        label = { Text("$label ($unit)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(minutes, rest)
}


/**
 * Manual control (moved off Home): connect or find a treadmill, then Start/Stop and sliders.
 * Spec 007 still applies: the belt only takes commands during a run started here.
 */
@Composable
private fun ManualControlScreen(
    state: TreadmillState,
    message: String?,
    devices: List<Scanner.Device>,
    scanning: Boolean,
    limits: SafetyLimits,
    controller: TreadmillController,
    onScan: () -> Unit,
    onOpenWorkouts: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        ScreenHeader("Manual control", "Drive the treadmill by hand", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        if (message != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (state.connectionState == ConnectionState.CONNECTED) {
            ControlScreen(
                state = state,
                limits = limits,
                controller = controller,
                onOpenWorkouts = onOpenWorkouts,
            )
        } else {
            controller.savedDevice()?.let { saved ->
                TreadmillHero(name = saved.name, limits = limits, onConnect = controller::connectToSaved)
                Spacer(Modifier.height(16.dp))
            }
            ConnectScreen(
                devices = devices,
                scanning = scanning,
                onScan = onScan,
                onConnect = controller::connect,
                onOpenWorkouts = onOpenWorkouts,
            )
        }
    }
}

/**
 * After a workout: its numbers and charts (the History entry), or a short note when it ran under a
 * minute and so was not kept.
 */
@Composable
private fun WorkoutSummaryScreen(
    entry: com.ofit.protocol.HistoryEntry?,
    onRate: (com.ofit.protocol.HistoryEntry, Int?, String?) -> Unit = { _, _, _ -> },
    name: String,
    onDone: () -> Unit,
    onHistory: () -> Unit,
    onRepeat: (() -> Unit)? = null,
    onDiscard: ((com.ofit.protocol.HistoryEntry) -> Unit)? = null,
) {
    // The History entry is written a moment after the workout ends; wait briefly for it.
    var waited by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2_500)
        waited = true
    }
    val units = com.ofit.app.settings.LocalUnits.current
    when {
        entry != null -> com.ofit.app.history.WorkoutDetail(
            entry, units, onClose = onDone, title = "Workout summary", onRepeat = onRepeat,
            onRate = { stars, feel -> onRate(entry, stars, feel) },
            onDiscard = onDiscard?.let { discard -> { discard(entry) } },
        )
        !waited -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        else -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Workout ended. It ran under a minute, so it was not saved to History.")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(onClick = onDone) { Text("Done") }
                androidx.compose.material3.OutlinedButton(onClick = onHistory) { Text("History") }
            }
        }
    }
}

/**
 * Devices: the treadmill on the left (its state, connect, "No treadmill", and the scan for another),
 * the heart-rate strap on the right (strap, zones, Hold zone).
 */
@Composable
private fun DevicesScreen(
    state: TreadmillState,
    devices: List<Scanner.Device>,
    scanning: Boolean,
    controller: TreadmillController,
    onScan: () -> Unit,
    onBack: () -> Unit,
) {
    val noTreadmill by controller.workouts.noTreadmill.collectAsStateWithLifecycle()
    val demo by controller.demo.collectAsStateWithLifecycle()
    val saved = controller.savedDevice()
    val connected = state.connectionState == ConnectionState.CONNECTED
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Devices", "Your treadmill and heart-rate strap", onBack = onBack)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("TREADMILL", style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color(0x99FFFFFF))
                // The treadmill as a hero card, in Home's style (Lane 1b "exciting screen" pass).
                val accent = MaterialTheme.colorScheme.primary
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.copy(alpha = 0.40f), androidx.compose.ui.graphics.Color(0xFF1C1A22))))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(80.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accent.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Icon(
                                    if (connected) androidx.compose.material.icons.Icons.Filled.BluetoothConnected else androidx.compose.material.icons.Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            Spacer(Modifier.width(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (demo) "Demo treadmill" else saved?.name ?: "No treadmill set up", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        demo -> "Simulated: the workout screen behaves as with a real treadmill, and nothing moves."
                                        saved == null -> "Look for it below; OpenFit remembers it."
                                        connected -> "Connected"
                                        state.connectionState == ConnectionState.CONNECTING -> "Connecting…"
                                        else -> "Not connected · OpenFit connects when you start a workout"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (saved != null && !connected && !noTreadmill && !demo) {
                                Button(
                                    onClick = controller::connectToSaved,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = androidx.compose.ui.graphics.Color.White,
                                        contentColor = androidx.compose.ui.graphics.Color.Black,
                                    ),
                                ) { Text("Connect now", fontWeight = FontWeight.Bold) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("No treadmill", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Run workouts on screen only, with suggested speeds; nothing is sent to a machine.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            androidx.compose.material3.Switch(checked = noTreadmill, enabled = !demo, onCheckedChange = { controller.setNoTreadmill(it) })
                        }
                        // The demo treadmill has no switch here (it is turned on from adb); while it is
                        // on, this lets it be turned off.
                        if (demo) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Demo treadmill", style = MaterialTheme.typography.titleMedium)
                                    Text("Simulated; nothing real moves. Turn off to use a real treadmill.", style = MaterialTheme.typography.bodySmall)
                                }
                                androidx.compose.material3.Switch(checked = true, onCheckedChange = { controller.setDemoTreadmill(false) })
                            }
                        }
                }
                Text("Another treadmill", style = MaterialTheme.typography.titleMedium)
                ConnectScreen(
                    devices = devices,
                    scanning = scanning,
                    onScan = onScan,
                    onConnect = controller::connect,
                    onOpenWorkouts = {},
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                com.ofit.app.heart.HeartRateScreen(manager = controller.heart, onBack = onBack, embedded = true)
            }
        }
    }
}

/**
 * Manual control's treadmill card, in Home's style: a big picture of the machine, its name, what it
 * can do, and a white Connect pill. Connecting only watches the treadmill; nothing moves until a run
 * is started.
 */
@Composable
private fun TreadmillHero(name: String, limits: SafetyLimits, onConnect: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val units = com.ofit.app.settings.LocalUnits.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.45f), androidx.compose.ui.graphics.Color(0xFF16161C), androidx.compose.ui.graphics.Color(0xFF0D0D0D)),
                ),
            )
            .padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(150.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(androidx.compose.ui.graphics.Color(0x1FFFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.DirectionsRun,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(84.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YOUR TREADMILL", color = accent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(name, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            Text(
                "Up to ${units.speedText(limits.effectiveMaxSpeedKph)} · incline " +
                    "${"%.0f".format(limits.machineMinInclinePercent ?: 0.0)} to ${"%.0f".format(limits.effectiveMaxInclinePercent)} %",
                color = androidx.compose.ui.graphics.Color(0xCCFFFFFF),
                fontSize = 17.sp,
            )
            Text(
                "Not connected. Connecting only watches the treadmill; nothing moves until you start. " +
                    "OpenFit lets go after 5 minutes with no workout.",
                color = androidx.compose.ui.graphics.Color(0x99FFFFFF),
                fontSize = 14.sp,
            )
        }
        Row(
            Modifier
                .height(64.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFF5F7F8))
                .clickable(onClick = onConnect)
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFF0D0D0D),
            )
            Spacer(Modifier.width(8.dp))
            Text("Connect now", color = androidx.compose.ui.graphics.Color(0xFF0D0D0D), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}


/** A training plan session as a one-off workout: not saved to My workouts; History records it by name. */
private fun planOneOff(program: com.ofit.protocol.WorkoutProgram, limits: com.ofit.protocol.SafetyLimits) =
    com.ofit.app.data.SavedWorkout(id = "plan-session", program = program.fitTo(limits), updatedAtMillis = System.currentTimeMillis())
