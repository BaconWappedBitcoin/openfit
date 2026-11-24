package com.ofit.app.library

import com.ofit.app.data.SavedWorkout
import com.ofit.app.workout.WorkoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a video-to-workout request has got to. */
sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Working(val video: VideoHit) : AnalysisState
    data class Done(val video: VideoHit, val analysis: Analysis) : AnalysisState
    data class Failed(val video: VideoHit, val reason: String) : AnalysisState
}

/**
 * Finding running videos, turning one into a workout, and the community library.
 *
 * Everything that touches the network lives here, so the rest of the app keeps working with no
 * connection at all. A workout that has been saved to the phone never needs the server again.
 */
class LibraryManager(
    private val api: LibraryApi,
    private val workouts: WorkoutManager,
    private val onMessage: (String?) -> Unit,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _results = MutableStateFlow<List<VideoHit>>(emptyList())
    val results: StateFlow<List<VideoHit>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysis: StateFlow<AnalysisState> = _analysis.asStateFlow()

    private val _entries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    val entries: StateFlow<List<LibraryEntry>> = _entries.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _libraryError = MutableStateFlow<String?>(null)
    val libraryError: StateFlow<String?> = _libraryError.asStateFlow()

    fun search(query: String) {
        val text = query.trim()
        if (text.length < 2) return
        scope.launch {
            _searching.value = true
            _searchError.value = null
            try {
                _results.value = api.search(text)
                if (_results.value.isEmpty()) _searchError.value = "No running videos found for that."
            } catch (e: LibraryException) {
                _searchError.value = e.message
            } finally {
                _searching.value = false
            }
        }
    }

    fun analyse(video: VideoHit) {
        if (_analysis.value is AnalysisState.Working) return
        _analysis.value = AnalysisState.Working(video)
        scope.launch {
            _analysis.value = try {
                AnalysisState.Done(video, api.analyze(video.id, video.durationSeconds))
            } catch (e: LibraryException) {
                AnalysisState.Failed(video, e.message ?: "The analysis failed.")
            }
        }
    }

    fun clearAnalysis() {
        if (_analysis.value !is AnalysisState.Working) _analysis.value = AnalysisState.Idle
    }

    /** Keep an analysed workout on the phone, and optionally share it with everyone. */
    fun keep(done: AnalysisState.Done, share: Boolean) {
        workouts.saveWithVideo(done.analysis.program, done.analysis.videoId)
        if (share) {
            scope.launch {
                try {
                    api.publish(done.analysis.program, done.analysis.videoId, "analysis")
                    onMessage("Shared with the community library.")
                } catch (e: LibraryException) {
                    onMessage("Saved here, but sharing failed: ${e.message}")
                }
            }
        }
        _analysis.value = AnalysisState.Idle
    }

    fun loadLibrary(top: Boolean) {
        scope.launch {
            _loading.value = true
            _libraryError.value = null
            try {
                _entries.value = api.list(top)
            } catch (e: LibraryException) {
                _libraryError.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /** Download a community workout and save it on the phone with its video. */
    fun save(entry: LibraryEntry) {
        scope.launch {
            try {
                val (full, program) = api.get(entry.id)
                workouts.saveWithVideo(program, full.videoId)
            } catch (e: LibraryException) {
                onMessage(e.message)
            }
        }
    }

    fun rate(entry: LibraryEntry, stars: Int) {
        scope.launch {
            try {
                val updated = api.rate(entry.id, stars)
                _entries.value = _entries.value.map { if (it.id == updated.id) updated else it }
            } catch (e: LibraryException) {
                onMessage(e.message)
            }
        }
    }

    fun report(entry: LibraryEntry, reason: String) {
        scope.launch {
            try {
                if (api.report(entry.id, reason)) {
                    _entries.value = _entries.value.filterNot { it.id == entry.id }
                    onMessage("Thanks. ${entry.name} is hidden from the community library now.")
                } else {
                    onMessage("Thanks. It is hidden once a few people report it.")
                }
            } catch (e: LibraryException) {
                onMessage(e.message)
            }
        }
    }

    /** Share a workout that is already saved on the phone. */
    fun share(saved: SavedWorkout) {
        scope.launch {
            try {
                api.publish(saved.program, workouts.youtubeIdFor(saved.id), "manual")
                onMessage("Shared ${saved.program.name} with the community library.")
            } catch (e: LibraryException) {
                onMessage(e.message)
            }
        }
    }
}
