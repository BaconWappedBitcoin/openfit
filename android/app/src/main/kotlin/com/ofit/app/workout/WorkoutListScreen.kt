package com.ofit.app.workout

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
) {
    var confirmShare by remember { mutableStateOf<SavedWorkout?>(null) }

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
            confirmButton = {
                Button(onClick = {
                    confirmShare = null
                    onShare(saved)
                }) { Text("Share") }
            },
            dismissButton = { TextButton(onClick = { confirmShare = null }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        HeaderBar(title = "Workouts", onBack = onBack)

        Spacer(Modifier.height(12.dp))

        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text("New workout")
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = onFindVideo, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text("Make a workout from a YouTube video")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = onLibrary, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Public, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text("Community workouts")
        }


        Spacer(Modifier.height(12.dp))

        if (workouts.isEmpty()) {
            Text(
                text = "No workouts yet. Create one and it will be saved on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(workouts, key = { it.id }) { saved ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = saved.program.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${saved.program.steps.size} steps, " +
                                "${formatClock(saved.program.totalDurationSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = { onRun(saved) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text("Run")
                            }
                            OutlinedButton(onClick = { onEdit(saved) }) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Text("Edit")
                            }
                            IconButton(onClick = { confirmShare = saved }) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = "Share ${saved.program.name} with the community",
                                )
                            }
                            IconButton(onClick = { onDelete(saved) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove ${saved.program.name}",
                                )
                            }
                        }
                    }
                }
            }
        }
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
