package com.ofit.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.browse.WorkoutArt
import com.ofit.app.data.SavedWorkout
import com.ofit.app.library.LibraryEntry
import com.ofit.app.settings.LocalUnits
import com.ofit.protocol.ConnectionState
import com.ofit.protocol.HistoryEntry
import com.ofit.protocol.HistoryTotals
import com.ofit.protocol.MachineReadiness
import com.ofit.protocol.PersonalBests
import com.ofit.protocol.TreadmillState
import com.ofit.protocol.WorkoutTemplates
import kotlinx.coroutines.delay
import java.util.Calendar

/** Minutes a week the goal ring counts towards (the common 150-minute guideline). */
private const val WEEKLY_GOAL_MINUTES = 150

/**
 * Home: a featured-workout banner to start from, this week at a glance, and shelves of workouts to
 * pick from (the layout common to training and media apps; styled after Nuvio's design system).
 */
@Composable
fun HomeDashboard(
    state: TreadmillState,
    treadmillName: String?,
    history: List<HistoryEntry>,
    workouts: List<SavedWorkout>,
    onConnect: () -> Unit,
    onFindTreadmill: () -> Unit,
    onOpenWorkout: (SavedWorkout) -> Unit,
    onBrowse: () -> Unit,
    onMyWorkouts: () -> Unit,
    onFindVideo: () -> Unit,
    onHistory: () -> Unit,
    /** A workout to pick up again (P4): the workout and where it was. */
    resume: Pair<SavedWorkout, com.ofit.protocol.ResumePoint>? = null,
    onResume: (SavedWorkout, Long) -> Unit = { _, _ -> },
    onDismissResume: () -> Unit = {},
    /** The training plan followed (P11/P12), shown when today's session is due. */
    plan: com.ofit.protocol.TrainingPlans.Status? = null,
    onPlanStart: (com.ofit.protocol.WorkoutProgram) -> Unit = {},
    onPlans: () -> Unit = {},
    onManual: () -> Unit,
    onQuickStart: () -> Unit = {},
    youtubeIdFor: (String) -> String? = { null },
    onEdit: (SavedWorkout) -> Unit = {},
    community: List<LibraryEntry> = emptyList(),
    onLoadCommunity: () -> Unit = {},
    onTemplate: (WorkoutTemplates.Template) -> Unit = {},
    /** Play a template as it is (a 3D run with the scenery choices before Start), instead of editing it. */
    onPlayTemplate: (WorkoutTemplates.Template) -> Unit = {},
    /** Open Virtual trails with this landscape picked. */
    onTrail: (String) -> Unit = {},
) {
    LaunchedEffect(Unit) { if (community.isEmpty()) onLoadCommunity() }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // 0. Pick up where you left off (P4): OpenFit closed while this workout was running.
        resume?.let { (saved, point) ->
            fun clock(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), Color(0xFF1A1A1A))))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("PICK UP WHERE YOU LEFT OFF", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF))
                    Text(saved.program.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("OpenFit closed at ${clock(point.elapsedMillis)} of ${clock(point.totalMillis)}.", style = MaterialTheme.typography.bodyMedium, color = Color(0xCCFFFFFF))
                }
                androidx.compose.material3.Button(onClick = { onResume(saved, point.elapsedMillis) }) { Text("Resume") }
                androidx.compose.material3.OutlinedButton(onClick = onDismissResume) { Text("Dismiss") }
            }
        }

        // 0b. Today's training plan session (P11/P12).
        plan?.takeIf { it.dueToday }?.let { status ->
            val next = status.next ?: return@let
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), Color(0xFF1A1A1A))))
                    .clickable(onClick = onPlans)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TODAY'S SESSION · ${status.plan.name.uppercase()}", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF))
                    Text(next.name.substringAfter(" · "), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${next.totalDurationSeconds / 60} min · ${status.done} of ${status.plan.totalSessions} sessions done", style = MaterialTheme.typography.bodyMedium, color = Color(0xCCFFFFFF))
                }
                androidx.compose.material3.Button(onClick = { onPlanStart(next) }) { Text("Start") }
                androidx.compose.material3.OutlinedButton(onClick = onPlans) { Text("Plan") }
            }
        }

        // 1. Featured: the workout last run first, then others with a video.
        val last = history.firstOrNull()
        val resume = last?.let { entry -> workouts.firstOrNull { it.program.name == entry.name } }
        val featured = remember(workouts, last) {
            (listOfNotNull(resume) + workouts.sortedByDescending { youtubeIdFor(it.id) != null }.sortedByDescending { it.updatedAtMillis })
                .distinctBy { it.id }
                .take(5)
        }
        if (featured.isNotEmpty()) {
            HeroCarousel(featured, resume, last, youtubeIdFor, onOpenWorkout, onEdit)
        } else {
            EmptyHero(onFindVideo, onQuickStart)
        }

        // 2. This week at a glance.
        // Three cards of one size (user request 2026-11-24): equal widths, and as tall as the tallest.
        Row(
            Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WeekGoalCard(history, onHistory, Modifier.weight(1f).fillMaxHeight())
            StreakCard(history, onHistory, Modifier.weight(1f).fillMaxHeight())
            TreadmillCard(state, treadmillName, onConnect, onFindTreadmill, Modifier.weight(1f).fillMaxHeight())
        }

        // 3. Shelves.
        if (workouts.isNotEmpty()) {
            Shelf("Your workouts", onSeeAll = onMyWorkouts) {
                items(workouts.sortedByDescending { it.updatedAtMillis }, key = { it.id }) { saved ->
                    PosterCard(
                        title = saved.program.name,
                        detail = "${lengthText(saved.program.totalDurationSeconds)} · ${saved.program.steps.size} steps",
                        onClick = { onOpenWorkout(saved) },
                    ) { WorkoutArt(youtubeIdFor(saved.id), saved.program.steps, accent, Modifier.fillMaxSize()) }
                }
            }
        }
        if (community.isNotEmpty()) {
            Shelf("From the community", onSeeAll = onBrowse) {
                items(community.take(12), key = { it.id }) { entry ->
                    PosterCard(
                        title = entry.name,
                        detail = "${entry.durationSeconds / 60} min" + (if (entry.ratings > 0) " · ★ %.1f".format(entry.rating) else ""),
                        onClick = onBrowse,
                    ) { WorkoutArt(entry.videoId, emptyList(), accent, Modifier.fillMaxSize()) }
                }
            }
        }
        // 3D landscapes to run through, no video needed.
        Shelf("Virtual trails", onSeeAll = { onTrail(com.ofit.protocol.TrailProgram.TRAILS.first().key) }) {
            items(com.ofit.protocol.TrailProgram.TRAILS, key = { it.key }) { trail ->
                PosterCard(title = trail.name, detail = trail.blurb, onClick = { onTrail(trail.key) }) { TrailPicture(trail.key) }
            }
        }
        // Tap to play (3D, pick the scenery before Start); the pencil edits it instead (user 2026-11-25).
        Shelf("Start something new", onSeeAll = null) {
            items(WorkoutTemplates.all, key = { it.name }) { template ->
                val blank = template.name == "Blank" // nothing to play: it is for building
                PosterCard(
                    title = template.name, detail = template.description,
                    onClick = { if (blank) onTemplate(template) else onPlayTemplate(template) },
                    corner = if (blank) null else ({ EditCorner { onTemplate(template) } }),
                ) {
                    WorkoutArt(null, template.steps, accent, Modifier.fillMaxSize())
                }
            }
        }

        // 4. Everything else, as pills.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Quick start", Icons.Filled.Bolt, onQuickStart)
            Pill("Browse", Icons.Filled.Movie, onBrowse)
            Pill("My workouts", Icons.AutoMirrored.Filled.List, onMyWorkouts)
            Pill("Find a video", Icons.Filled.Search, onFindVideo)
            Pill("History", Icons.Filled.History, onHistory)
            Pill("Manual control", Icons.Filled.Tune, onManual)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroCarousel(
    featured: List<SavedWorkout>,
    resume: SavedWorkout?,
    last: HistoryEntry?,
    youtubeIdFor: (String) -> String?,
    onOpen: (SavedWorkout) -> Unit,
    onEdit: (SavedWorkout) -> Unit,
) {
    val pager = rememberPagerState { featured.size }
    // Turns by itself every 8 s, like a featured row; a swipe takes over.
    LaunchedEffect(featured.size) {
        while (featured.size > 1) {
            delay(8_000)
            pager.animateScrollToPage((pager.currentPage + 1) % featured.size)
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxWidth()
            .height(400.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val saved = featured[page]
            val p = saved.program
            Box(Modifier.fillMaxSize().clickable { onOpen(saved) }) {
                WorkoutArt(youtubeIdFor(saved.id), p.steps, accent, Modifier.fillMaxSize(), hiRes = true)
                // Scrims: dark on the left for the text, and a fade into the page at the bottom.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to bg.copy(alpha = 0.95f), 0.55f to bg.copy(alpha = 0.35f), 1f to Color.Transparent),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to bg.copy(alpha = 0.85f)),
                    ),
                )
                Column(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.56f)
                        .padding(start = 40.dp, top = 36.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val isResume = resume?.id == saved.id && last != null
                    Text(
                        if (isResume) "START WHERE YOU LEFT OFF" else "FEATURED",
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        p.name,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    val units = LocalUnits.current
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Meta(Icons.Filled.Timer, lengthText(p.totalDurationSeconds))
                        Meta(Icons.Filled.Speed, "up to " + units.speedText(p.steps.maxOfOrNull { it.speedKph } ?: 0.0))
                        Meta(Icons.AutoMirrored.Filled.TrendingUp, "%.0f %% max".format(p.steps.maxOfOrNull { it.inclinePercent } ?: 0.0))
                        if (isResume && last != null) Meta(Icons.Filled.History, "last run " + relativeDay(last.startedAtMillis))
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color(0xFFF5F7F8))
                                .clickable { onOpen(saved) }
                                .padding(horizontal = 28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF0D0D0D))
                            Spacer(Modifier.width(8.dp))
                            Text("Start", color = Color(0xFF0D0D0D), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable { onEdit(saved) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White) }
                    }
                }
            }
        }
        // Page dots.
        if (featured.size > 1) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(featured.size) { i ->
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (i == pager.currentPage) 22.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) Color.White else Color(0x66FFFFFF)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHero(onFindVideo: () -> Unit, onQuickStart: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)))
            .padding(40.dp),
    ) {
        Text("WELCOME", color = accent, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Run anywhere in the world", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("Find a running video", Icons.Filled.Search, onFindVideo)
            Pill("Quick start", Icons.Filled.Bolt, onQuickStart)
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color(0xE6FFFFFF), fontSize = 15.sp)
    }
}

