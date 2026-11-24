package com.ofit.app.workout

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.foundation.border
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
fun QuickStartScreen(
    limits: SafetyLimits,
    onGo: (WorkoutProgram) -> Unit,
    onBack: () -> Unit,
    body: com.ofit.protocol.BodyProfile = com.ofit.protocol.BodyProfile(),
) {
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
        com.ofit.app.ui.ScreenHeader("Quick start", "Pick a pace and go", onBack = onBack)

        // One-tap presets: picture tiles that set speed and incline.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickStart.PRESETS.forEachIndexed { i, preset ->
                val presetSpeed = units.speedFromKph(preset.speedKph).toHalf()
                val presetIncline = preset.inclinePercent.coerceIn(minIncline, maxIncline)
                val on = kotlin.math.abs(speedShown - presetSpeed) < 0.01 && kotlin.math.abs(incline - presetIncline) < 0.01
                PresetTile(
                    preset = preset,
                    speedText = "%.1f %s".format(presetSpeed, units.speedUnit) + (if (presetIncline > 0) " · %.0f %%".format(presetIncline) else ""),
                    colors = PRESET_COLORS[i % PRESET_COLORS.size],
                    icon = PRESET_ICONS[i % PRESET_ICONS.size],
                    selected = on,
                    onClick = {
                        speedShown = presetSpeed
                        incline = presetIncline
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // The workout as it will run: its shape, what it adds up to, and the white Go pill.
        val total = program.totalDurationSeconds
        val estimate = QuickStart.estimate(program, body)
        val accent = MaterialTheme.colorScheme.primary
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1A1A1A)),
        ) {
            com.ofit.app.browse.WorkoutArt(null, program.steps, accent, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            0f to Color(0xF00D0D0D), 0.55f to Color(0x990D0D0D), 1f to Color(0x000D0D0D),
                        ),
                    ),
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("YOUR WORKOUT", style = MaterialTheme.typography.labelLarge, color = accent, letterSpacing = 2.sp)
                    Text(program.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        BigFigure("${total / 60}", "min")
                        BigFigure("%.1f".format(units.distanceFromMeters(estimate.meters)), units.distanceUnit)
                        BigFigure("${estimate.kcal.roundToInt()}", "cals (est)")
                        BigFigure("${program.steps.size}", if (program.steps.size == 1) "step" else "steps")
                    }
                }
                Row(
                    Modifier
                        .height(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFFF5F7F8))
                        .clickable { onGo(program) }
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF0D0D0D),
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open workout", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D0D0D))
                }
            }
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

private val PRESET_COLORS = listOf(
    Color(0xFF2E7D6B) to Color(0xFF123B33),
    Color(0xFF3A6FB0) to Color(0xFF16294A),
    Color(0xFF9A6B2F) to Color(0xFF3D2810),
    Color(0xFF7B4FB5) to Color(0xFF2C1A47),
    Color(0xFFB5475A) to Color(0xFF461821),
)

private val PRESET_ICONS = listOf(
    androidx.compose.material.icons.Icons.AutoMirrored.Filled.DirectionsWalk,
    androidx.compose.material.icons.Icons.AutoMirrored.Filled.DirectionsWalk,
    androidx.compose.material.icons.Icons.Filled.Terrain,
    androidx.compose.material.icons.Icons.AutoMirrored.Filled.DirectionsRun,
    androidx.compose.material.icons.Icons.Filled.Bolt,
)

@Composable
private fun PresetTile(
    preset: QuickStart.Preset,
    speedText: String,
    colors: Pair<Color, Color>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .height(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(colors.first, colors.second)))
            .then(
                if (selected) Modifier.border(3.dp, Color.White, RoundedCornerShape(20.dp))
                else Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp)),
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        Column {
            Text(preset.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(speedText, fontSize = 14.sp, color = Color(0xD9FFFFFF))
        }
    }
}

@Composable
private fun BigFigure(value: String, label: String) {
    Column {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 14.sp, color = Color(0xB3FFFFFF))
    }
}
