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
            val sink = if (previewMode) previewSink else machineSink
            activeSink = sink
            return sink.startWorkout(speedKph, inclinePercent)
        }

        override suspend fun applyStep(speedKph: Double, inclinePercent: Double): Boolean =
            activeSink.applyStep(speedKph, inclinePercent)

        override suspend fun stopWorkout(): Boolean = activeSink.stopWorkout()
    }

    /** The engine that runs a program. */
    val engine = WorkoutEngine(limitsProvider, switchingSink)

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
     * A program that is out of bounds is rejected before anything is sent to the machine.
     */
    fun start(program: WorkoutProgram) {
        scope.launch {
            when (val outcome = engine.start(program)) {
                is StartOutcome.Started -> {
                    _runningProgram.value = program
                    onMessage(null)
                }

                is StartOutcome.Rejected -> {
                    onMessage(outcome.problems.joinToString("\n") { problem -> problem.message })
                }

                is StartOutcome.Failed -> onMessage(outcome.reason)
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
    fun setPreview(enabled: Boolean) {
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
                    "Preview: the program runs without a treadmill, and nothing is sent to a machine."
                } else {
                    null
                },
            )
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

    /** Stop the program and the machine. */
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