/** A titled horizontal row of cards, with "See all" at the end of the title. */
@Composable
private fun Shelf(title: String, onSeeAll: (() -> Unit)?, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onSeeAll)
                        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("See all", fontSize = 14.sp)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 16.dp), content = content)
    }
}

/** A landscape poster: the picture with a subtle lit edge, then the title and a line under it. */
@Composable
private fun PosterCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
    corner: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    art: @Composable () -> Unit,
) {
    Column(Modifier.width(300.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x05FFFFFF)))),
                    RoundedCornerShape(14.dp),
                ),
        ) {
            art()
            corner?.invoke(this)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0x99FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Pill(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

/** This week's minutes as a ring towards the weekly goal, with the week's totals beside it. */
@Composable
private fun WeekGoalCard(history: List<HistoryEntry>, onHistory: () -> Unit, modifier: Modifier) {
    val units = LocalUnits.current
    val weekStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val week = HistoryTotals.of(history.filter { it.startedAtMillis >= weekStart })
    val minutes = (week.durationSeconds / 60).toInt()
    val share = (minutes / WEEKLY_GOAL_MINUTES.toFloat()).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    HomeCard(modifier.clickable(onClick = onHistory)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 11.dp.toPx()
                    val inset = w / 2
                    val arc = Size(size.width - w, size.height - w)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
                    drawArc(accent, -90f, 360f * share, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$minutes", fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                    Text("of $WEEKLY_GOAL_MINUTES min", fontSize = 11.sp, color = Color(0x99FFFFFF))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("THIS WEEK", style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Big(week.workouts.toString(), if (week.workouts == 1) "workout" else "workouts")
                    Big(units.distanceValueText(week.distanceMeters), units.distanceUnitLong)
                    Big("%.0f".format(week.calories), "cals")
                }
            }
        }
    }
}

