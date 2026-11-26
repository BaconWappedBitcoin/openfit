package com.ofit.app.workout

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.settings.LocalUnits
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.TrailProgram
import com.ofit.protocol.WorkoutProgram

/**
 * Virtual trails (user request 2026-11-25): pick a 3D landscape, how long and how hard, the time of
 * day and the weather, and go. On a tablet it is one screen with nothing to scroll to (user
 * 2026-11-25: "I don't know to scroll down after selecting a biome"): the landscapes on the left, the
 * options on the right, Go along the bottom. Narrow screens stack the same parts and scroll.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrailsScreen(
    limits: SafetyLimits,
    onGo: (TrailProgram.Trail, WorkoutProgram) -> Unit,
    onBack: () -> Unit,
    initialKey: String? = null,
) {
    val units = LocalUnits.current
    var key by rememberSaveable { mutableStateOf(initialKey ?: TrailProgram.TRAILS.first().key) }
    var minutes by rememberSaveable { mutableStateOf(30) }
    var effortName by rememberSaveable { mutableStateOf(TrailProgram.Effort.BRISK.name) }
    val trail = TrailProgram.trail(key)
    val effort = TrailProgram.Effort.valueOf(effortName)
    val program = remember(key, minutes, effortName) { TrailProgram.program(trail, minutes, effort) }
    val context = LocalContext.current
    var look by remember { mutableStateOf(WorldLook.choices(context)) }
    val setLook: (WorldLook) -> Unit = { look = it; WorldLook.save(context, it) }

    // The option groups, shared by both layouts; [tile] is the square size.
    val options: @Composable (Dp) -> Unit = { tile ->
        Group("HOW LONG") {
            listOf(20, 30, 45, 60).forEach { m -> Square("$m", "min", m == minutes, tile) { minutes = m } }
        }
        Group("HOW HARD") {
            TrailProgram.Effort.entries.forEach { e ->
                Square(e.label, "%.1f %s".format(units.speedFromKph(e.kph), units.speedUnit), e == effort, tile) { effortName = e.name }
            }
        }
        Group("TIME OF DAY") {
            (listOf(WorldLook.SURPRISE to "Surprise") + WorldLook.TIMES).forEach { (k, label) ->
                Square(label, if (k == WorldLook.SURPRISE) "any" else "", look.time == k, tile) { setLook(look.copy(time = k)) }
            }
        }
        Group("WEATHER") {
            (listOf(WorldLook.SURPRISE to "Surprise") + WorldLook.WEATHERS).forEach { (k, label) ->
                Square(label, if (k == WorldLook.SURPRISE) "any" else "", look.weather == k, tile) { setLook(look.copy(weather = k)) }
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.ofit.app.ui.ScreenHeader("Virtual trails", "Run through a 3D landscape, no video needed", onBack = onBack)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The landscapes: four to a row, as many rows as fit.
                    Column(Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TrailProgram.TRAILS.chunked(4).forEach { row ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { t ->
                                    TrailCard(t, selected = t.key == key, onClick = { key = t.key }, modifier = Modifier.weight(1f), compact = true)
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) { options(72.dp) }
                }
                GoBar(program, trail, units, limits, Modifier.fillMaxWidth().height(104.dp), onGo)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                com.ofit.app.ui.ScreenHeader("Virtual trails", "Run through a 3D landscape, no video needed", onBack = onBack)
                TrailProgram.TRAILS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { t -> TrailCard(t, selected = t.key == key, onClick = { key = t.key }, modifier = Modifier.weight(1f)) }
                        repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { options(80.dp) }
                }
                GoBar(program, trail, units, limits, Modifier.fillMaxWidth().height(150.dp), onGo)
            }
        }
    }
}

/** A labelled row of square choices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** The workout as it will run, what the landscape is, and Go. */
@Composable
private fun GoBar(
    program: WorkoutProgram,
    trail: TrailProgram.Trail,
    units: com.ofit.protocol.UnitSystem,
    limits: SafetyLimits,
    modifier: Modifier,
    onGo: (TrailProgram.Trail, WorkoutProgram) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val meters = program.steps.sumOf { it.speedKph * it.durationSeconds / 3.6 }
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF1A1A1A))) {
        com.ofit.app.browse.WorkoutArt(null, program.steps, accent, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF00D0D0D), 0.6f to Color(0x990D0D0D), 1f to Color(0x000D0D0D))))
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(program.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Text(trail.blurb, color = Color(0xDDFFFFFF), fontSize = 14.sp, maxLines = 1)
                Text(
                    "%.1f %s · hills up to %s %% · warm-up and easy finish".format(
                        units.distanceFromMeters(meters), units.distanceUnit,
                        "%.1f".format(program.steps.maxOf { it.inclinePercent }).removeSuffix(".0"),
                    ) + if (program.steps.any { it.speedKph > limits.effectiveMaxSpeedKph || it.inclinePercent > limits.effectiveMaxInclinePercent }) " · capped to your treadmill's limits" else "",
                    color = Color(0xAAFFFFFF), fontSize = 13.sp, maxLines = 1,
                )
            }
            // A square Go button (user preference: square buttons).
            Box(
                Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFF5F7F8)).clickable { onGo(trail, program) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Open the trail", tint = Color(0xFF0D0D0D), modifier = Modifier.size(34.dp))
                    Text("Go", fontWeight = FontWeight.Bold, color = Color(0xFF0D0D0D))
                }
            }
        }
    }
}

