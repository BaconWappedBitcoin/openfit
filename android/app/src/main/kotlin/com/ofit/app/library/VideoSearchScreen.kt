package com.ofit.app.library

import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.foundation.layout.aspectRatio

import androidx.compose.foundation.clickable

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ofit.app.settings.LocalUnits
import com.ofit.app.workout.HeaderBar
import com.ofit.app.workout.formatClock
import com.ofit.protocol.WorkoutProgram

/**
 * Search YouTube for a running video and turn it into a workout.
 *
 * Nothing is downloaded. The server asks a video model to watch the public video, and the answer
 * comes back as a list of steps. The video plays in YouTube's own player when the workout runs.
 */
@Composable
fun VideoSearchScreen(
    results: List<VideoHit>,
    searching: Boolean,
    error: String?,
    analysis: AnalysisState,
    onSearch: (String) -> Unit,
    onMakeWorkout: (VideoHit) -> Unit,
    onKeep: (AnalysisState.Done, Boolean) -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    hasOwnKey: Boolean = false,
    onAddKey: () -> Unit = {},
    onMakeManual: (VideoHit) -> Unit = {},
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    // The video being previewed in the popup, if any.
    var previewing by androidx.compose.runtime.remember { mutableStateOf<VideoHit?>(null) }
    previewing?.let { video ->
        VideoPreviewDialog(
            video = video,
            onClose = { previewing = null },
            onMakeWorkout = { previewing = null; onMakeWorkout(video) },
            onMakeManual = { previewing = null; onMakeManual(video) },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        HeaderBar(title = "Find a running video", onBack = onBack, subtitle = "Any running video can become a workout")
        // Bring your own key: where people need it, a one-tap way to set it up.
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                if (hasOwnKey) "Making workouts uses your own Gemini key."
                else "Making a workout from a new video needs your own free Gemini key. Videos others have made are free.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = onAddKey) {
                Text(if (hasOwnKey) "Manage key" else "Get a free key")
            }
        }
        Spacer(Modifier.height(8.dp))

        when (analysis) {
            AnalysisState.Idle -> Unit
            is AnalysisState.Working -> {
                WorkingCard(analysis.video)
                return@Column
            }
            is AnalysisState.Done -> {
                DoneCard(analysis, onKeep, onDiscard)
                return@Column
            }
            is AnalysisState.Failed -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Could not make a workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(analysis.reason, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onDiscard) { Text("Back to results") }
                    }
                }
                return@Column
            }
        }

        // The search, as a lit panel in Home's style (Lane 1b "exciting screen" pass).
        val accent = MaterialTheme.colorScheme.primary
        Row(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color(0xFF1A1A1F))))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Where do you want to run? A city, a trail, a coastline…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onSearch(query) },
                enabled = query.trim().length >= 2 && !searching,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White, contentColor = androidx.compose.ui.graphics.Color.Black),
                modifier = Modifier.height(56.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (searching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Looking for running videos…", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodyMedium)
        }
        if (results.isEmpty() && !searching && error == null) {
            SearchIdeas(onPick = { place -> query = place; onSearch(place) })
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.id }) { video ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        com.ofit.app.browse.WorkoutArt(
                            videoId = video.id,
                            steps = emptyList(),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .width(213.dp)
                                .height(120.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .clickable { previewing = video },
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(
                                "${video.channel}  ·  ${formatClock(video.durationSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            val tooShortOrLong = video.durationSeconds < 120 || video.durationSeconds > 3600
                            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onMakeWorkout(video) }, enabled = !tooShortOrLong) {
                                    Text(if (tooShortOrLong) "Needs 2 to 60 minutes" else "Make a workout (AI)")
                                }
                                // No AI: author it by hand on the timeline while the video plays.
                                androidx.compose.material3.OutlinedButton(onClick = { onMakeManual(video) }) {
                                    Text("Make it myself")
                                }
                                androidx.compose.material3.OutlinedButton(onClick = { previewing = video }) {
                                    Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Preview")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkingCard(video: VideoHit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // There is no progress report from the server, so this is an estimate from the video's
            // length (about a minute for 40 minutes of video); it waits at 95 % until the answer comes.
            val expectedMillis = (video.durationSeconds * 1500L / 60).coerceIn(20_000L, 180_000L)
            var elapsed by androidx.compose.runtime.remember { mutableStateOf(0L) }
            androidx.compose.runtime.LaunchedEffect(video.id) {
                val start = System.currentTimeMillis()
                while (true) {
                    elapsed = System.currentTimeMillis() - start
                    kotlinx.coroutines.delay(250)
                }
            }
            val share = com.ofit.protocol.AnalysisProgress.estimate(elapsed, expectedMillis)
            Text("Making your workout", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { share },
                    modifier = Modifier.weight(1f).height(10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Text("${(share * 100).toInt()} %", fontWeight = FontWeight.Bold)
            }
            Text(
                com.ofit.protocol.AnalysisProgress.stage(share) + " · about ${expectedMillis / 1000} s in all (estimated)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "A video model works out the speed and slope of each half minute. Keep this screen open.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DoneCard(done: AnalysisState.Done, onKeep: (AnalysisState.Done, Boolean) -> Unit, onDiscard: () -> Unit) {
    val units = LocalUnits.current
    val program: WorkoutProgram = done.analysis.program
    val speeds = program.steps.map { it.speedKph }
    val inclines = program.steps.map { it.inclinePercent }
    var share by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(program.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Text("${program.steps.size} steps, ${formatClock(program.totalDurationSeconds)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Speed ${units.speedFromKph(speeds.min()).fmt()} to ${units.speedText(speeds.max())}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Incline ${inclines.min().fmt()} to ${inclines.max().fmt()} %",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "These numbers are estimates made by a video model, not measurements " +
                    "(its own confidence: ${(done.analysis.confidence * 100).toInt()} %). " +
                    "Check the first minutes against how it feels, and edit any step you like.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = share, onCheckedChange = { share = it })
                Text("Also share it with the community library", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onKeep(done, share) }) { Text("Save workout") }
                OutlinedButton(onClick = onDiscard) { Text("Discard") }
            }
        }
    }
}

private fun Double.fmt() = "%.1f".format(java.util.Locale.ROOT, this)

/** A preview of a search result: the video playing, its details, and the two ways to make a workout. */
@Composable
private fun VideoPreviewDialog(video: VideoHit, onClose: () -> Unit, onMakeWorkout: () -> Unit, onMakeManual: () -> Unit) {
    // Start a quarter of the way in, where the scenery usually is, and let it play from there.
    val startMillis = (video.durationSeconds * 1000L / 4).toInt()
    val openedAt = androidx.compose.runtime.remember { System.currentTimeMillis() }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.75f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color.Black),
            ) {
                com.ofit.app.workout.YouTubePlayer(
                    videoId = video.id,
                    playing = true,
                    positionMillis = { startMillis + (System.currentTimeMillis() - openedAt).toInt() },
                    seekRequest = 1,
                    volume = 60,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(video.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
            Text("${video.channel}  ·  ${formatClock(video.durationSeconds)}", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val tooShortOrLong = video.durationSeconds < 120 || video.durationSeconds > 3600
                Button(onClick = onMakeWorkout, enabled = !tooShortOrLong) {
                    Text(if (tooShortOrLong) "Needs 2 to 60 minutes" else "Make a workout (AI)")
                }
                OutlinedButton(onClick = onMakeManual) { Text("Make it myself") }
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}

/** Places to try, as colour tiles: one tap searches. */
private val IDEAS = listOf(
    Triple("Swiss Alps", "Lakes and mountain paths", listOf(0xFF1E6091, 0xFF52B69A)),
    Triple("Tokyo", "City streets and rivers", listOf(0xFF7B2CBF, 0xFFE0AAFF)),
    Triple("Paris", "Along the Seine", listOf(0xFF3A0CA3, 0xFF4CC9F0)),
    Triple("Beach run", "Sand, surf and sun", listOf(0xFFF77F00, 0xFFFCBF49)),
    Triple("Forest trail", "Shade and soft ground", listOf(0xFF2D6A4F, 0xFF95D5B2)),
    Triple("Snowy trail", "Winter woods", listOf(0xFF457B9D, 0xFFE0FBFC)),
    Triple("New York", "Parks and avenues", listOf(0xFFD00000, 0xFFFFBA08)),
    Triple("Coastal path", "Cliffs and sea views", listOf(0xFF006D77, 0xFF83C5BE)),
)

@Composable
private fun SearchIdeas(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Where to today?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        IDEAS.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (place, detail, colors) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(120.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(colors.map { androidx.compose.ui.graphics.Color(it) }))
                            .clickable { onPick(place) }
                            .padding(18.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(place, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(detail, color = androidx.compose.ui.graphics.Color(0xE6FFFFFF), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Text("How it works", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                Triple("1", "Find a video", "Any walking or running video on YouTube that can be played in other apps."),
                Triple("2", "Let AI read it", "It watches the video and works out the pace and the hills, step by step."),
                Triple("3", "Run it", "The treadmill follows the video, and the video can follow your speed."),
            ).forEach { (n, title, text) ->
                Row(
                    Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Text(n, color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold) }
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color(0xB3FFFFFF))
                    }
                }
            }
        }
        Text("Only videos that can be played in other apps are listed.", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0x99FFFFFF))
    }
}
