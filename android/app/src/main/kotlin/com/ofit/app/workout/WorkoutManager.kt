package com.ofit.app.workout

import android.content.Context
import com.ofit.app.data.SavedWorkout
import com.ofit.app.data.WorkoutRepository
import com.ofit.protocol.LimitCheck
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.Session
import com.ofit.protocol.SessionWorkoutSink
import com.ofit.protocol.StartOutcome
import com.ofit.protocol.WorkoutCommandSink
import com.ofit.protocol.WorkoutEngine
import com.ofit.protocol.WorkoutEngineSnapshot
import com.ofit.protocol.WorkoutProgram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the saved workout programs and the running one.
 *
 * The programs are stored on the device and run entirely offline: the engine drives the machine
 * over Bluetooth and never needs the internet. A local video file, if the user picks one, is
 * played alongside; nothing protected is copied or decrypted.
 */
class WorkoutManager(
    context: Context,
    session: Session,
    limitsProvider: () -> SafetyLimits,
    private val onMessage: (String?) -> Unit,
) {

    private val appContext = context.applicationContext
    private val repository = WorkoutRepository(context)

    private val videoPrefs
        get() = appContext.getSharedPreferences("ofit_videos", Context.MODE_PRIVATE)

    /** The YouTube video remembered for a saved workout, if there is one. */
    fun youtubeIdFor(workoutId: String): String? = videoPrefs.getString("yt:$workoutId", null)

    /** The video file (a content URI) chosen for a workout, if it has one instead of a YouTube video. */
    fun videoFileFor(workoutId: String): String? = videoPrefs.getString(workoutId, null)

    /**
     * Save a program that came with a YouTube video, and remember the video for it, so the
     * workout opens with its scenery. Only the video's id is kept: the video itself is never
     * downloaded and plays in YouTube's own player.
     */
    fun saveWithVideo(program: WorkoutProgram, videoId: String?, onSaved: (SavedWorkout) -> Unit = {}) {
        scope.launch {
            val saved = repository.save(program)
            if (videoId != null) {
                videoPrefs.edit().putString("yt:${saved.id}", videoId).remove(saved.id).apply()
            }
            refresh()
            onMessage("Saved ${program.name}.")
            onSaved(saved)
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The sink that drives the real machine. */
    private val machineSink = SessionWorkoutSink(session)

    /** The sink that drives nothing, for a preview. */
    private val previewSink = PreviewWorkoutSink()

    /**
     * Which sink the running program is using.
     *
     * It is decided when the program starts and kept for as long as it runs, so that a program
     * started against the machine can never have its next step sent to the preview instead, or
     * the other way round.
     */
    @Volatile
    private var activeSink: WorkoutCommandSink = machineSink

    /** Whether the next program to start runs against the preview. */
    @Volatile
    private var previewMode: Boolean = false

    /**
     * The demo treadmill's sink while the demo is on (TreadmillController.setDemoTreadmill): a
     * program then runs against the simulated machine instead of the real one.
     */
    @Volatile
    var demoSink: com.ofit.protocol.WorkoutCommandSink? = null

    private val _preview = MutableStateFlow(false)

    /**
     * Whether a program started now would be a preview.
     *
     * A preview runs the program without a treadmill, so the display and the video can be seen
     * before stepping onto the machine.
     */
    val preview: StateFlow<Boolean> = _preview.asStateFlow()

    /**
     * The engine talks to one sink, but which one depends on the mode.
     *
     * The switch lives here, in one place, so that no other part of the app can send a command
     * to a machine that is not there, and no command meant for the machine can be swallowed.
     */
    private val switchingSink = object : WorkoutCommandSink {

        override suspend fun startWorkout(speedKph: Double, inclinePercent: Double): Boolean {
            val sink = if (previewMode) previewSink else (demoSink ?: machineSink)
            activeSink = sink
            return sink.startWorkout(speedKph, inclinePercent)
        }

        override suspend fun applyStep(speedKph: Double, inclinePercent: Double): Boolean =
            activeSink.applyStep(speedKph, inclinePercent)

        override suspend fun stopWorkout(): Boolean = activeSink.stopWorkout()
    }

    /** The engine that runs a program. */
    // With no treadmill there is no machine to protect, so the treadmill's own range does not apply
    // (a workout with downhill steps runs on screen as it is). With a treadmill, every limit applies.
    val engine = WorkoutEngine(
        { if (previewMode) NO_TREADMILL_LIMITS else limitsProvider() },
        switchingSink,
        // A workout ends with a 10-minute cool-down, the speed easing down. With no treadmill the
        // same cool-down is suggested on screen (user request 2026-11-24: the same path as with one).
        coolDownFor = { snapshot ->
            // A program that cools down inside its video has no second cool-down once it is in that part.
            val from = runningProgram.value?.coolDownFromSeconds
            if (from != null && snapshot.totalElapsedMillis / 1000 >= from - 1) null
            else com.ofit.protocol.CoolDown.build(snapshot.speedKph, if (previewMode) NO_TREADMILL_LIMITS else limitsProvider())
        },
    )

    private val _startProblem = MutableStateFlow<String?>(null)

    /** Why the last Start was refused, shown on the workout screen (it used to show only on Home). */
    val startProblem: StateFlow<String?> = _startProblem.asStateFlow()

    /** The engine's state, for the user interface. */
    val snapshot: StateFlow<WorkoutEngineSnapshot> = engine.snapshot

    private val _workouts = MutableStateFlow<List<SavedWorkout>>(emptyList())

    /** The saved programs. */
    val workouts: StateFlow<List<SavedWorkout>> = _workouts.asStateFlow()

    private val _runningProgram = MutableStateFlow<WorkoutProgram?>(null)

    /** The program that is running, if one is. */
    val runningProgram: StateFlow<WorkoutProgram?> = _runningProgram.asStateFlow()

    init {
        refresh()
    }

    /** Re-read the saved programs from disk. */
    fun refresh() {
        scope.launch {
            _workouts.value = repository.list()
        }
    }

    /** Save a program and refresh the list. */
    fun save(program: WorkoutProgram) {
        scope.launch {
            repository.save(program)
            refresh()
            onMessage("Saved ${program.name}.")
        }
    }

    /**
     * Save from the visual editor: overwrite [existingId] when editing, or save a new workout, and
     * remember its YouTube video.
     */
    fun saveEdited(
        existingId: String?,
        program: WorkoutProgram,
        videoId: String?,
        videoFile: String? = null,
        onSaved: (SavedWorkout) -> Unit = {},
    ) {
        scope.launch {
            val saved = if (existingId != null) repository.replace(existingId, program) else repository.save(program)
            // One video per workout: a file replaces a YouTube video and the other way round.
            when {
                videoFile != null -> videoPrefs.edit().putString(saved.id, videoFile).remove("yt:${saved.id}").apply()
                videoId != null -> videoPrefs.edit().putString("yt:${saved.id}", videoId).remove(saved.id).apply()
            }
            refresh()
            onMessage("Saved ${program.name}.")
            onSaved(saved)
        }
    }

    /** Save a copy of a workout, with the same video, named "… (copy)". */
    fun duplicate(original: SavedWorkout) {
        scope.launch {
            val copy = repository.save(original.program.copy(name = "${original.program.name} (copy)".take(80)))
            val edit = videoPrefs.edit()
            videoPrefs.getString("yt:${original.id}", null)?.let { edit.putString("yt:${copy.id}", it) }
            videoPrefs.getString(original.id, null)?.let { edit.putString(copy.id, it) }
            edit.apply()
            refresh()
            onMessage("Copied ${original.program.name}.")
        }
    }

    /** Remove a program and refresh the list. */
    fun delete(id: String) {
        scope.launch {
            repository.delete(id)
            refresh()
            onMessage("Workout removed.")
        }
    }

    /**
     * Start a program.
     *
     * Steps outside the treadmill's range are fitted into it by the engine; only an empty program
     * or a step with no duration is rejected.
     */
    fun start(program: WorkoutProgram) {
        scope.launch {
            android.util.Log.i("ofit-ble", "start: preview=$previewMode steps=${program.steps.size}")
            val outcome = try {
                engine.start(program)
            } catch (e: Exception) {
                android.util.Log.e("ofit-ble", "start failed", e)
                StartOutcome.Failed("Couldn't start: ${e.message ?: e.javaClass.simpleName}")
            }
            android.util.Log.i("ofit-ble", "start outcome: ${outcome.javaClass.simpleName}")
            when (outcome) {
                is StartOutcome.Started -> {
                    // The program as fitted to this treadmill, so the screen shows what is sent.
                    _runningProgram.value = outcome.program
                    _startProblem.value = null
                    onMessage(null)
                }

                is StartOutcome.Rejected -> {
                    val reason = "Can't start on this treadmill: " +
                        outcome.problems.map { problem -> problem.message }.distinct().take(3).joinToString(" ") +
                        " Edit the workout, or choose No treadmill."
                    _startProblem.value = reason
                    onMessage(reason)
                }

                is StartOutcome.Failed -> {
                    _startProblem.value = outcome.reason
                    onMessage(outcome.reason)
                }
            }
        }
    }

    /**
     * Turn the preview on or off.
     *
     * A program that is running is stopped first, in the mode it was started in: a program that
     * was being previewed has nothing to stop, and one that was driving a machine has to be
     * stopped while the machine is still the one being talked to.
     */
    fun setPreview(enabled: Boolean, byUser: Boolean = false) {
        // A choice the runner made ("No treadmill") sticks: nothing switches it back on its own.
        if (byUser) _noTreadmill.value = enabled
        scope.launch {
            if (enabled == previewMode) return@launch

            if (engine.snapshot.value.isActive) {
                engine.abandon()
                _runningProgram.value = null
            }

            previewMode = enabled
            _preview.value = enabled
            onMessage(
                if (enabled) {
                    "No treadmill: the workout runs on screen only, and nothing is sent to a machine."
                } else {
                    null
                },
            )
        }
    }

    private val _noTreadmill = MutableStateFlow(false)

    /** The runner chose to work out without a treadmill; OpenFit then neither connects nor switches it back. */
    val noTreadmill: StateFlow<Boolean> = _noTreadmill.asStateFlow()

    private val _videoHeld = MutableStateFlow(false)

    /**
     * The workout's video is stuck (an ad, buffering): the program's clock stops, so speed and incline
     * stay as they are and no step changes until the video moves again (see VideoHold).
     */
    val videoHeld: StateFlow<Boolean> = _videoHeld.asStateFlow()

    fun setVideoHeld(held: Boolean) {
        if (_videoHeld.value != held) {
            _videoHeld.value = held
            android.util.Log.i("ofit-ble", "video hold: $held")
        }
    }

    /** Pause the program. */
    fun pause() {
        scope.launch { engine.pause() }
    }

    /** Resume the program. */
    fun resume() {
        scope.launch { engine.resume() }
    }

    /** Move to the next step. */
    fun skip() {
        scope.launch { engine.skip() }
    }

    /** Jump to a point in the workout (progress bar, rewind, fast forward). */
    fun seekTo(millis: Long) {
        scope.launch { engine.seekTo(millis) }
    }

    private val _playbackRate = MutableStateFlow(1f)

    /** How fast the workout and its video play (1 = normal). The program's clock runs at this rate too. */
    val playbackRate: StateFlow<Float> = _playbackRate.asStateFlow()

    fun setPlaybackRate(rate: Float) {
        _playbackRate.value = rate.coerceIn(0.5f, 2f)
    }

    private val _matchSpeed = MutableStateFlow(true) // on by default (user request 2026-11-24)

    /**
     * "Match my speed": with a treadmill, the video (and the program's clock) run at belt speed ÷ the
     * step's programmed speed, so going faster or slower than the plan moves the scenery to match.
     */
    val matchSpeed: StateFlow<Boolean> = _matchSpeed.asStateFlow()

    fun setMatchSpeed(on: Boolean) {
        _matchSpeed.value = on
    }

    private val _effectiveRate = MutableStateFlow(1f)

    /** Whether the workout on screen has a video; without one (a virtual trail) the rate stays 1. */
    @Volatile
    var hasVideo: Boolean = true

    /** The rate actually in use: the chosen speed, or the matched one. */
    val effectiveRate: StateFlow<Float> = _effectiveRate.asStateFlow()

    /** Set by the controller once a second. */
    fun updateEffectiveRate(beltKph: Double?) {
        val snapshot = engine.snapshot.value
        // Against the workout's pace (the video's own pace, or the workout's average), not the
        // current step, so a fast step plays the video fast.
        val programmed = runningProgram.value?.let { com.ofit.protocol.MatchSpeed.referenceKph(it) }
        // The cool-down's clock runs at real time (its video is paused), so its ten minutes are ten real minutes.
        if (snapshot.isCoolingDown) {
            _effectiveRate.value = 1f
            return
        }
        // 3D workouts (no video) have a fixed time: the clock is real time whatever speed you set, so a
        // different speed changes the distance, not the time left (user 2026-11-25: "make the 3d
        // workouts fixed time"). Only a video's time follows your speed (Match my speed).
        if (!hasVideo) {
            _effectiveRate.value = 1f
            return
        }
        // No treadmill: the speed set on screen stands in for the belt (user request 2026-11-24).
        val belt = if (previewMode) snapshot.speedKph else beltKph
        val matched = if (_matchSpeed.value && snapshot.isRunning &&
            programmed != null && programmed > 0.3 && belt != null && belt > 0.3
        ) {
            com.ofit.protocol.MatchSpeed.rate(belt, programmed)
        } else {
            null
        }
        _effectiveRate.value = matched ?: _playbackRate.value
    }

    /** Override the current step's speed, subject to safety ceilings. */
    fun overrideSpeed(speedKph: Double) {
        scope.launch {
            when (val result = engine.overrideSpeed(speedKph)) {
                is LimitCheck.Accepted -> onMessage(null)
                is LimitCheck.Refused -> onMessage(result.reason.explanation)
            }
        }
    }

    /** Override the current step's incline, subject to safety ceilings. */
    fun overrideIncline(inclinePercent: Double) {
        scope.launch {
            when (val result = engine.overrideIncline(inclinePercent)) {
                is LimitCheck.Accepted -> onMessage(null)
                is LimitCheck.Refused -> onMessage(result.reason.explanation)
            }
        }
    }

    /** Return to the current step's programmed speed and incline. */
    fun resetToProgram() {
        scope.launch { engine.resetToProgram() }
    }

    /**
     * Clear a finished workout, so opening a workout from the list shows it fresh ("Not started")
     * rather than the last one's "Finished" screen. A workout that is still running is left alone.
     */
    fun clearFinished() {
        if (engine.snapshot.value.isFinished) {
            engine.reset()
            _runningProgram.value = null
        }
    }

    /** Return only the speed to the program. */
    fun resetSpeed() {
        scope.launch { engine.resetSpeedToProgram() }
    }

    /** Return only the incline to the program. */
    fun resetIncline() {
        scope.launch { engine.resetInclineToProgram() }
    }

    /**
     * The runner ended the workout (End, then Quit): with a treadmill this starts the cool-down;
     * in the cool-down it stops the belt. No treadmill, or paused: it just ends.
     */
    fun end() {
        scope.launch {
            engine.end()
            if (!engine.snapshot.value.isActive) _runningProgram.value = null
        }
    }

    /** Stop the program and the machine at once (E-STOP). */
    fun abandon() {
        scope.launch {
            engine.abandon()
            _runningProgram.value = null
        }
    }

    /** The link to the machine was lost, so the program ends. */
    fun onLinkLost() {
        scope.launch {
            engine.onLinkLost()
            _runningProgram.value = null
        }
    }
}

/** Limits for a workout run with no treadmill: only sanity bounds, nothing machine-specific. */
private val NO_TREADMILL_LIMITS = SafetyLimits(maxSpeedKph = 30.0, maxInclinePercent = 40.0)
