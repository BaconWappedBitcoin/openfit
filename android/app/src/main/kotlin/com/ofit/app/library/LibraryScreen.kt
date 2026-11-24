package com.ofit.app.library

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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ofit.app.workout.HeaderBar
import com.ofit.app.workout.formatClock
import java.util.Locale

/**
 * The community library: workouts other people made and shared.
 *
 * A workout here is only a list of steps and the id of a YouTube video. Saving one puts the steps
 * on the phone; the video is shown by YouTube's own player when the workout is run.
 */
@Composable
fun LibraryScreen(
    entries: List<LibraryEntry>,
    loading: Boolean,
    error: String?,
    onLoad: (top: Boolean) -> Unit,
    onSave: (LibraryEntry) -> Unit,
    onRate: (LibraryEntry, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var top by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(top) { onLoad(top) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        HeaderBar(title = "Community workouts", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !top, onClick = { top = false }, label = { Text("Newest") })
            FilterChip(selected = top, onClick = { top = true }, label = { Text("Top rated") })
        }
        Spacer(Modifier.height(8.dp))

        if (loading && entries.isEmpty()) CircularProgressIndicator()
        if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium)
        if (!loading && error == null && entries.isEmpty()) {
            Text(
                "Nothing has been shared yet. Make a workout from a video, or share one of yours " +
                    "from the Workouts list, and it will show up here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(
                            "${entry.steps} steps, ${formatClock(entry.durationSeconds)}" +
                                (if (entry.videoId != null) ", with video" else "") +
                                (if (entry.origin == "analysis") ", made from a video" else ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            for (star in 1..5) {
                                IconButton(onClick = { onRate(entry, star) }) {
                                    Icon(
                                        if (star <= Math.round(entry.rating).toInt() && entry.ratings > 0) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Rate $star of 5",
                                    )
                                }
                            }
                            Text(
                                if (entry.ratings == 0) "not rated yet" else "%.1f (%d)".format(Locale.ROOT, entry.rating, entry.ratings),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = { onSave(entry) }) { Text("Save to my workouts") }
                    }
                }
            }
        }
    }
}