/** A square choice: a big line and a small one under it. */
@Composable
private fun Square(big: String, small: String, on: Boolean, size: Dp, onClick: () -> Unit) {
    val ink = if (on) Color(0xFF111111) else Color.White
    Column(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else Color(0x1AFFFFFF))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            big, color = ink, fontWeight = FontWeight.Bold, fontSize = if (big.length > 6) 12.sp else if (big.length > 4) 14.sp else 18.sp, maxLines = 2, lineHeight = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (small.isNotEmpty()) Text(small, color = ink.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * A landscape card: its picture (assets/world/preview/<key>.jpg) or its colours, its name and, unless
 * [compact], a line about it. A compact card fills the space it is given.
 */
@Composable
internal fun TrailCard(trail: TrailProgram.Trail, selected: Boolean, onClick: () -> Unit, modifier: Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val picture = remember(trail.key) {
        runCatching { context.assets.open("world/preview/${trail.key}.jpg").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    val colours = TRAIL_COLOURS[trail.key] ?: (Color(0xFF2E7D6B) to Color(0xFF123B33))
    Box(
        modifier
            .let { if (compact) it else it.height(150.dp) }
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(colours.first, colours.second)))
            .then(if (selected) Modifier.border(3.dp, Color.White, RoundedCornerShape(20.dp)) else Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp)))
            .clickable(onClick = onClick),
    ) {
        if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color(0x00000000), 1f to Color(0xD9000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(if (compact) 10.dp else 14.dp)) {
            Text(trail.name, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            if (!compact) Text(trail.blurb, fontSize = 13.sp, color = Color(0xDDFFFFFF), maxLines = 2)
        }
    }
}

private val TRAIL_COLOURS = mapOf(
    "forest" to (Color(0xFF4F7A3A) to Color(0xFF1F3318)),
    "pine" to (Color(0xFF35604A) to Color(0xFF142820)),
    "mountains" to (Color(0xFF7D8A99) to Color(0xFF2E3640)),
    "autumn" to (Color(0xFFB8742E) to Color(0xFF4A2A10)),
    "meadow" to (Color(0xFF8DB458) to Color(0xFF3A5220)),
    "fields" to (Color(0xFFC9A94A) to Color(0xFF4F4418)),
    "moor" to (Color(0xFF8A6A7A) to Color(0xFF3A2A34)),
    "desert" to (Color(0xFFD9A066) to Color(0xFF6A4220)),
    "coast" to (Color(0xFF4A8FB0) to Color(0xFF1A3A4A)),
    "town" to (Color(0xFF9A6A52) to Color(0xFF3A2A24)),
)
