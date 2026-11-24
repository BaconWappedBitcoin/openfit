package com.ofit.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.data.SavedWorkout
import com.ofit.app.settings.LocalUnits
import com.ofit.protocol.ConnectionState
import com.ofit.protocol.HistoryEntry
import com.ofit.protocol.HistoryTotals
import com.ofit.protocol.MachineReadiness
import com.ofit.protocol.TreadmillState
import com.ofit.protocol.UnitSystem
import java.util.Calendar

/**
 * Home as a dashboard (BACKLOG lane 2): the treadmill's state, the workout to do next, this week's
 * totals and the main places. Manual control lives on its own screen.
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
    onManual: () -> Unit,
    onQuickStart: () -> Unit = {},
) {
    val units = LocalUnits.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TreadmillCard(state, treadmillName, onConnect, onFindTreadmill, Modifier.weight(1f))
            WeekCard(history, units, onHistory, Modifier.weight(1f))
        }

        // The workout to do next: the last one run, else the newest saved.
        val last = history.firstOrNull()
        val next = last?.let { entry -> workouts.firstOrNull { it.program.name == entry.name } }
            ?: workouts.maxByOrNull { it.updatedAtMillis }
        if (next != null) {
            Card(
                Modifier.fillMaxWidth().clickable { onOpenWorkout(next) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (last != null && next.program.name == last.name) "START WHERE YOU LEFT OFF" else "READY WHEN YOU ARE",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(next.program.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        Text(
                            "${next.program.totalDurationSeconds / 60} min · ${next.program.steps.size} steps" +
                                (last?.takeIf { it.name == next.program.name }?.let { " · last run ${relativeDay(it.startedAtMillis)}" } ?: ""),
                            color = Color(0xCCFFFFFF),
                        )
                    }
                    Button(onClick = { onOpenWorkout(next) }) { Text("Open workout") }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickLink("Quick start", Icons.Filled.Bolt, onQuickStart, Modifier.weight(1f))
            QuickLink("Browse", Icons.Filled.Movie, onBrowse, Modifier.weight(1f))
            QuickLink("My workouts", Icons.AutoMirrored.Filled.List, onMyWorkouts, Modifier.weight(1f))
            QuickLink("Find a video", Icons.Filled.Search, onFindVideo, Modifier.weight(1f))
            QuickLink("History", Icons.Filled.History, onHistory, Modifier.weight(1f))
            QuickLink("Manual control", Icons.Filled.Tune, onManual, Modifier.weight(1f))
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
        name == null -> "No treadmill yet" to "Find your treadmill once; OpenFit remembers it."
        state.readiness == MachineReadiness.BUSY_LOCAL -> "In use by hand" to "OpenFit leaves it alone and sends nothing."
        connected && state.workoutMode == com.ofit.protocol.WorkoutMode.RUNNING -> "Workout running" to name
        connected -> "Connected" to "$name · lets go after a minute with no workout"
        state.connectionState == ConnectionState.CONNECTING -> "Connecting…" to name
        else -> "Not connected" to "$name · connects when you open a workout"
    }
    Card(modifier) {
        Column(Modifier.padding(20.dp)) {
            Text("TREADMILL", style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            if (name == null) {
                Button(onClick = onFind) { Text("Find my treadmill") }
            } else if (!connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onConnect) { Text("Connect now") }
                    OutlinedButton(onClick = onFind) { Text("Another treadmill") }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(history: List<HistoryEntry>, units: UnitSystem, onHistory: () -> Unit, modifier: Modifier) {
    val weekStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val week = HistoryTotals.of(history.filter { it.startedAtMillis >= weekStart })
    Card(modifier.clickable(onClick = onHistory)) {
        Column(Modifier.padding(20.dp)) {
            Text("THIS WEEK", style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Big(week.workouts.toString(), if (week.workouts == 1) "workout" else "workouts")
                Big("${week.durationSeconds / 60}", "minutes")
                Big(units.distanceValueText(week.distanceMeters), units.distanceUnitLong)
                Big("%.0f".format(week.calories), "cals")
            }
        }
    }
}

@Composable
private fun Big(value: String, label: String) {
    Column {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuickLink(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Card(modifier.clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
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
