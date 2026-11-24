package com.ofit.app.ui

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
    val heartSummary by controller.heart.summary.collectAsStateWithLifecycle()
    val hudTiles by controller.settings.hudTiles.collectAsStateWithLifecycle()
    val hudSwapped by controller.settings.hudSwapped.collectAsStateWithLifecycle()
    val historyEntries by controller.history.entries.collectAsStateWithLifecycle()
    val coachStatus by controller.coach.status.collectAsStateWithLifecycle()
    val coachNotice by controller.coach.notice.collectAsStateWithLifecycle()
    val coachLevel by controller.heart.coachLevel.collectAsStateWithLifecycle()
    val profileList by controller.profiles.profiles.collectAsStateWithLifecycle()
    val currentProfile by controller.profiles.current.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    // A topic chosen on Browse, so the search screen opens with it filled in.
    var videoTopic by remember { mutableStateOf("") }
    // Where the video search was opened from, so Back returns there (Browse, Home or My workouts).
    var searchFrom by remember { mutableStateOf<AppScreen>(AppScreen.Workouts) }
    fun openSearch() {
        if (screen != AppScreen.VideoSearch) searchFrom = screen
        onNavigate(AppScreen.VideoSearch)
    }

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
            AppScreen.Profiles -> onNavigate(AppScreen.Home)
            AppScreen.Browse -> onNavigate(AppScreen.Home)
            AppScreen.VideoSearch -> {
                controller.library.clearAnalysis()
                onNavigate(searchFrom)
            }
            is AppScreen.Run -> if (!engine.isActive) onNavigate(AppScreen.Workouts)
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
    // The menu opens from every screen except the workout itself and "Who's running?".
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val drawerScope = androidx.compose.runtime.rememberCoroutineScope()
    val fullscreenSetting by controller.settings.fullscreen.collectAsStateWithLifecycle()
    val menuAllowed = screen !is AppScreen.Run && screen != AppScreen.Profiles
    val openMenu: (() -> Unit)? = if (menuAllowed) ({ drawerScope.launch { drawerState.open() } }) else null
    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = menuAllowed,
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

            AppScreen.QuickStart -> com.ofit.app.workout.QuickStartScreen(
                limits = limits,
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
                onAdd = { name, color, accent -> controller.profiles.add(name, color, accent) },
                onUpdate = controller.profiles::update,
                onRemove = controller.profiles::remove,
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
                entries = historyEntries,
                onDelete = controller.history::delete,
                onBack = { onNavigate(AppScreen.Home) },
            )

            AppScreen.HeartRate -> com.ofit.app.heart.HeartRateScreen(
                manager = controller.heart,
                onBack = { onNavigate(AppScreen.Home) },
            )

            AppScreen.Workouts -> WorkoutListScreen(
                workouts = workouts,
                onNew = { onNavigate(AppScreen.Editor(null)) },
                onEdit = { saved -> onNavigate(AppScreen.Editor(saved)) },
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
                onBack = { onNavigate(AppScreen.Workouts) },
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

            is AppScreen.Run -> {
                // Spec 007: the treadmill is connected while the workout screen is open, and let go
                // of when it closes and nothing is running.
                androidx.compose.runtime.DisposableEffect(Unit) {
                    controller.setWorkoutScreenOpen(true)
                    onDispose { controller.setWorkoutScreenOpen(false) }
                }
                WorkoutRunScreen(
                saved = screen.saved,
                snapshot = engine,
                state = state,
                limits = limits,
                preview = preview,
                onPreviewChange = controller.workouts::setPreview,
                onStart = {
                    if (preview) controller.workouts.start(screen.saved.program)
                    else controller.whenConnected { controller.workouts.start(screen.saved.program) }
                },
                onPause = { controller.workouts.pause() },
                onResume = { controller.workouts.resume() },
                onSkip = { controller.workouts.skip() },
                onAbandon = { controller.workouts.abandon() },
                onOverrideSpeed = controller.workouts::overrideSpeed,
                onOverrideIncline = controller.workouts::overrideIncline,
                onResetToProgram = controller.workouts::resetToProgram,
                onResetSpeed = controller.workouts::resetSpeed,
                onResetIncline = controller.workouts::resetIncline,
                onEmergencyStop = { controller.stopWorkout() },
                onBack = { onNavigate(AppScreen.Workouts) },
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
                    text = "Open treadmill control · NordicTrack T 6.5 Foldable",
                    style = MaterialTheme.typography.bodyMedium,
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
        HomeDashboard(
            state = state,
            treadmillName = controller.savedDevice()?.name,
            history = historyEntries,
            workouts = savedWorkouts,
            onConnect = controller::connectToSaved,
            onFindTreadmill = { onNavigate(AppScreen.ManualControl) },
            onOpenWorkout = { saved ->
                controller.workouts.clearFinished()
                onNavigate(AppScreen.Run(saved))
            },
            onBrowse = onBrowse,
            onMyWorkouts = onOpenWorkouts,
            onFindVideo = { onNavigate(AppScreen.VideoSearch) },
            onHistory = { onNavigate(AppScreen.History) },
            onManual = { onNavigate(AppScreen.ManualControl) },
            onQuickStart = { onNavigate(AppScreen.QuickStart) },
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

        Spacer(Modifier.height(12.dp))

        // The workouts do not need a treadmill to be connected. A user
        // may well want to import a workout before ever turning the machine on.
        OutlinedButton(onClick = onOpenWorkouts, modifier = Modifier.fillMaxWidth()) {
            Text("Workouts")
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

        Button(onClick = onOpenWorkouts, modifier = Modifier.fillMaxWidth()) {
            Text("Workouts")
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            MenuButton()
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.padding(start = 12.dp))
            Text("Manual control", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Treadmill: ${saved.name}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Not connected. OpenFit connects when you open a workout, and lets go when none is running.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = controller::connectToSaved) { Text("Connect now") }
                    }
                }
                Spacer(Modifier.height(12.dp))
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
