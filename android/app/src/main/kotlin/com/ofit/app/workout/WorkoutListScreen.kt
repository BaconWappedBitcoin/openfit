package com.ofit.app.workout

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import com.ofit.app.ui.MenuButton
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ofit.app.data.SavedWorkout

/**
 * The list of saved workout programs.
 *
 * A program is a list of speed and incline steps. It is stored on the device, so it is available
 * with no network and no account.
 */
@Composable
fun WorkoutListScreen(
    workouts: List<SavedWorkout>,
    onNew: () -> Unit,
    onEdit: (SavedWorkout) -> Unit,
    onDelete: (SavedWorkout) -> Unit,
    onRun: (SavedWorkout) -> Unit,
    onFindVideo: () -> Unit,
    onLibrary: () -> Unit,
    onShare: (SavedWorkout) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    youtubeIdFor: (String) -> String? = { null },
) {
    var confirmShare by remember { mutableStateOf<SavedWorkout?>(null) }
    var confirmDelete by remember { mutableStateOf<SavedWorkout?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Recent") }
    val units = com.ofit.app.settings.LocalUnits.current
    val accent = MaterialTheme.colorScheme.primary

    confirmShare?.let { saved ->
        AlertDialog(
            onDismissRequest = { confirmShare = null },
            title = { Text("Share this workout?") },
            text = {
                Text(
                    "\"${saved.program.name}\" will be visible to everyone using the app, and anyone " +
                        "can save a copy. Only the steps and the YouTube link are shared, not your " +
                        "name or anything else about you. It cannot be taken back from here.",
                )
            },
            confirmButton = { Button(onClick = { confirmShare = null; onShare(saved) }) { Text("Share") } },
            dismissButton = { TextButton(onClick = { confirmShare = null }) { Text("Cancel") } },
        )
    }
    confirmDelete?.let { saved ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${saved.program.name}\"?") },
            text = { Text("It is removed from this tablet. Your history of runs is kept.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(saved) }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }

    val shown = workouts
        .filter { query.isBlank() || it.program.name.contains(query.trim(), ignoreCase = true) }
        .let { list ->
            when (sort) {
                "Name" -> list.sortedBy { it.program.name.lowercase() }
                "Shortest" -> list.sortedBy { it.program.totalDurationSeconds }
                "Longest" -> list.sortedByDescending { it.program.totalDurationSeconds }
                else -> list.sortedByDescending { it.updatedAtMillis }
            }
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header: title, count and the main actions, kept compact.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.ofit.app.ui.MenuButton()
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("My workouts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${workouts.size} saved · ${formatClock(workouts.sumOf { it.program.totalDurationSeconds })} in all",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Button(onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New")
            }
            OutlinedButton(onClick = onFindVideo) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("From a video")
            }
            OutlinedButton(onClick = onLibrary) {
                Icon(Icons.Filled.Public, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Community")
            }
        }

        // Search and sort.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search your workouts") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.width(420.dp),
            )
            Spacer(Modifier.weight(1f))
            listOf("Recent", "Name", "Shortest", "Longest").forEach { option ->
                val on = option == sort
                Text(
                    option,
                    color = if (on) Ink else Color.White,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) accent else Color(0x1AFFFFFF))
                        .clickable { sort = option }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        if (workouts.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("No workouts yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Build one on the timeline, make one from a video, or save one from the community.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onNew) { Text("New workout") }
                    OutlinedButton(onClick = onFindVideo) { Text("From a video") }
                }
            }
            return@Column
        }
        if (shown.isEmpty()) {
            Text("Nothing matches \"$query\".", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            gridItems(shown, key = { it.id }) { saved ->
                WorkoutCard(
                    saved = saved,
                    videoId = youtubeIdFor(saved.id),
                    speedText = { units.speedText(it) },
                    onRun = { onRun(saved) },
                    onEdit = { onEdit(saved) },
                    onShare = { confirmShare = saved },
                    onDelete = { confirmDelete = saved },
                )
            }
        }
    }
}

private val Danger = Color(0xFFF87171)
private val Muted = Color(0x99FFFFFF)
private val Ink = Color(0xFF111111)

@Composable
private fun WorkoutCard(
    saved: SavedWorkout,
    videoId: String?,
    speedText: (Double) -> String,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = saved.program
    val accent = MaterialTheme.colorScheme.primary
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onRun), shape = RoundedCornerShape(18.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            com.ofit.app.browse.WorkoutArt(videoId, p.steps, accent, Modifier.fillMaxSize())
            Badge(formatClock(p.totalDurationSeconds), Modifier.align(Alignment.BottomEnd))
            if (videoId != null) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("Video", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val maxSpeed = p.steps.maxOfOrNull { it.speedKph } ?: 0.0
            val maxIncline = p.steps.maxOfOrNull { it.inclinePercent } ?: 0.0
            Text(
                "${p.steps.size} step${if (p.steps.size == 1) "" else "s"} · up to ${speedText(maxSpeed)} · ${"%.0f".format(maxIncline)} %",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xB3FFFFFF),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRun, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start")
                }
                CardIcon(Icons.Filled.Edit, "Edit ${p.name}", onClick = onEdit)
                CardIcon(Icons.Filled.Share, "Share ${p.name} with the community", onClick = onShare)
                CardIcon(Icons.Filled.Delete, "Delete ${p.name}", danger = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A square icon button for a workout card. */
@Composable
private fun CardIcon(icon: ImageVector, description: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AFFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (danger) Danger else Color.White)
    }
}

/**
 * The bar at the top of a screen, with a way back.
 */
@Composable
fun HeaderBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MenuButton()
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
    }
}

/** A duration as minutes and seconds. */
internal fun formatClock(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(minutes, rest)
}
