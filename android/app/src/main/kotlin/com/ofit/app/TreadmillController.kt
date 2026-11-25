package com.ofit.app

import android.content.Context
import android.os.SystemClock
import com.ofit.app.service.TreadmillService
import com.ofit.app.workout.WorkoutManager
import com.ofit.protocol.CapturedUnlock
import com.ofit.protocol.ConnectionState
import com.ofit.protocol.FeatureFlags
import com.ofit.protocol.Gatt
import com.ofit.protocol.LimitCheck
import com.ofit.protocol.MachineReadiness
import com.ofit.protocol.Refusal
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.Session
import com.ofit.protocol.SessionLog
import com.ofit.protocol.TreadmillState
import com.ofit.protocol.TreadmillStateHolder
import com.ofit.protocol.UnlockProvider
import com.ofit.transport.BleTransport
import com.ofit.transport.Scanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the session and everything the user interface talks to.
 *
 * One instance lives for the life of the process, so the connection survives the screens and the
 * background.
 */
class TreadmillController(context: Context) {

    private val appContext = context.applicationContext
    /**
     * A failed command must never take the app down: with the belt running, a crash leaves the
     * machine going with no controls on screen (seen live, 2026-11-23). It is reported instead.
     */
    private val failureHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
        android.util.Log.w("ofit-ble", "command failed", error)
        _message.value = "The treadmill did not confirm that command (${error.message}). " +
            "If the belt is moving, use Stop, or the treadmill's own STOP button."
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + failureHandler)

    /** Finds treadmills. */
    val scanner = Scanner(appContext)

    private val transport = BleTransport(appContext)

    /** The current machine state. */
    val holder = TreadmillStateHolder()

    /**
     * The demo treadmill: a simulated machine for trying, showing and filming OpenFit with the
     * full workout screen and nothing real moving. While it is on, nothing talks to Bluetooth.
     */
    val demoTreadmill = com.ofit.protocol.SimulatedTreadmill()
    private val _demo = MutableStateFlow(
        appContext.getSharedPreferences(DEVICE_PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY_DEMO, false),
    )
    val demo: StateFlow<Boolean> = _demo.asStateFlow()
    private var demoJob: kotlinx.coroutines.Job? = null

    /** The session log, for diagnosis. */
    val log = SessionLog()

    /** The user's safety ceilings. */
    private val _limits = MutableStateFlow(SafetyLimits.DEFAULT)

    /** The safety ceilings in force. */
    val limits: StateFlow<SafetyLimits> = _limits.asStateFlow()

    /** A user supplied unlock, if one has been entered. */
    private var unlock: UnlockProvider = CapturedUnlock(ByteArray(0))

    /** The session. */
    val session: Session = Session(
        transport = transport,
        holder = holder,
        unlockProvider = CapturedUnlock(ByteArray(0)),
        limitsProvider = { _limits.value },
        log = log,
    )

    /** The saved workout programs, and the one that is running. */
    val workouts = WorkoutManager(
        context = appContext,
        session = session,
        limitsProvider = { _limits.value },
        onMessage = { message -> _message.value = message },
    )

    /** The people who use this tablet; each has their own settings, strap and history. */
    val profiles = com.ofit.app.profile.ProfileManager(appContext)

    /** The heart-rate strap, if the user has one (spec 002, section 3b). */
    val heart = com.ofit.app.heart.HeartRateManager(
        context = appContext,
        scope = scope,
        consoleState = holder.state,
        workout = workouts.snapshot,
        prefsName = profiles.prefsName(com.ofit.app.heart.HeartRateManager.PREFS),
    )

    /** "Hold my zone": heart rate leads the speed, only when switched on (spec 003). */
    val coach = com.ofit.app.heart.CoachManager(
        scope = scope,
        heart = heart,
        workout = workouts.snapshot,
        program = workouts.runningProgram,
        limits = { _limits.value },
        setSpeed = { kph -> workouts.overrideSpeed(kph) },
        onMessage = { message -> _message.value = message },
    )

    /** Health Connect (P17): finished workouts written there when turned on. */
    val health = com.ofit.app.history.HealthConnectSync(appContext, scope)

    /** Every finished workout, for the history screen. */
    val history = com.ofit.app.history.HistoryManager(
        context = appContext,
        scope = scope,
        consoleState = holder.state,
        workout = workouts.snapshot,
        heartSummary = heart.summary,
        preview = workouts.preview,
        demo = demo,
        fileName = profiles.fileName("history", "json"),
        program = workouts.runningProgram,
        estimatedCalories = heart.calories,
        currentHeartRate = { heart.shown.value.bpm },
    )

    init {
        // New History entries go to Health Connect when that is turned on (P17).
        scope.launch { history.entries.collect { health.onHistory(it) } }
    }

    /** Small user settings: the unit system, and this install's anonymous id. */
    val settings: com.ofit.app.settings.AppSettings = com.ofit.app.settings.AppSettings(
        appContext,
        profiles.prefsName(com.ofit.app.settings.AppSettings.BASE_PREFS),
    ).also { created ->
        profiles.onSwitch = { profile ->
            created.switchProfile(profiles.prefsName(com.ofit.app.settings.AppSettings.BASE_PREFS, profile))
            heart.switchProfile(profiles.prefsName(com.ofit.app.heart.HeartRateManager.PREFS, profile))
            history.switchProfile(profiles.fileName("history", "json", profile))
        }
    }

    /** The user's own API keys, encrypted on this tablet. */
    val secrets = com.ofit.app.settings.SecretStore(appContext)

    /** Video search, video-to-workout, and the community library. */
    val library = com.ofit.app.library.LibraryManager(
        api = com.ofit.app.library.LibraryApi(
            installId = { settings.installId },
            geminiKey = { secrets.geminiKey() },
            useServer = { secrets.useServer.value },
            baseUrl = { secrets.serverUrl.value },
            youtubeKey = { secrets.youtubeKey() },
        ).also { com.ofit.app.library.LibraryApi.shared = it },
        workouts = workouts,
        onMessage = { message -> _message.value = message },
    )

    /** The scanned treadmills. */
    private val _devices = MutableStateFlow<List<Scanner.Device>>(emptyList())

    /** The treadmills seen during the last scan. */
    val devices: StateFlow<List<Scanner.Device>> = _devices.asStateFlow()

    /** The message shown at the top of the screen. */
    private val _message = MutableStateFlow<String?>(null)

    /** The current message, if any. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Whether a scan is running. */
    private val _scanning = MutableStateFlow(false)

    /** Whether a scan is running. */
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** The address of the treadmill we are connected to. */
    private var connectedAddress: String? = null

    init {
        session.start()
        scope.launch {
            transport.errors.collect { message ->
                _message.value = message
            }
        }
        scope.launch {
            // Poll while connected. The machine answers one request at a time, so the poll waits
            // for the previous one to finish rather than overlapping.
            while (true) {
                if (!_demo.value && holder.state.value.connectionState == ConnectionState.CONNECTED) {
                    runCatching { session.poll() }
                        .onFailure { android.util.Log.i("ofit-ble", "poll failed: $it") }
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
        scope.launch {
            // Keep the workout engine's clock moving. The engine decides when a step is over.
            var last = SystemClock.elapsedRealtime()
            while (true) {
                delay(WORKOUT_TICK_MILLIS)
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - last
                last = now
                // While the video is held (an ad), the program's clock stands still: no step changes.
                // Playback speed runs the program's clock faster or slower, with the video.
                workouts.updateEffectiveRate(holder.state.value.displayedSpeedKph)
                val scaled = (elapsed * workouts.effectiveRate.value).toLong()
                runCatching { workouts.engine.tick(if (workouts.videoHeld.value) 0 else scaled) }
            }
        }
        scope.launch {
            // A dropped link ends any running program, and the machine is never reported as
            // running while the app cannot see it. Specification requirement FR-013.
            //
            // A preview is the one exception, and it is not really an exception: a preview has no
            // machine and so no link to lose. The program is left running because there is
            // nothing on the other end that needs stopping.
            holder.state.collect { state ->
                if (state.connectionState == ConnectionState.DISCONNECTED && !workouts.preview.value) {
                    runCatching { workouts.onLinkLost() }
                }
            }
        }
        scope.launch {
            holder.state.collect { applyMachineRange(it) }
        }
        scope.launch {
            // Spec 007: let go of the treadmill whenever no workout is running.
            while (true) {
                delay(HANDS_OFF_CHECK_MILLIS)
                runCatching { handsOffCheck() }
            }
        }
        scope.launch {
            // A machine that is connected is a machine that must be driven, not previewed. The
            // preview is turned off so that the next program started is a real one.
            holder.state.collect { state ->
                // Never while a no-treadmill workout is running, and never against the runner's choice.
                if (state.connectionState == ConnectionState.CONNECTED && workouts.preview.value &&
                    !workouts.noTreadmill.value && !workouts.snapshot.value.isActive
                ) {
                    workouts.setPreview(false)
                }
            }
        }
    }

    private companion object {
        /** Messages from trying to reach the treadmill, cleared when they no longer apply. */
        private val CONNECT_NOISE = listOf("Could not connect", "Could not reach", "Still trying", "Reconnecting", "Connecting to")
        /** The machine's own poll cadence is about 200 ms. */
        const val POLL_INTERVAL_MILLIS = 250L

        /** How often the workout engine is asked how much time has passed. */
        const val WORKOUT_TICK_MILLIS = 200L

        /** How many times Stop is sent before the user is told to stop the machine by hand. */
        const val STOP_ATTEMPTS = 3

        /** Handshake tries at connect, and the pause between them. */
        const val DISCOVERY_ATTEMPTS = 4
        const val DISCOVERY_RETRY_MILLIS = 3_000L

        /** The remembered treadmill. */
        const val DEVICE_PREFS = "ofit_device"
        const val KEY_WORKOUT_RUNNING = "workout_running"
        const val KEY_DEMO = "demo_treadmill"
        const val DEMO_TICK_MILLIS = 250L
        const val SAVED_ADDRESS = "address"
        const val SAVED_NAME = "name"

        /** Reconnect tries at start-up, and how long each is given. */
        const val AUTO_CONNECT_ATTEMPTS = 8
        const val AUTO_CONNECT_WAIT_MILLIS = 15_000L

        /** Spec 007: idle time before OpenFit lets go of the treadmill. */
        // 5 min (user request, 2026-11-24): connecting from Devices outside a workout is useful, so
        // the link is kept as long as on the workout screen.
        const val IDLE_DISCONNECT_MILLIS = 5 * 60_000L
        const val IDLE_KEEPALIVE_MILLIS = 5 * 60_000L
        const val HANDS_OFF_CHECK_MILLIS = 2_000L

        /** Where the saved unlock code lives. */
        const val UNLOCK_PREFS = "ofit_unlock"
        const val UNLOCK_KEY = "unlock_hex"
    }

    init {
        log.onEvent = { android.util.Log.i("ofit-ble", "session: $it") }
        // Restore a previously saved unlock code.
        appContext.getSharedPreferences(UNLOCK_PREFS, android.content.Context.MODE_PRIVATE)
            .getString(UNLOCK_KEY, null)
            ?.let { saved -> runCatching { CapturedUnlock.fromHex(saved) }.getOrNull() }
            ?.let { restored ->
                unlock = restored
                session.unlockProvider = restored
                FeatureFlags.unlockSupplied = true
            }
    }

    /** Whether the user has set a ceiling themselves; until then it follows the machine's maximum. */
    @Volatile private var userSetSpeedCeiling = false
    @Volatile private var userSetInclineCeiling = false

    /** Set the user's speed ceiling. */
    fun setMaxSpeed(kph: Double) {
        userSetSpeedCeiling = true
        _limits.value = _limits.value.copy(maxSpeedKph = kph)
    }

    /** Set the user's incline ceiling. */
    fun setMaxIncline(percent: Double) {
        userSetInclineCeiling = true
        _limits.value = _limits.value.copy(maxInclinePercent = percent)
    }

    /**
     * The machine's reported range becomes its limits (checked in the protocol layer), and the
     * user's ceiling follows the machine's maximum until the user sets one. A value outside a
     * plausible treadmill range is ignored rather than trusted.
     */
    private fun applyMachineRange(state: TreadmillState) {
        val maxKph = state.maxSpeedKph?.takeIf { it in 5.0..30.0 }
        val minKph = state.minSpeedKph?.takeIf { it in 0.1..5.0 }
        val maxGrade = state.maxInclinePercent?.takeIf { it in 1.0..40.0 }
        val minGrade = state.minInclinePercent?.takeIf { it in -10.0..5.0 }
        val old = _limits.value
        var next = old.copy(
            machineMaxSpeedKph = maxKph ?: old.machineMaxSpeedKph,
            machineMinSpeedKph = minKph ?: old.machineMinSpeedKph,
            machineMaxInclinePercent = maxGrade ?: old.machineMaxInclinePercent,
            machineMinInclinePercent = minGrade ?: old.machineMinInclinePercent,
        )
        if (!userSetSpeedCeiling && maxKph != null) next = next.copy(maxSpeedKph = maxKph)
        if (!userSetInclineCeiling && maxGrade != null) next = next.copy(maxInclinePercent = maxGrade)
        if (next != old) {
            android.util.Log.i("ofit-ble", "limits from the machine: $next")
            _limits.value = next
        }
        // Remember the range, so the editor and No treadmill mode know this treadmill's real top
        // speed even when it is off (user report: the editor stopped at 7.5 mph on a 10 mph machine).
        if (maxKph != null || maxGrade != null) {
            machinePrefs.edit().apply {
                maxKph?.let { putFloat("maxKph", it.toFloat()) }
                minKph?.let { putFloat("minKph", it.toFloat()) }
                maxGrade?.let { putFloat("maxGrade", it.toFloat()) }
                minGrade?.let { putFloat("minGrade", it.toFloat()) }
            }.apply()
        }
    }

    private val machinePrefs by lazy { appContext.getSharedPreferences("ofit_machine", android.content.Context.MODE_PRIVATE) }

    /** The last treadmill's range, from a previous connection, until this one reports its own. */
    private fun restoreMachineRange() {
        fun f(key: String) = machinePrefs.getFloat(key, Float.NaN).takeIf { !it.isNaN() }?.toDouble()
        // Nothing stored yet but a treadmill was set up before (this app's first machine is a T 6.5:
        // 10 mph, 0-10 %). Only a stand-in: its own report replaces it on connection, and a workout
        // outside the real range is still refused at Start.
        val maxKph = f("maxKph") ?: if (savedDevice() != null) {
            _limits.value = _limits.value.copy(
                machineMaxSpeedKph = 16.09,
                machineMinSpeedKph = 0.8,
                machineMaxInclinePercent = 10.0,
                machineMinInclinePercent = 0.0,
                maxSpeedKph = 16.09,
            )
            return
        } else {
            return
        }
        val maxGrade = f("maxGrade")
        _limits.value = _limits.value.copy(
            machineMaxSpeedKph = maxKph,
            machineMinSpeedKph = f("minKph"),
            machineMaxInclinePercent = maxGrade,
            machineMinInclinePercent = f("minGrade"),
            maxSpeedKph = maxKph,
            maxInclinePercent = maxGrade ?: _limits.value.maxInclinePercent,
        )
    }


    /** Show a message at the top of the screen. */
    fun showMessage(message: String?) {
        _message.value = message
    }

    /** Supply an unlock payload, as hex. */
    fun setUnlock(hex: String): Boolean {
        val provider = runCatching { CapturedUnlock.fromHex(hex.trim()) }.getOrNull() ?: return false
        unlock = provider
        session.unlockProvider = provider
        // Kept, so it need not be typed again: it is this machine's code, not a secret of the user's.
        appContext.getSharedPreferences(UNLOCK_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(UNLOCK_KEY, hex.trim()).apply()
        FeatureFlags.unlockSupplied = true
        _message.value = "Unlock saved."
        return true
    }

    /** Start scanning for treadmills. */
    fun startScan() {
        if (!scanner.hasPermission()) {
            _message.value = "The app needs permission to scan for Bluetooth devices."
            return
        }
        if (!scanner.isBluetoothAvailable()) {
            _message.value = "Bluetooth is switched off."
            return
        }
        _scanning.value = true
        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                scanner.scan().collect { device ->
                    val current = _devices.value
                    if (current.none { it.address == device.address }) {
                        // The console is worth seeing first.
                        // A console first, then the strongest signal: the machine you are
                        // standing next to is the one with the strongest signal.
                        _devices.value = (current + device)
                            .sortedWith(compareByDescending<Scanner.Device> { it.isConsole }.thenByDescending { it.rssi })
                    }
                }
            } catch (error: SecurityException) {
                _message.value = "The app needs permission to scan for Bluetooth devices."
            } finally {
                _scanning.value = false
            }
        }
    }

    private var scanJob: kotlinx.coroutines.Job? = null

    /**
     * Stop scanning. Cancelling the collector stops the LE scan and the classic discovery; only
     * clearing the flag (as before) left discovery running, and it blocked LE connects to the
     * console (status 147, seen live 2026-11-23).
     */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    /**
     * Connect to a treadmill and discover what it is.
     *
     * The machine is left in read-only mode after this: the user can watch it without taking
     * control away from the physical buttons.
     */
    fun connect(device: Scanner.Device) {
        if (_demo.value) setDemoTreadmill(false)
        // Any scan running now would slow or block the connect.
        stopScan()
        heart.stopScan()
        connectedAddress = device.address
        scope.launch {
            // No pairing: an HCI capture of the vendor application on this console (2026-11-23)
            // shows it never pairs. It connects, subscribes, and talks. Asking Android to pair
            // made the console hang up.
            if (!workouts.preview.value) _message.value = "Connecting to ${device.name}."
            runCatching { transport.connect(device.address) }
                .onFailure { error ->
                    // In No treadmill mode a failed background try is not news (the treadmill is
                    // likely off); say nothing rather than leave an error on Home.
                    _message.value = if (workouts.preview.value) null else "Could not connect: ${error.message}"
                    holder.update { it.copy(connectionState = ConnectionState.FAILED) }
                }
                .onSuccess {
                    _message.value = "Connected. Discovering the treadmill."
                    // The console sometimes refuses the first handshake right after a previous
                    // session ended (it answers `01 00 00 00`, seen live 2026-11-23), so it is
                    // retried a few times before giving up.
                    var attempt = 0
                    var result: Result<com.ofit.protocol.DeviceInfo>
                    while (true) {
                        result = runCatching { session.discover() }
                        attempt++
                        if (result.isSuccess || attempt >= DISCOVERY_ATTEMPTS ||
                            holder.state.value.connectionState != ConnectionState.CONNECTED
                        ) {
                            break
                        }
                        android.util.Log.i("ofit-ble", "handshake attempt $attempt failed, retrying: ${result.exceptionOrNull()?.message}")
                        delay(DISCOVERY_RETRY_MILLIS)
                    }
                    result
                        .onSuccess { info ->
                            _message.value = if (info.serialNumber != null) {
                                "Treadmill found. Serial ${info.serialNumber}."
                            } else {
                                "Treadmill found."
                            }
                            // Remembered, so the next start connects by itself.
                            devicePrefs.edit()
                                .putString(SAVED_ADDRESS, device.address)
                                .putString(SAVED_NAME, device.name)
                                .apply()
                            // The connection is real now, so the foreground service can start: the
                            // Bluetooth permission it needs has been granted by this point.
                            TreadmillService.start(appContext)
                        }
                        .onFailure { _message.value = "Connected, but discovery failed: ${it.message}" }
                }
        }
    }

    private val devicePrefs = appContext.getSharedPreferences(DEVICE_PREFS, android.content.Context.MODE_PRIVATE)

    init {
        // After devicePrefs exists: the stand-in range needs to know whether a treadmill was set up.
        runCatching { restoreMachineRange() }.onFailure { android.util.Log.w("ofit-ble", "machine range", it) }
    }

    // ------------------------------------------------------------------ closed mid-workout

    private val _leftRunning = MutableStateFlow(false)

    /**
     * OpenFit was closed (killed, updated, crashed) during a workout on the treadmill. The console
     * has no time-out for a vanished app, so the belt keeps going at its last speed (user report,
     * 2026-11-24). On the next start OpenFit reconnects and, if the belt is moving, asks whether to
     * stop it.
     */
    val leftRunning: StateFlow<Boolean> = _leftRunning.asStateFlow()

    init {
        if (devicePrefs.getBoolean(KEY_WORKOUT_RUNNING, false)) {
            android.util.Log.i("ofit-ble", "closed during a workout last time: checking the belt")
            _leftRunning.value = true
            scope.launch {
                delay(1_500)
                connectToSaved()
                // Nothing to ask if the treadmill cannot be reached in a minute, or the belt is still.
                delay(60_000)
                if (_leftRunning.value && holder.state.value.connectionState != ConnectionState.CONNECTED) clearLeftRunning()
            }
        }
        // Note while a workout runs on the treadmill, so a restart can tell it was cut short.
        scope.launch {
            workouts.snapshot.collect { snapshot ->
                val onTreadmill = snapshot.isActive && !workouts.preview.value && !_demo.value
                if (devicePrefs.getBoolean(KEY_WORKOUT_RUNNING, false) != onTreadmill) {
                    devicePrefs.edit().putBoolean(KEY_WORKOUT_RUNNING, onTreadmill).commit()
                }
            }
        }
        // Once connected: a belt that is not moving needs no question.
        scope.launch {
            holder.state.collect { state ->
                if (_leftRunning.value && state.connectionState == ConnectionState.CONNECTED &&
                    state.deviceInfo != null && !beltMoving(state)
                ) {
                    clearLeftRunning()
                }
            }
        }
    }

    /** Whether the belt is moving (or the console says a workout is running). */
    fun beltMoving(state: TreadmillState = holder.state.value): Boolean =
        (state.displayedSpeedKph ?: 0.0) > 0.3 ||
            state.workoutMode == com.ofit.protocol.WorkoutMode.RUNNING

    /** The runner chose to stop the belt left running. */
    fun stopLeftRunning() {
        scope.launch {
            var stopped = false
            repeat(STOP_ATTEMPTS) {
                if (!stopped) stopped = runCatching { session.stop() }.getOrDefault(false)
            }
            clearLeftRunning()
            _message.value = if (stopped) {
                "Stopped the treadmill."
            } else {
                "The treadmill did not confirm the stop. Use the treadmill's own STOP button or pull the safety key."
            }
            delay(3_000)
            runCatching { session.releaseControl() }
        }
    }

    /** The runner chose to leave the belt as it is (they are using it by hand). */
    fun clearLeftRunning() {
        _leftRunning.value = false
        devicePrefs.edit().putBoolean(KEY_WORKOUT_RUNNING, false).apply()
    }

    /** The treadmill last connected to, if any. */
    fun savedDevice(): Scanner.Device? {
        val address = devicePrefs.getString(SAVED_ADDRESS, null) ?: return null
        val name = devicePrefs.getString(SAVED_NAME, null) ?: Gatt.ADVERTISED_NAME
        return Scanner.Device(name = name, address = address, rssi = 0, isConsole = true)
    }

    private var autoConnecting = false

    /**
     * Connect to the remembered treadmill without a scan. The console may still be off when the
     * app opens, so it is tried a few times, a little apart, until it answers or the user scans.
     */
    /** At app start: the strap only. The treadmill is connected on demand (spec 007). */
    fun connectStrap() {
        if (scanner.hasPermission()) runCatching { heart.connectToSaved() }
    }

    fun connectToSaved() {
        if (_demo.value) return
        // The strap reconnects on its own link, whatever happens with the treadmill.
        connectStrap()
        val device = savedDevice() ?: return
        if (autoConnecting || !scanner.hasPermission() || !scanner.isBluetoothAvailable()) return
        autoConnecting = true
        scope.launch {
            try {
                repeat(AUTO_CONNECT_ATTEMPTS) { attempt ->
                    val state = holder.state.value.connectionState
                    if (state == ConnectionState.CONNECTED || _scanning.value) return@launch
                    // A workout already running with no treadmill: stop trying, quietly.
                    if (workouts.preview.value && workouts.snapshot.value.isActive) {
                        _message.value = null
                        return@launch
                    }
                    _message.value = if (workouts.preview.value) null else if (attempt == 0) {
                        "Reconnecting to ${device.name}."
                    } else {
                        "Still trying to reach ${device.name}. Is the treadmill switched on?"
                    }
                    connect(device)
                    // Give the link and the handshake time to finish before judging it.
                    delay(AUTO_CONNECT_WAIT_MILLIS)
                    if (holder.state.value.connectionState == ConnectionState.CONNECTED) return@launch
                }
                if (!workouts.preview.value) {
                    _message.value = "Could not reach ${device.name}. Switch the treadmill on, then look for it."
                }
            } finally {
                autoConnecting = false
            }
        }
    }

    // ------------------------------------------------------------------ hands off (spec 007)

    /** True while the workout screen is open: the link is kept longer so Start is instant. */
    @Volatile private var keepAlive = false
    private var idleSinceMillis: Long? = null

    /** The workout screen opened or closed. Opening it connects to the remembered treadmill. */
    fun setWorkoutScreenOpen(open: Boolean) {
        keepAlive = open
        // Leaving the workout screen: a connect message from trying while it was open is stale.
        if (!open && holder.state.value.connectionState != ConnectionState.CONNECTED &&
            _message.value?.let { m -> CONNECT_NOISE.any { m.startsWith(it) } } == true
        ) {
            _message.value = null
        }
        if (open && !workouts.noTreadmill.value && !_demo.value && holder.state.value.connectionState != ConnectionState.CONNECTED) {
            connectToSaved()
        }
    }

    /**
     * The demo treadmill on or off. On: a real treadmill is let go of, "No treadmill" is turned
     * off, and the simulated machine is what the workout screen shows and the engine drives. Off:
     * the simulated machine is gone and the app is disconnected, as after a real disconnect.
     */
    fun setDemoTreadmill(on: Boolean) {
        if (_demo.value == on && (demoJob != null) == on) return
        devicePrefs.edit().putBoolean(KEY_DEMO, on).apply()
        if (on) {
            if (workouts.snapshot.value.isActive) workouts.abandon()
            if (holder.state.value.connectionState == ConnectionState.CONNECTED) {
                scope.launch {
                    runCatching { session.releaseControl() }
                    runCatching { session.disconnect() }
                    TreadmillService.stop(appContext)
                }
            }
            _demo.value = true
            workouts.setPreview(false, byUser = true)
            workouts.demoSink = demoTreadmill
            demoTreadmill.reset()
            demoJob = scope.launch {
                while (true) {
                    delay(DEMO_TICK_MILLIS)
                    demoTreadmill.tick(DEMO_TICK_MILLIS)
                    holder.update { demoTreadmill.state.value }
                }
            }
            _message.value = "Demo treadmill: the workout screen works as with a real treadmill, but nothing moves."
        } else {
            demoJob?.cancel()
            demoJob = null
            if (workouts.snapshot.value.isActive) workouts.abandon()
            workouts.demoSink = null
            _demo.value = false
            holder.update { TreadmillState() }
            _message.value = null
        }
    }

    init {
        // The demo survives a restart, like the choice of treadmill.
        if (_demo.value) setDemoTreadmill(true)
        // A workout deleted from History is taken out of Health Connect too.
        history.onDeleted = { entry -> health.remove(entry.startedAtMillis) }
    }

    /**
     * Work out without a treadmill (on screen only), or go back to driving one. Choosing it lets go
     * of a connected treadmill, so nothing can be sent to it.
     */
    fun setNoTreadmill(on: Boolean) {
        if (on && _demo.value) setDemoTreadmill(false)
        workouts.setPreview(on, byUser = true)
        if (on && holder.state.value.connectionState == ConnectionState.CONNECTED && !workouts.snapshot.value.isActive) {
            scope.launch {
                runCatching { session.releaseControl() }
                runCatching { session.disconnect() }
                TreadmillService.stop(appContext)
            }
        }
        if (!on) connectToSaved()
    }

    /**
     * Run [action] once the treadmill is connected and its handshake is done, connecting to the
     * remembered one first if needed. Gives up after 30 s with a message.
     */
    fun whenConnected(action: () -> Unit) {
        val ready = { holder.state.value.connectionState == ConnectionState.CONNECTED && holder.state.value.deviceInfo != null }
        if (ready()) {
            action()
            return
        }
        connectToSaved()
        scope.launch {
            val ok = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                while (!ready()) delay(250)
                true
            } ?: false
            if (ok) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { action() }
            } else {
                _message.value = "Could not reach the treadmill. Is it switched on?"
            }
        }
    }

    /**
     * Spec 007: OpenFit never stays connected to a treadmill it is not running a workout on. An app
     * that stayed connected and sent commands while someone used the machine by hand
     * would be a safety problem; OpenFit drops the link instead.
     *  - Someone starts the belt by hand (the app did not start it): disconnect at once.
     *  - No workout for 5 min (connected from Devices, or on the workout screen): give control back, disconnect.
     */
    private suspend fun handsOffCheck() {
        if (_demo.value) return
        val state = holder.state.value
        if (state.connectionState != ConnectionState.CONNECTED) {
            idleSinceMillis = null
            return
        }
        // While asking about a belt left running from before, stay connected to be able to stop it.
        if (_leftRunning.value) {
            idleSinceMillis = null
            return
        }
        if (session.gate.manualUse && !workouts.snapshot.value.isActive) {
            android.util.Log.i("ofit-ble", "hands off: the treadmill is being used by hand; disconnecting")
            runCatching { session.disconnect() }
            TreadmillService.stop(appContext)
            _message.value = "Someone is using the treadmill by hand, so OpenFit disconnected and sends nothing. " +
                "It reconnects when you start a workout in the app."
            idleSinceMillis = null
            return
        }
        val busy = workouts.snapshot.value.isActive || session.gate.ownsRun ||
            state.workoutMode == com.ofit.protocol.WorkoutMode.RUNNING ||
            state.workoutMode == com.ofit.protocol.WorkoutMode.PAUSE
        if (busy) {
            idleSinceMillis = null
            return
        }
        val now = SystemClock.elapsedRealtime()
        val since = idleSinceMillis ?: now.also { idleSinceMillis = it }
        val limit = if (keepAlive) IDLE_KEEPALIVE_MILLIS else IDLE_DISCONNECT_MILLIS
        if (now - since >= limit) {
            android.util.Log.i("ofit-ble", "hands off: no workout for ${limit / 1000} s; releasing and disconnecting")
            runCatching { session.releaseControl() }
            runCatching { session.disconnect() }
            TreadmillService.stop(appContext)
            _message.value = "No workout for 5 minutes, so OpenFit let go of the treadmill. It reconnects when you start one."
            idleSinceMillis = null
        }
    }

    /** Disconnect from the treadmill. */
    fun disconnect() {
        scope.launch {
            runCatching { session.disconnect() }
            connectedAddress = null
            holder.update { it.copy(readiness = MachineReadiness.UNKNOWN) }
            // Nothing to keep alive once the link is gone.
            TreadmillService.stop(appContext)
        }
    }

    /**
     * Unlock and arm the treadmill so that it accepts control.
     */
    fun takeControl() {
        scope.launch {
            _message.value = "Unlocking the treadmill."
            val armed = runCatching { session.enterControlMode() }
                .getOrElse { error ->
                    android.util.Log.i("ofit-ble", "take control failed", error)
                    _message.value = "Could not unlock the treadmill: ${error.message}"
                    false
                }
            android.util.Log.i("ofit-ble", "take control: armed=$armed state=${holder.state.value.readiness} error=${holder.state.value.lastError}")
            if (!armed) {
                _message.value = holder.state.value.lastError ?: "The treadmill is not ready yet."
            }
        }
    }

    /** Set the target speed. */
    fun setSpeed(kph: Double) {
        scope.launch {
            when (val result = session.setSpeed(kph)) {
                is LimitCheck.Accepted -> _message.value = null
                is LimitCheck.Refused -> _message.value = result.reason.explanation
            }
        }
    }

    /** Set the target incline. */
    fun setIncline(percent: Double) {
        scope.launch {
            val result = session.setIncline(percent)
            android.util.Log.i("ofit-ble", "setIncline($percent): $result")
            when (result) {
                is LimitCheck.Accepted -> _message.value = null
                is LimitCheck.Refused -> _message.value = result.reason.explanation
            }
        }
    }

    /** Start a workout. */
    fun startWorkout(speedKph: Double, inclinePercent: Double) {
        scope.launch {
            val result = session.start(speedKph, inclinePercent)
            android.util.Log.i("ofit-ble", "start($speedKph, $inclinePercent): $result limits=${_limits.value}")
            when (result) {
                is LimitCheck.Accepted -> _message.value = null
                is LimitCheck.Refused -> _message.value = result.reason.explanation
            }
        }
    }

    /** Stop the workout. */
    fun stopWorkout() {
        if (_demo.value) {
            scope.launch { demoTreadmill.stopWorkout(); _message.value = "Stopped." }
            return
        }
        scope.launch {
            // Stop is the one command that must get through: try it up to three times.
            var stopped = false
            repeat(STOP_ATTEMPTS) {
                if (!stopped) {
                    stopped = runCatching { session.stop() }
                        .onFailure { android.util.Log.w("ofit-ble", "stop attempt failed", it) }
                        .getOrDefault(false)
                }
            }
            _message.value = if (stopped) {
                "Stopped."
            } else {
                "The treadmill did not confirm the stop. Use the treadmill's own STOP button or pull the safety key."
            }
        }
    }

    /** Poll the machine. */
    fun poll() {
        scope.launch {
            runCatching { session.poll() }
        }
    }

    /** The current machine state. */
    val state: StateFlow<TreadmillState> get() = holder.state
}
