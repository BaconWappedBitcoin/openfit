package com.ofit.app.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.browse.WorkoutArt
import com.ofit.app.workout.HeaderBar
import com.ofit.app.workout.formatClock
import com.ofit.app.workout.matches
import java.util.Locale

/** What the Community list can be narrowed to (besides the search box). */
enum class CommunityFilter(val label: String) {
    ALL("All"),
    VIDEO("With video"),
    SHORT("Under 20 min"),
    LONG("40 min +"),
    FROM_VIDEO("Made from a video"),
}

/** Search and filter the community list; pure, so it is easy to reason about. */
fun filterCommunity(
    entries: List<LibraryEntry>,
    query: String,
    filter: CommunityFilter,
    level: String = com.ofit.app.workout.LEVEL_FILTERS[0],
    kind: String = com.ofit.app.workout.KIND_FILTERS[0],
): List<LibraryEntry> {
    val q = query.trim()
    return entries
        .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
        .filter {
            when (filter) {
                CommunityFilter.ALL -> true
                CommunityFilter.VIDEO -> it.videoId != null
                CommunityFilter.SHORT -> it.durationSeconds < 20 * 60
                CommunityFilter.LONG -> it.durationSeconds >= 40 * 60
                CommunityFilter.FROM_VIDEO -> it.origin == "analysis"
            }
        }
        .filter { it.effort.matches(level, kind) }
}

/**
 * The community library: workouts other people made and shared, as the same picture grid as
 * My workouts, with search and filters. A workout here is only its steps and a YouTube video id;
 * saving one puts the steps on this tablet.
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
    /** Report a workout to moderation, with one of the server's reasons. */
    onReport: (LibraryEntry, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    /** Names already in My workouts, to mark those as saved. */
    savedNames: Set<String> = emptySet(),
) {
    var top by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CommunityFilter.ALL) }
    var levelFilter by rememberSaveable { mutableStateOf(com.ofit.app.workout.LEVEL_FILTERS[0]) }
    var kindFilter by rememberSaveable { mutableStateOf(com.ofit.app.workout.KIND_FILTERS[0]) }
    LaunchedEffect(top) { onLoad(top) }
    val shown = remember(entries, query, filter, levelFilter, kindFilter) { filterCommunity(entries, query, filter, levelFilter, kindFilter) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeaderBar(title = "Community workouts", onBack = onBack, subtitle = "${entries.size} workouts other runners have shared")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search the community") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(320.dp),
            )
            CommunityFilter.entries.forEach { option ->
                FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
            }
            Spacer(Modifier.weight(1f))
            Text("Sort", style = MaterialTheme.typography.labelLarge)
            FilterChip(selected = !top, onClick = { top = false }, label = { Text("Newest") })
            FilterChip(selected = top, onClick = { top = true }, label = { Text("Top rated") })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.ofit.app.workout.LEVEL_FILTERS.forEach { option ->
                FilterChip(selected = levelFilter == option, onClick = { levelFilter = option }, label = { Text(option) })
            }
            Spacer(Modifier.width(14.dp))
            com.ofit.app.workout.KIND_FILTERS.forEach { option ->
                FilterChip(selected = kindFilter == option, onClick = { kindFilter = option }, label = { Text(option) })
            }
        }

        if (loading && entries.isEmpty()) CircularProgressIndicator()
        if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium)
        if (!loading && error == null && entries.isEmpty()) {
            Text(
                "Nothing has been shared yet. Make a workout from a video, or share one of yours " +
                    "from My workouts, and it shows up here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (entries.isNotEmpty() && shown.isEmpty()) {
            Text("No community workouts match.", color = Color(0x99FFFFFF))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(shown, key = { it.id }) { entry ->
                CommunityGridCard(entry, saved = entry.name in savedNames, onSave = { onSave(entry) }, onRate = { onRate(entry, it) }, onReport = { onReport(entry, it) })
            }
        }
    }
}

@Composable
private fun CommunityGridCard(entry: LibraryEntry, saved: Boolean, onSave: () -> Unit, onRate: (Int) -> Unit, onReport: (String) -> Unit = {}) {
    var tapped by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    if (reporting) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { reporting = false },
            title = { Text("Report ${entry.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What's wrong with it? A video that has gone bad is hidden at once; otherwise it is hidden when a few people report it.", color = Color(0xB3FFFFFF))
                    REPORT_REASONS.forEach { (id, label) ->
                        Text(
                            label,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x14FFFFFF))
                                .clickable { reporting = false; onReport(id) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { reporting = false }) { Text("Cancel") } },
        )
    }
    val isSaved = saved || tapped
    val accent = MaterialTheme.colorScheme.primary
    Card(shape = RoundedCornerShape(18.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x05FFFFFF)))), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        ) {
            WorkoutArt(entry.videoId, emptyList(), accent, Modifier.fillMaxSize())
            Badge(formatClock(entry.durationSeconds), Modifier.align(Alignment.BottomEnd))
            // Report (moderation): a small flag in the corner.
            Icon(
                Icons.Filled.Flag,
                contentDescription = "Report ${entry.name}",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x99000000))
                    .clickable { reporting = true }
                    .padding(10.dp),
            )
            entry.effort?.let { com.ofit.app.workout.RatingBadges(it, Modifier.align(Alignment.BottomStart).padding(10.dp)) }
            if (entry.ratings > 0) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC857), modifier = Modifier.size(16.dp))
                    Text(" %.1f".format(Locale.ROOT, entry.rating), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${entry.steps} steps" + (if (entry.origin == "analysis") " · made from a video" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xB3FFFFFF),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Save: a wide pill, then the stars to rate it.
                Row(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isSaved) Color(0x334ADE80) else accent)
                        .clickable(enabled = !isSaved) { tapped = true; onSave() }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (isSaved) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null, tint = if (isSaved) Color(0xFF4ADE80) else Color(0xFF111111))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isSaved) "In your workouts" else "Save",
                        color = if (isSaved) Color(0xFF4ADE80) else Color(0xFF111111),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                for (star in 1..5) {
                    Icon(
                        if (star <= Math.round(entry.rating).toInt() && entry.ratings > 0) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Rate $star of 5",
                        tint = Color(0xFFFFC857),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onRate(star) }
                            .padding(3.dp),
                    )
                }
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
        fontSize = 14.sp,
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}


/** The reasons the server accepts (backend/lib/moderation.ts), with what the runner sees. */
private val REPORT_REASONS = listOf(
    "adult" to "Sexual or adult content",
    "violent" to "Violent or shocking",
    "hateful" to "Hateful or abusive",
    "illegal" to "Illegal or dangerous",
    "spam" to "Spam or a scam",
    "broken" to "The video does not play",
    "other" to "Something else",
)
