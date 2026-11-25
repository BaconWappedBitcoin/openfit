package com.ofit.app.browse

import androidx.compose.material.icons.filled.Landscape

import androidx.compose.material.icons.filled.AcUnit

import androidx.compose.material.icons.filled.Park

import androidx.compose.material.icons.filled.LocationCity

import androidx.compose.material.icons.filled.Forest

import androidx.compose.material.icons.filled.BeachAccess

import androidx.compose.material.icons.filled.Water

import androidx.compose.material.icons.filled.Terrain

import androidx.compose.foundation.border

import androidx.compose.foundation.BorderStroke

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
import androidx.compose.runtime.collectAsState
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
import com.ofit.app.workout.matches
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
    val forYouAll = remember(workouts, history) {
        val byId = workouts.associateBy { it.id }
        com.ofit.protocol.ForYou.pick(
            workouts.map { com.ofit.protocol.ForYou.Candidate(it.id, it.program.name, it.program) },
            history,
            System.currentTimeMillis(),
        ).mapNotNull { byId[it.id] }
    }
    val favouriteIds by com.ofit.app.workout.Favourites.ids(androidx.compose.ui.platform.LocalContext.current).collectAsState()
    // Effort and type filters, applied to every shelf.
    var levelFilter by remember { mutableStateOf(com.ofit.app.workout.LEVEL_FILTERS[0]) }
    var kindFilter by remember { mutableStateOf(com.ofit.app.workout.KIND_FILTERS[0]) }
    fun SavedWorkout.fits() = com.ofit.protocol.WorkoutRating.of(program).matches(levelFilter, kindFilter)

    LazyColumn(
        // Clear of the navigation bar / taskbar, which covered the last row on the tablet.
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(440.dp)) {
                if (hero != null) {
                    WorkoutArt(youtubeIdFor(hero.id), hero.program.steps, accent, Modifier.fillMaxSize(), hiRes = true)
                }
                val bg = MaterialTheme.colorScheme.background
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to bg.copy(alpha = 0.92f), 0.5f to bg.copy(alpha = 0.3f), 1f to Color.Transparent),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color(0x99000000), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to bg,
                        ),
                    ),
                )
                Row(
                    Modifier.statusBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MenuButton()
                    com.ofit.app.ui.BackButton(onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    Text("Browse", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.6f).padding(start = 40.dp, end = 24.dp, bottom = 28.dp)) {
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
                        Text(
                            hero.program.name,
                            fontSize = 40.sp,
                            lineHeight = 44.sp,
                            letterSpacing = (-1).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(summary(hero), color = Color(0xE6FFFFFF), fontSize = 16.sp)
                        Spacer(Modifier.height(18.dp))
                        Row(
                            Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color(0xFFF5F7F8))
                                .clickable { onRun(hero) }
                                .padding(horizontal = 28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF0D0D0D))
                            Spacer(Modifier.width(8.dp))
                            Text("Start", color = Color(0xFF0D0D0D), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                    }
                }
            }
        }

        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(com.ofit.app.workout.LEVEL_FILTERS) { option ->
                    androidx.compose.material3.FilterChip(selected = levelFilter == option, onClick = { levelFilter = option }, label = { Text(option) })
                }
                item { Spacer(Modifier.width(14.dp)) }
                items(com.ofit.app.workout.KIND_FILTERS) { option ->
                    androidx.compose.material3.FilterChip(selected = kindFilter == option, onClick = { kindFilter = option }, label = { Text(option) })
                }
            }
        }
        // Favourites (P16), then the rest.
        val favouriteShown = workouts.filter { it.id in favouriteIds && it.fits() }
        if (favouriteShown.isNotEmpty()) {
            item { Shelf("Favourites", favouriteShown) { WorkoutCard(it, youtubeIdFor(it.id), accent) { onRun(it) } } }
        }
        val recentShown = recent.drop(1).filter { it.fits() }
        if (recentShown.isNotEmpty()) {
            item { Shelf("Jump back in", recentShown) { WorkoutCard(it, youtubeIdFor(it.id), accent) { onRun(it) } } }
        }
        // For you (P18): like what you usually do, not run in the last two days.
        val forYou = forYouAll.filter { it.fits() }
        if (forYou.isNotEmpty()) {
            item { Shelf("For you", forYou) { WorkoutCard(it, youtubeIdFor(it.id), accent) { onRun(it) } } }
        }
        val workoutsShown = workouts.filter { it.fits() }
        if (workoutsShown.isNotEmpty()) {
            val withVideoFirst = workoutsShown.sortedWith(compareByDescending<SavedWorkout> { youtubeIdFor(it.id) != null }.thenByDescending { it.updatedAtMillis })
            item { Shelf("Your workouts", withVideoFirst) { WorkoutCard(it, youtubeIdFor(it.id), accent) { onRun(it) } } }
        }
        item {
            when {
                community.isNotEmpty() -> Shelf(
                    "Community favourites",
                    community.filter { it.effort.matches(levelFilter, kindFilter) }.sortedByDescending { it.score },
                ) { entry ->
                    CommunityCard(entry, saved = entry.name in savedNames, accent = accent) { onSaveCommunity(entry) }
                }
                communityLoading -> RowTitleOnly("Community favourites", "Loading…")
                else -> RowTitleOnly("Community favourites", "Couldn't load the community library. Check the internet connection.")
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Explore by scenery", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                // One tap to a ready search, as big picture tiles.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(SCENERY) { tile ->
                        Box(
                            Modifier
                                .width(220.dp)
                                .height(128.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(listOf(tile.from, tile.to)))
                                .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x05FFFFFF)))), RoundedCornerShape(18.dp))
                                .clickable { onFindTopic(tile.query) }
                                .padding(16.dp),
                        ) {
                            Icon(
                                tile.icon,
                                contentDescription = null,
                                tint = Color(0x33FFFFFF),
                                modifier = Modifier.align(Alignment.TopEnd).size(64.dp),
                            )
                            Text(tile.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomStart))
                        }
                    }
                }
                // Any video becomes a workout: the way in, as a banner.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.35f), MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))))
                        .clickable(onClick = onFindVideo)
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Turn any running video into a workout", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Search YouTube for a trail, city or hike; OpenFit sets the speed and incline from it.", color = Color(0xCCFFFFFF))
                    }
                    Row(
                        Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(40.dp))
                            .background(Color(0xFFF5F7F8))
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text("Find a video", color = Color(0xFF0D0D0D), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** A scenery tile: label, the search it runs, an icon and its colours. */
private data class SceneryTile(val label: String, val query: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val from: Color, val to: Color)

private val SCENERY = listOf(
    SceneryTile("Mountains", "virtual run mountain trail 4k", Icons.Filled.Terrain, Color(0xFF1E3A5F), Color(0xFF3B6E8F)),
    SceneryTile("Lakes", "virtual run lake scenery 4k", Icons.Filled.Water, Color(0xFF0E4A5C), Color(0xFF14808A)),
    SceneryTile("Beaches", "treadmill walk beach 4k", Icons.Filled.BeachAccess, Color(0xFF7A4B12), Color(0xFFC28B2C)),
    SceneryTile("Forests", "virtual hike forest 4k", Icons.Filled.Forest, Color(0xFF173D22), Color(0xFF2F6B3A)),
    SceneryTile("Cities", "city walk 4k treadmill", Icons.Filled.LocationCity, Color(0xFF2B2440), Color(0xFF51407A)),
    SceneryTile("National parks", "virtual hike national park 4k", Icons.Filled.Park, Color(0xFF2E3B14), Color(0xFF5C7424)),
    SceneryTile("Snow", "virtual winter walk snow 4k", Icons.Filled.AcUnit, Color(0xFF34495E), Color(0xFF7F9CB5)),
    SceneryTile("Hills", "treadmill hill walk scenery 4k", Icons.Filled.Landscape, Color(0xFF4A2F1A), Color(0xFF8A5A33)),
)

@Composable
private fun <T> Shelf(title: String, items: List<T>, card: @Composable (T) -> Unit) {
    Column {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp))
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


@Composable
private fun WorkoutCard(saved: SavedWorkout, videoId: String?, accent: Color, onClick: () -> Unit) {
    Column(Modifier.width(CARD_WIDTH.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Box {
            WorkoutArt(
                videoId,
                saved.program.steps,
                accent,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x05FFFFFF)))), RoundedCornerShape(14.dp)),
            )
            com.ofit.app.workout.RatingBadges(com.ofit.protocol.WorkoutRating.of(saved.program), Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
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
            entry.effort?.let { com.ofit.app.workout.RatingBadges(it, Modifier.align(Alignment.BottomStart).padding(8.dp)) }
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
    val length = if (p.totalDurationSeconds < 60) "${p.totalDurationSeconds} s" else "${p.totalDurationSeconds / 60} min"
    return "$length · ${p.steps.size} steps · up to ${units.speedText(maxSpeed)}, %.0f %%".format(maxIncline)
}
