package com.ofit.app.history

import androidx.compose.material.icons.filled.Replay

import androidx.compose.foundation.layout.aspectRatio

import com.ofit.app.ui.MenuButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.settings.LocalUnits
import com.ofit.protocol.HistoryEntry
import com.ofit.protocol.HistoryTotals
import com.ofit.protocol.UnitSystem
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Past workouts: this week's and all-time totals, then every workout, newest first. */
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onDelete: (HistoryEntry) -> Unit,
    onBack: () -> Unit,
    /** Saved workouts, to show each entry's picture and to run it again. */
    workouts: List<com.ofit.app.data.SavedWorkout> = emptyList(),
    youtubeIdFor: (String) -> String? = { null },
    onRepeat: (com.ofit.app.data.SavedWorkout) -> Unit = {},
    /** Rate a workout from its detail (P15). */
    onRate: (HistoryEntry, Int?, String?) -> Unit = { _, _, _ -> },
) {
    fun workoutOf(entry: HistoryEntry) = workouts.firstOrNull { it.program.name == entry.name }
    val units = LocalUnits.current
    var deleting by remember { mutableStateOf<HistoryEntry?>(null) }
    var opened by remember { mutableStateOf<HistoryEntry?>(null) }
    opened?.let { entry ->
        WorkoutDetail(
            entry,
            units,
            onClose = { opened = null },
            onRepeat = workoutOf(entry)?.let { saved -> { opened = null; onRepeat(saved) } },
            onRate = { stars, feel -> onRate(entry, stars, feel) },
        )
    }

    val weekStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val thisWeek = HistoryTotals.of(entries.filter { it.startedAtMillis >= weekStart })
    val allTime = HistoryTotals.of(entries)

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        com.ofit.app.ui.ScreenHeader("History", "Every workout you've finished, and your bests", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TotalsCard("This week", thisWeek, units, Modifier.weight(1f))
            TotalsCard("All time", allTime, units, Modifier.weight(1f))
        }
        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val bests = remember(entries) { com.ofit.protocol.PersonalBests.of(entries) }
            BestsCard(bests, units, onOpen = { opened = it })
            Spacer(Modifier.height(12.dp))
            AchievementsCard(entries, bests.bestWeekStreak, units)
        }
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            Text(
                "No workouts yet. Every workout that runs for at least a minute is kept here.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        // Search and filters (user request): name search, what to show, and the order.
        var query by remember { mutableStateOf("") }
        var show by remember { mutableStateOf(com.ofit.protocol.HistoryFilter.Show.ALL) }
        var sort by remember { mutableStateOf(com.ofit.protocol.HistoryFilter.Sort.NEWEST) }
        if (entries.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search your workouts") },
                    singleLine = true,
                    leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Search, contentDescription = null) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.width(280.dp),
                )
                com.ofit.protocol.HistoryFilter.Show.entries.forEach { option ->
                    androidx.compose.material3.FilterChip(selected = show == option, onClick = { show = option }, label = { Text(option.label) })
                }
                Spacer(Modifier.weight(1f))
                Text("Sort", style = MaterialTheme.typography.labelLarge)
                com.ofit.protocol.HistoryFilter.Sort.entries.forEach { option ->
                    androidx.compose.material3.FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val shown = remember(entries, query, show, sort) { com.ofit.protocol.HistoryFilter.apply(entries, query, show, sort) }
        if (entries.isNotEmpty() && shown.isEmpty()) {
            Text("No workouts match.", style = MaterialTheme.typography.bodyLarge, color = Color(0x99FFFFFF))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.startedAtMillis }) { entry ->
                val saved = workoutOf(entry)
                EntryCard(
                    entry,
                    units,
                    onDelete = { deleting = entry },
                    onOpen = { opened = entry },
                    videoId = saved?.let { youtubeIdFor(it.id) },
                    steps = saved?.program?.steps ?: entry.samples.map { s ->
                        com.ofit.protocol.WorkoutStep(speedKph = s.speedKph, inclinePercent = s.inclinePercent, durationSeconds = com.ofit.protocol.HistoryCodec.SAMPLE_SECONDS)
                    },
                    onRepeat = saved?.let { { onRepeat(it) } },
                )
            }
        }
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this workout?") },
            text = { Text("${entry.name}, ${dateText(entry.startedAtMillis)}. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(entry); deleting = null }) { Text("Delete", color = Color(0xFFF87171)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

/** Personal bests (tap one to open that workout) and weeks in a row. */
@Composable
private fun BestsCard(bests: com.ofit.protocol.PersonalBests, units: UnitSystem, onOpen: (HistoryEntry) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Personal bests", style = MaterialTheme.typography.titleMedium)
                Text("Tap one to see that workout", style = MaterialTheme.typography.bodySmall, color = Color(0x99FFFFFF))
            }
            bests.longest?.let { e -> Best(hoursMinutes(e.durationSeconds), "longest") { onOpen(e) } }
            bests.farthest?.let { e -> Best(units.distanceValueText(e.distanceMeters), "farthest (${units.distanceUnitLong})") { onOpen(e) } }
            bests.mostClimb?.let { e ->
                Best(verticalText(e.verticalGainMeters, units), if (units == UnitSystem.IMPERIAL) "most climb (ft)" else "most climb (m)") { onOpen(e) }
            }
            bests.mostCalories?.let { e -> Best("%.0f".format(e.calories ?: 0.0), "most cals (est)") { onOpen(e) } }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${bests.weekStreak} " + if (bests.weekStreak == 1) "week" else "weeks",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("in a row · best ${bests.bestWeekStreak}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Best(value: String, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TotalsCard(title: String, totals: HistoryTotals, units: UnitSystem, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(totals.workouts.toString(), if (totals.workouts == 1) "workout" else "workouts")
                Stat(hoursMinutes(totals.durationSeconds), "time")
                Stat(units.distanceValueText(totals.distanceMeters), units.distanceUnitLong)
                Stat("%.0f".format(totals.calories), "cals (est)")
                Stat(verticalText(totals.verticalGainMeters, units), if (units == UnitSystem.IMPERIAL) "ft climbed" else "m climbed")
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: HistoryEntry,
    units: UnitSystem,
    onDelete: () -> Unit,
    onOpen: () -> Unit = {},
    videoId: String? = null,
    steps: List<com.ofit.protocol.WorkoutStep> = emptyList(),
    onRepeat: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The workout's picture: tap it (or the row) for the charts.
            androidx.compose.foundation.layout.Box(
                Modifier
                    .width(176.dp)
                    .aspectRatio(16f / 9f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
            ) {
                com.ofit.app.browse.WorkoutArt(videoId, steps, MaterialTheme.colorScheme.primary, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                // Name on its own line; the tags go beside the date, so a long name never squeezes them.
                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(dateText(entry.startedAtMillis), style = MaterialTheme.typography.bodySmall)
                // Tags on their own line, one line, never squeezed.
                val tags = listOfNotNull(
                    if (entry.noTreadmill) "no treadmill" else null,
                    if (entry.outcome == "FAILED") "ended early (link lost)" else if (entry.outcome != "COMPLETED") "ended early" else null,
                )
                if (tags.isNotEmpty()) {
                    Text(
                        tags.joinToString(" · "),
                        color = if (entry.outcome != "COMPLETED") Color(0xFFF59E0B) else Color(0xFF7CC4FA),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                
                entry.zoneSeconds?.let { ZoneTimes(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Stat(hoursMinutes(entry.durationSeconds), "time")
                Stat(units.distanceValueText(entry.distanceMeters), units.distanceUnitLong)
                Stat(entry.calories?.let { "%.0f".format(it) } ?: "--", "cals (est)")
                Stat(
                    entry.averageHeartRate?.toString() ?: "--",
                    "avg bpm" + (entry.maxHeartRate?.let { " · max $it" } ?: ""),
                )
                if (onRepeat != null) {
                    androidx.compose.material3.FilledTonalButton(onClick = onRepeat) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Replay, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Repeat")
                    }
                }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

/** Time in each heart-rate zone: a bar in the zone colours, then the minutes per zone. */
@Composable
private fun ZoneTimes(seconds: List<Long>) {
    val total = seconds.sum().coerceAtLeast(1)
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier
            .fillMaxWidth(0.9f)
            .height(8.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
    ) {
        seconds.forEachIndexed { zone, s ->
            if (s > 0) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .weight(s.toFloat() / total)
                        .fillMaxHeight()
                        .background(com.ofit.app.heart.zoneColor(zone.takeIf { it > 0 })),
                )
            }
        }
    }
    Text(
        seconds.withIndex().filter { it.value > 0 }.joinToString("  ") { (zone, s) ->
            (if (zone == 0) "Below Z1" else "Z$zone") + " " + minutesSeconds(s)
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun minutesSeconds(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun verticalText(meters: Double, units: UnitSystem) =
    "%.0f".format(if (units == UnitSystem.IMPERIAL) meters * 3.28084 else meters)

private fun hoursMinutes(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m ${seconds % 60}s"
}

private fun dateText(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
