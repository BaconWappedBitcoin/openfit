package com.ofit.app.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.settings.LocalUnits
import com.ofit.app.ui.MenuButton
import com.ofit.protocol.QuickStart
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.UnitSystem
import com.ofit.protocol.WorkoutProgram
import kotlin.math.roundToInt

/** Pick a speed, incline and goal and go (BACKLOG lane 7, P3). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickStartScreen(limits: SafetyLimits, onGo: (WorkoutProgram) -> Unit, onBack: () -> Unit) {
    val units = LocalUnits.current
    val imperial = units == UnitSystem.IMPERIAL
    val minKph = limits.machineMinSpeedKph ?: 0.8
    val maxKph = limits.effectiveMaxSpeedKph
    val minIncline = limits.machineMinInclinePercent ?: 0.0
    val maxIncline = limits.effectiveMaxInclinePercent

    // Shown in the user's unit, stepped by 0.5.
    var speedShown by remember { mutableStateOf(if (imperial) 3.0 else 5.0) }
    var incline by remember { mutableStateOf(1.0) }
    var goal by remember { mutableStateOf<QuickStart.Goal>(QuickStart.Goal.Time(30)) }
    var warmUp by remember { mutableStateOf(true) }
    var coolDown by remember { mutableStateOf(true) }

    val speedKph = units.speedToKph(speedShown).coerceIn(minKph, maxKph)
    val program = QuickStart.build(speedKph, incline, goal, warmUp, coolDown, limits)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MenuButton()
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text("Quick start", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stepper(
                title = "Speed",
                value = "%.1f".format(speedShown),
                unit = units.speedUnit,
                canDown = units.speedToKph(speedShown - 0.5) >= minKph - 1e-6,
                canUp = units.speedToKph(speedShown + 0.5) <= maxKph + 1e-6,
                onDown = { speedShown -= 0.5 },
                onUp = { speedShown += 0.5 },
                modifier = Modifier.weight(1f),
            )
            Stepper(
                title = "Incline",
                value = "%.1f".format(incline),
                unit = "%",
                canDown = incline - 0.5 >= minIncline - 1e-6,
                canUp = incline + 0.5 <= maxIncline + 1e-6,
                onDown = { incline -= 0.5 },
                onUp = { incline += 0.5 },
                modifier = Modifier.weight(1f),
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("GOAL", style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF))
                Spacer(Modifier.height(8.dp))
                val unitMeters = if (imperial) 1609.344 else 1000.0
                val unitLabel = if (imperial) "mi" else "km"
                val goals: List<Pair<String, QuickStart.Goal>> = listOf(
                    "Open" to QuickStart.Goal.Open,
                    "20 min" to QuickStart.Goal.Time(20),
                    "30 min" to QuickStart.Goal.Time(30),
                    "45 min" to QuickStart.Goal.Time(45),
                    "1 $unitLabel" to QuickStart.Goal.Distance(unitMeters),
                    "3 $unitLabel" to QuickStart.Goal.Distance(3 * unitMeters),
                    "5 $unitLabel" to QuickStart.Goal.Distance(5 * unitMeters),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    goals.forEach { (label, g) ->
                        val on = g == goal
                        Text(
                            label,
                            color = if (on) Color(0xFF111111) else Color.White,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary else Color(0x1AFFFFFF))
                                .clickable { goal = g }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                if (goal == QuickStart.Goal.Open) {
                    Spacer(Modifier.height(8.dp))
                    Text("Runs until you end it (stops by itself after ${QuickStart.OPEN_MINUTES} min).", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Toggle("3-minute warm-up", "Easy pace, flat", warmUp) { warmUp = it }
                Toggle("3-minute cool-down", "Easy pace, flat", coolDown) { coolDown = it }
            }
        }

        val total = program.totalDurationSeconds
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${total / 60} min in all · ${program.steps.size} step${if (program.steps.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onGo(program) }, modifier = Modifier.height(56.dp)) {
                Text("Open workout", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Stepper(
    title: String,
    value: String,
    unit: String,
    canDown: Boolean,
    canUp: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier,
) {
    Card(modifier) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(unit, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            // Square ±0.5 buttons, as on the workout screen.
            SquareStep("−", canDown, onDown)
            Spacer(Modifier.width(10.dp))
            SquareStep("+", canUp, onUp)
        }
    }
}

@Composable
private fun SquareStep(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Color(0x26FFFFFF) else Color(0x0DFFFFFF))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else Color(0x55FFFFFF))
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Round to the nearest 0.5. */
internal fun Double.toHalf(): Double = (this * 2).roundToInt() / 2.0
