package com.ofit.app.browse

import com.ofit.app.ui.MenuButton
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ofit.app.data.SavedWorkout
import com.ofit.app.library.LibraryEntry
import com.ofit.app.settings.LocalUnits
import com.ofit.protocol.HistoryEntry

/**
 * Browse: a hero banner for the workout to do next, then rows of cards (the home layout of media
 * apps such as NuvioMobile, written for OpenFit): jump back in, your workouts, community favourites,
 * and finding a new video.
 */
@Composable
fun BrowseScreen(
    workouts: List<SavedWorkout>,
    community: List<LibraryEntry>,
    communityLoading: Boolean,
    history: List<HistoryEntry>,
    youtubeIdFor: (String) -> String?,
    onLoadCommunity: () -> Unit,
    onRun: (SavedWorkout) -> Unit,
    onSaveCommunity: (LibraryEntry) -> Unit,
    onFindVideo: () -> Unit,
    onBack: () -> Unit,
    onFindTopic: (String) -> Unit = { onFindVideo() },
) {
    LaunchedEffect(Unit) { if (community.isEmpty()) onLoadCommunity() }
    val accent = MaterialTheme.colorScheme.primary

    // Recently run, matched to saved workouts by name, most recent first.
    val recent = history.map { it.name }.distinct().mapNotNull { name -> workouts.firstOrNull { it.program.name == name } }
    val withVideo = workouts.filter { youtubeIdFor(it.id) != null }
    val hero = recent.firstOrNull() ?: withVideo.maxByOrNull { it.updatedAtMillis } ?: workouts.maxByOrNull { it.updatedAtMillis }
    val savedNames = workouts.map { it.program.name }.toSet()

    LazyColumn(
        // Clear of the navigation bar / taskbar, which covered the last row on the tablet.
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                if (hero != null) {
                    WorkoutArt(youtubeIdFor(hero.id), hero.program.steps, accent, Modifier.fillMaxSize())
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color(0x99000000), 0.35f to Color(0x22000000), 1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                Row(
                    Modifier.statusBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MenuButton()
                    OutlinedButton(onClick = onBack) { Text("Back", color = Color.White) }
                    Spacer(Modifier.width(12.dp))
                    Text("Browse", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 12.dp)) {
                    if (hero == null) {
                        Text("No workouts yet", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Find a video below and OpenFit turns it into a workout.", color = Color(0xCCFFFFFF))
                    } else {
                        Text(
                            if (hero in recent) "JUMP BACK IN" else "FEATURED",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(hero.program.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(summary(hero), color = Color(0xCCFFFFFF), fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onRun(hero) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Open workout", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (recent.size > 1) {
            item { Shelf("Jump back in", recent.drop(1)) { WorkoutCard(it, youtubeIdFor(it.id), accent) { onRun(it) } } }
        }
        if (workouts.isNotEmpty()) {
            val withVideoFirst = workouts.sortedWith(compareByDescending<SavedWorkout> { youtubeIdFor(it.id) != null }.thenByDescending { it.updatedAtMillis })
            item { Shelf("Your workouts", withVideoFirst) { WorkoutCard(it, youtubeIdFor(it.id), accent) { onRun(it) } } }
        }
        item {
            when {
                community.isNotEmpty() -> Shelf("Community favourites", community.sortedByDescending { it.rating }) { entry ->
                    CommunityCard(entry, saved = entry.name in savedNames, accent = accent) { onSaveCommunity(entry) }
                }
                communityLoading -> RowTitleOnly("Community favourites", "Loading…")
                else -> RowTitleOnly("Community favourites", "Couldn't load the community library. Check the internet connection.")
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("Find a new video", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onFindVideo)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Search YouTube for a trail, city or hike walk; OpenFit sets the speed and incline from it.", color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                // One tap to a ready search, like the genre rows of media apps.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(VIDEO_TOPICS) { (label, query) ->
                        Text(
                            label,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x1FFFFFFF))
                                .clickable { onFindTopic(query) }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> Shelf(title: String, items: List<T>, card: @Composable (T) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { card(it) }
        }
    }
}

@Composable
private fun RowTitleOnly(title: String, note: String) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(note, color = Color(0x99FFFFFF))
    }
}

private const val CARD_WIDTH = 300

/** Ready searches: a label and the YouTube query it runs. */
private val VIDEO_TOPICS = listOf(
    "Mountain trails" to "virtual run mountain trail 4k",
    "Lakes" to "virtual run lake scenery 4k",
    "Beaches" to "treadmill walk beach 4k",
    "Forests" to "virtual hike forest 4k",
    "Cities" to "city walk 4k treadmill",
    "National parks" to "virtual hike national park 4k",
    "Snow" to "virtual winter walk snow 4k",
    "Hills" to "treadmill hill walk scenery 4k",
)

@Composable
private fun WorkoutCard(saved: SavedWorkout, videoId: String?, accent: Color, onClick: () -> Unit) {
    Column(Modifier.width(CARD_WIDTH.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        WorkoutArt(videoId, saved.program.steps, accent, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(6.dp))
        Text(saved.program.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(summary(saved), fontSize = 13.sp, color = Color(0x99FFFFFF))
    }
}

@Composable
private fun CommunityCard(entry: LibraryEntry, saved: Boolean, accent: Color, onSave: () -> Unit) {
    var tapped by remember { mutableStateOf(false) }
    val isSaved = saved || tapped
    Column(
        Modifier
            .width(CARD_WIDTH.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !isSaved) { tapped = true; onSave() },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) {
            WorkoutArt(entry.videoId, emptyList(), accent, Modifier.fillMaxSize())
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSaved) Color(0xCC1B5E20) else Color(0xCC000000))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isSaved) Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(if (isSaved) "In your workouts" else "Save", color = Color.White, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC857), modifier = Modifier.size(14.dp))
            Text(
                " %.1f (%d) · %d min · %d steps".format(entry.rating, entry.ratings, entry.durationSeconds / 60, entry.steps),
                fontSize = 13.sp,
                color = Color(0x99FFFFFF),
            )
        }
    }
}

@Composable
private fun summary(saved: SavedWorkout): String {
    val units = LocalUnits.current
    val p = saved.program
    val maxSpeed = p.steps.maxOfOrNull { it.speedKph } ?: 0.0
    val maxIncline = p.steps.maxOfOrNull { it.inclinePercent } ?: 0.0
    return "${p.totalDurationSeconds / 60} min · ${p.steps.size} steps · up to ${units.speedText(maxSpeed)}, %.0f %%".format(maxIncline)
}