@Composable
private fun StreakCard(history: List<HistoryEntry>, onHistory: () -> Unit, modifier: Modifier) {
    val bests = remember(history) { PersonalBests.of(history) }
    HomeCard(modifier.clickable(onClick = onHistory)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color(0x33FB923C)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFB923C), modifier = Modifier.size(36.dp)) }
            Column {
                Text("STREAK", style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF))
                Text(
                    "${bests.weekStreak} " + if (bests.weekStreak == 1) "week" else "weeks",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("best ${bests.bestWeekStreak} · in a row", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TreadmillCard(
    state: TreadmillState,
    name: String?,
    onConnect: () -> Unit,
    onFind: () -> Unit,
    modifier: Modifier,
) {
    val connected = state.connectionState == ConnectionState.CONNECTED
    val (title, detail) = when {
        name == null -> "No treadmill yet" to "Find yours once; OpenFit remembers it."
        state.readiness == MachineReadiness.BUSY_LOCAL -> "In use by hand" to "OpenFit sends nothing."
        connected && state.workoutMode == com.ofit.protocol.WorkoutMode.RUNNING -> "Workout running" to name
        connected -> "Connected" to name
        state.connectionState == ConnectionState.CONNECTING -> "Connecting…" to name
        else -> "Ready" to "$name · connects when you start"
    }
    HomeCard(modifier.clickable { if (name == null) onFind() else if (!connected) onConnect() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (connected) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text("TREADMILL", style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF))
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A card in the page's style: the surface colour, 24 dp corners, a faint lit top edge. */
@Composable
private fun HomeCard(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x05FFFFFF)))), RoundedCornerShape(24.dp))
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun Big(value: String, label: String) {
    Column {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun relativeDay(millis: Long): String {
    val days = ((System.currentTimeMillis() - millis) / 86_400_000L).toInt()
    return when (days) {
        0 -> "today"
        1 -> "yesterday"
        else -> "$days days ago"
    }
}

/** "39 min", or "30 s" for a workout under a minute. */
private fun lengthText(seconds: Int): String = if (seconds < 60) "$seconds s" else "${seconds / 60} min"

/** A small pencil in the corner of a poster: edit instead of play. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.EditCorner(onClick: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xAA0D0D0D))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** A virtual trail's picture (assets/world/preview/<key>.jpg), or a plain colour if it is missing. */
@Composable
private fun TrailPicture(key: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picture = remember(key) {
        runCatching { context.assets.open("world/preview/$key.jpg").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    if (picture != null) {
        androidx.compose.foundation.Image(picture, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Box(Modifier.fillMaxSize().background(Color(0xFF2E7D6B)))
    }
}
