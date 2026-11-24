package com.ofit.app.history

import com.ofit.app.ui.MenuButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
) {
    val units = LocalUnits.current
    var deleting by remember { mutableStateOf<HistoryEntry?>(null) }

    val weekStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val thisWeek = HistoryTotals.of(entries.filter { it.startedAtMillis >= weekStart })
    val allTime = HistoryTotals.of(entries)

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MenuButton()
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TotalsCard("This week", thisWeek, units, Modifier.weight(1f))
            TotalsCard("All time", allTime, units, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            Text(
                "No workouts yet. Every workout that runs for at least a minute is kept here.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.startedAtMillis }) { entry ->
                EntryCard(entry, units, onDelete = { deleting = entry })
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
private fun EntryCard(entry: HistoryEntry, units: UnitSystem, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (entry.outcome != "COMPLETED") {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (entry.outcome == "FAILED") "ended early (link lost)" else "ended early",
                            color = Color(0xFFF59E0B),
                            fontSize = 12.sp,
                        )
                    }
                }
                Text(dateText(entry.startedAtMillis), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Stat(hoursMinutes(entry.durationSeconds), "time")
                Stat(units.distanceValueText(entry.distanceMeters), units.distanceUnitLong)
                Stat(units.paceText(entry.averageSpeedKph), "avg pace${units.paceUnit}")
                Stat(entry.calories?.let { "%.0f".format(it) } ?: "--", "cals")
                Stat(verticalText(entry.verticalGainMeters, units), if (units == UnitSystem.IMPERIAL) "ft" else "m")
                Stat(
                    entry.averageHeartRate?.toString() ?: "--",
                    "avg bpm" + (entry.maxHeartRate?.let { " · max $it" } ?: ""),
                )
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

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
