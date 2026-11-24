package com.ofit.app.workout

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.browse.ProfileArt
import com.ofit.protocol.HistoryEntry
import com.ofit.protocol.TrainingPlans
import com.ofit.protocol.WorkoutProgram
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Training plans (P11/P12): pick one, choose days and a reminder, then follow it session by session. */
@Composable
fun PlansScreen(
    profileKey: String,
    history: List<HistoryEntry>,
    onStart: (WorkoutProgram) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var enrollment by remember(profileKey) { mutableStateOf(PlanStore.load(context, profileKey)) }
    var choosing by remember { mutableStateOf<TrainingPlans.Plan?>(null) }
    val status = enrollment?.let { TrainingPlans.status(it, history) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        com.ofit.app.ui.ScreenHeader("Training plans", "A few weeks, a session at a time", onBack = onBack)
        val picked = choosing
        when {
            picked != null -> PlanSetup(picked, onCancel = { choosing = null }) { days, reminder ->
                val e = TrainingPlans.Enrollment(picked.id, System.currentTimeMillis(), days, reminder)
                PlanStore.save(context, profileKey, e)
                enrollment = e
                choosing = null
            }
            status != null -> FollowedPlan(
                status,
                enrollment!!,
                onStart = onStart,
                onChange = { e -> PlanStore.save(context, profileKey, e); enrollment = e },
                onLeave = { PlanStore.clear(context, profileKey); enrollment = null },
            )
            else -> {
                Text("Pick a plan. OpenFit counts each finished session, so you always know what's next.", color = Color(0xCCFFFFFF))
                TrainingPlans.ALL.forEach { plan -> PlanCard(plan) { choosing = plan } }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: TrainingPlans.Plan, onPick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onPick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // The busiest session draws the plan best (intervals rather than a flat block).
        val sessions = remember(plan) { List(plan.totalSessions) { plan.session(it) } }
        ProfileArt(sessions.maxBy { it.steps.size }.steps, accent, Modifier.width(220.dp).height(110.dp).clip(RoundedCornerShape(16.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(plan.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(plan.detail, color = Color(0xCCFFFFFF))
            val shortest = sessions.minOf { it.totalDurationSeconds }
            val longest = sessions.maxOf { it.totalDurationSeconds }
            fun mins(s: Int) = (s + 30) / 60
            fun span(s: Int) = if (mins(s) < 90) "${mins(s)} min" else "%d h %02d".format(mins(s) / 60, mins(s) % 60)
            val length = if (mins(longest) - mins(shortest) <= 10) "about ${span(longest)} each" else "${span(shortest)} to ${span(longest)}"
            Text("${plan.weeks} weeks · ${plan.sessionsPerWeek} sessions a week · $length", color = Color(0x99FFFFFF))
        }
        Button(onClick = onPick, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("Choose") }
    }
}

private val WEEK = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

private fun clockText(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
}

@Composable
private fun DayChips(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WEEK.forEach { d ->
            val on = d in days
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) accent else Color(0x14FFFFFF))
                    .clickable { onToggle(d) },
                contentAlignment = Alignment.Center,
            ) {
                Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontWeight = FontWeight.Bold, color = if (on) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun ReminderRow(minute: Int?, onChange: (Int?) -> Unit) {
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Remind me on training days", style = MaterialTheme.typography.titleMedium)
            Text(
                if (minute == null) "No reminder" else "At ${clockText(minute)}, only if the session is still to do",
                color = Color(0x99FFFFFF),
            )
        }
        if (minute != null) {
            OutlinedButton(onClick = { onChange((minute - 30).mod(24 * 60)) }, modifier = Modifier.size(72.dp), shape = RoundedCornerShape(18.dp)) { Text("−", fontSize = 28.sp) }
            OutlinedButton(onClick = { onChange((minute + 30).mod(24 * 60)) }, modifier = Modifier.size(72.dp), shape = RoundedCornerShape(18.dp)) { Text("+", fontSize = 28.sp) }
        }
        Switch(checked = minute != null, onCheckedChange = { on ->
            if (on && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            onChange(if (on) 18 * 60 else null)
        })
    }
}

@Composable
private fun PlanSetup(plan: TrainingPlans.Plan, onCancel: () -> Unit, onConfirm: (Set<DayOfWeek>, Int?) -> Unit) {
    var days by remember(plan) { mutableStateOf(plan.suggestedDays) }
    var reminder by remember(plan) { mutableStateOf<Int?>(18 * 60) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(plan.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(plan.detail, color = Color(0xCCFFFFFF))
        Text("Which days? (${plan.sessionsPerWeek} a week suggested)", style = MaterialTheme.typography.titleMedium)
        DayChips(days) { d -> days = if (d in days) days - d else days + d }
        ReminderRow(reminder) { reminder = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                // The reminder is on by default, so ask for notifications here (Android 13+).
                if (reminder != null && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                onConfirm(days, reminder)
            }, enabled = days.isNotEmpty()) { Text("Start the plan") }
            TextButton(onClick = onCancel) { Text("Back") }
        }
    }
}

@Composable
private fun FollowedPlan(
    status: TrainingPlans.Status,
    enrollment: TrainingPlans.Enrollment,
    onStart: (WorkoutProgram) -> Unit,
    onChange: (TrainingPlans.Enrollment) -> Unit,
    onLeave: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val plan = status.plan
    var confirmLeave by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.copy(alpha = 0.35f), Color(0xFF1A1A1A))))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("YOUR PLAN", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF))
        Text(plan.name, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(
            if (status.finished) "All ${plan.totalSessions} sessions done. Well done!"
            else "Week ${status.week} of ${plan.weeks} · ${status.done} of ${plan.totalSessions} sessions done",
            color = Color(0xCCFFFFFF),
        )
        LinearProgressIndicator(
            progress = { status.progress },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = if (status.finished) Color(0xFF34D399) else accent,
            trackColor = Color(0x22FFFFFF),
        )
        // One row of dots per week: filled when done, ringed for the next session.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(plan.weeks) { w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Week ${w + 1}", Modifier.width(72.dp), color = Color(0x99FFFFFF), fontSize = 13.sp)
                    repeat(plan.sessionsPerWeek) { s ->
                        val i = w * plan.sessionsPerWeek + s
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (i < status.done) accent else Color(0x18FFFFFF))
                                .then(if (i == status.done) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i < status.done) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.Black)
                        }
                    }
                }
            }
        }
        status.next?.let { next ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileArt(next.steps, accent, Modifier.width(240.dp).height(96.dp).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            status.dueToday -> "TODAY"
                            status.doneToday -> "DONE FOR TODAY · NEXT UP"
                            else -> "NEXT UP"
                        },
                        style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF),
                    )
                    Text(next.name.substringAfter(" · "), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${next.totalDurationSeconds / 60} min", color = Color(0xCCFFFFFF))
                }
                Button(
                    onClick = { onStart(next) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Training days", style = MaterialTheme.typography.titleMedium)
        DayChips(enrollment.days) { d ->
            val days = if (d in enrollment.days) enrollment.days - d else enrollment.days + d
            if (days.isNotEmpty()) onChange(enrollment.copy(days = days))
        }
        ReminderRow(enrollment.reminderMinuteOfDay) { onChange(enrollment.copy(reminderMinuteOfDay = it)) }
        if (confirmLeave) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Leave ${plan.name}? Your workouts stay in History.", Modifier.weight(1f))
                Button(onClick = onLeave) { Text("Leave plan") }
                TextButton(onClick = { confirmLeave = false }) { Text("Keep it") }
            }
        } else {
            TextButton(onClick = { confirmLeave = true }) { Text(if (status.finished) "Pick another plan" else "Leave this plan") }
        }
    }
}
