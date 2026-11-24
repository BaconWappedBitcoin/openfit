package com.ofit.app.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.settings.LocalUnits
import com.ofit.app.ui.MenuButton
import com.ofit.protocol.ProgramEdit
import com.ofit.protocol.ProgramEdit.Lane
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.WorkoutProgram
import com.ofit.protocol.WorkoutStep
import kotlinx.coroutines.delay
import kotlin.math.abs

private val SpeedBlue = Color(0xFF38BDF8)
private val InclineOrange = Color(0xFFFB923C)

/**
 * Visual editor: the video on top, the workout as a timeline underneath. Tap the timeline to move the
 * playhead (the video follows), drag a step up or down to set its speed or incline, drag the line
 * between two steps to change where one ends, split at the playhead, delete a step. No AI needed: this
 * is how a runner authors a workout for any video by hand.
 */
@Composable
fun VisualEditorScreen(
    initialName: String,
    initialSteps: List<WorkoutStep>,
    videoId: String?,
    limits: SafetyLimits,
    onSave: (WorkoutProgram) -> Unit,
    onBack: () -> Unit,
) {
    val units = LocalUnits.current
    var name by remember { mutableStateOf(initialName) }
    val steps = remember { mutableStateListOf<WorkoutStep>().also { it.addAll(initialSteps) } }
    val undo = remember { mutableStateListOf<List<WorkoutStep>>() }
    var lane by remember { mutableStateOf(Lane.SPEED) }
    var selected by remember { mutableIntStateOf(0) }
    var positionMillis by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var seekRequest by remember { mutableIntStateOf(0) }

    val total = steps.sumOf { it.durationSeconds }.coerceAtLeast(1)
    fun commit(next: List<WorkoutStep>) {
        if (next == steps.toList()) return
        undo.add(steps.toList())
        if (undo.size > 50) undo.removeAt(0)
        steps.clear(); steps.addAll(next)
        selected = selected.coerceIn(0, steps.size - 1)
    }
    fun seekTo(seconds: Double) {
        positionMillis = (seconds.coerceIn(0.0, total.toDouble()) * 1000).toInt()
        seekRequest++
        selected = ProgramEdit.stepAt(steps, seconds)
    }

    // The editor owns the clock; the video follows it.
    LaunchedEffect(playing) {
        var last = System.currentTimeMillis()
        while (playing) {
            delay(100)
            val now = System.currentTimeMillis()
            positionMillis = (positionMillis + (now - last).toInt()).coerceAtMost(total * 1000)
            last = now
            selected = ProgramEdit.stepAt(steps, positionMillis / 1000.0)
            if (positionMillis >= total * 1000) playing = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: name and save.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuButton()
            OutlinedButton(onClick = onBack) { Text("Back") }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                label = { Text("Workout name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { undo.removeLastOrNull()?.let { steps.clear(); steps.addAll(it) } }, enabled = undo.isNotEmpty()) {
                Text("Undo")
            }
            Button(onClick = { onSave(WorkoutProgram(name.trim().ifBlank { "My workout" }, steps.toList())) }) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Video, with play / pause and time.
            Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (videoId != null) {
                        val position by rememberUpdatedState(positionMillis)
                        YouTubePlayer(
                            videoId = videoId,
                            playing = playing,
                            positionMillis = { position },
                            seekRequest = seekRequest,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("No video: build the workout on the timeline", color = Color(0x99FFFFFF))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
                    OutlinedButton(onClick = { seekTo(positionMillis / 1000.0 - 10) }) { Text("−10 s") }
                    OutlinedButton(onClick = { seekTo(positionMillis / 1000.0 + 10) }) { Text("+10 s") }
                    Text("${formatClock(positionMillis / 1000)} / ${formatClock(total)}", fontSize = 18.sp)
                }
            }

            // The selected step.
            val step = steps.getOrNull(selected)
            Card(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (step != null) {
                        val start = ProgramEdit.startOf(steps, selected)
                        Text("Step ${selected + 1} of ${steps.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${formatClock(start)} – ${formatClock(start + step.durationSeconds)}  (${formatClock(step.durationSeconds)})")
                        LevelRow(
                            label = "Speed",
                            value = "%.1f %s".format(units.speedFromKph(step.speedKph), units.speedUnit),
                            color = SpeedBlue,
                            onMinus = { commit(ProgramEdit.setLevel(steps, selected, Lane.SPEED, units.speedToKph(units.speedFromKph(step.speedKph) - 0.1), limits)) },
                            onPlus = { commit(ProgramEdit.setLevel(steps, selected, Lane.SPEED, units.speedToKph(units.speedFromKph(step.speedKph) + 0.1), limits)) },
                        )
                        LevelRow(
                            label = "Incline",
                            value = "%.1f %%".format(step.inclinePercent),
                            color = InclineOrange,
                            onMinus = { commit(ProgramEdit.setLevel(steps, selected, Lane.INCLINE, step.inclinePercent - 0.5, limits)) },
                            onPlus = { commit(ProgramEdit.setLevel(steps, selected, Lane.INCLINE, step.inclinePercent + 0.5, limits)) },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { commit(ProgramEdit.split(steps, positionMillis / 1000)) }) { Text("Split at playhead") }
                            OutlinedButton(onClick = { commit(ProgramEdit.delete(steps, selected)) }, enabled = steps.size > 1) { Text("Delete step") }
                        }
                    }
                    Text(
                        "Tap the timeline to move the playhead. Drag a step up or down to change it. Drag a round handle " +
                            "left or right to move where one step ends and the next begins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0x99FFFFFF),
                    )
                }
            }
        }

        // Which line dragging edits.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Drag edits:", style = MaterialTheme.typography.labelLarge)
            LaneChip("Speed", SpeedBlue, lane == Lane.SPEED) { lane = Lane.SPEED }
            LaneChip("Incline", InclineOrange, lane == Lane.INCLINE) { lane = Lane.INCLINE }
        }

        Timeline(
            steps = steps,
            selected = selected,
            lane = lane,
            positionSeconds = positionMillis / 1000.0,
            limits = limits,
            onTapAt = { seconds -> seekTo(seconds) },
            onDragStart = { undo.add(steps.toList()) },
            onLevel = { index, value ->
                selected = index
                val next = ProgramEdit.setLevel(steps, index, lane, value, limits)
                steps.clear(); steps.addAll(next)
            },
            onBoundary = { index, seconds ->
                val next = ProgramEdit.moveBoundary(steps, index, seconds)
                steps.clear(); steps.addAll(next)
            },
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
    }
}

@Composable
private fun Timeline(
    steps: List<WorkoutStep>,
    selected: Int,
    lane: Lane,
    positionSeconds: Double,
    limits: SafetyLimits,
    onTapAt: (Double) -> Unit,
    onDragStart: () -> Unit,
    onLevel: (Int, Double) -> Unit,
    onBoundary: (Int, Int) -> Unit,
    modifier: Modifier,
) {
    val total = steps.sumOf { it.durationSeconds }.coerceAtLeast(1)
    val speedMax = limits.effectiveMaxSpeedKph
    val inclineMax = limits.effectiveMaxInclinePercent.coerceAtLeast(1.0)
    val grab = with(LocalDensity.current) { 24.dp.toPx() }
    val currentSteps by rememberUpdatedState(steps)
    val currentLane by rememberUpdatedState(lane)

    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF15151B))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp)),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset -> onTapAt(total * (offset.x / size.width).toDouble()) }
                }
                .pointerInput(Unit) {
                    // A drag that starts on a boundary moves it; anywhere else it sets the level of
                    // the step under the finger, for the lane being edited.
                    var boundary = -1
                    var stepIndex = -1
                    detectDragGestures(
                        onDragStart = { start ->
                            val s = currentSteps
                            val t = s.sumOf { it.durationSeconds }.coerceAtLeast(1)
                            boundary = -1
                            var acc = 0
                            for (i in 1 until s.size) {
                                acc += s[i - 1].durationSeconds
                                // Only the round handle on the centre line grabs a boundary, so a
                                // drag anywhere else always edits the step's level.
                                if (abs(size.width * acc / t.toFloat() - start.x) < grab &&
                                    abs(start.y - size.height / 2f) < grab
                                ) { boundary = i; break }
                            }
                            stepIndex = com.ofit.protocol.ProgramEdit.stepAt(s, t * (start.x / size.width).toDouble())
                            onDragStart()
                        },
                        onDrag = { change, _ ->
                            val s = currentSteps
                            val t = s.sumOf { it.durationSeconds }.coerceAtLeast(1)
                            if (boundary > 0) {
                                onBoundary(boundary, (t * (change.position.x / size.width)).toInt())
                            } else if (stepIndex >= 0) {
                                val share = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                val value = if (currentLane == Lane.SPEED) speedMax * share else inclineMax * share
                                onLevel(stepIndex, value)
                            }
                            change.consume()
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            fun x(sec: Int) = w * sec / total.toFloat()
            fun ySpeed(kph: Double) = (h - h * (kph / speedMax)).toFloat()
            fun yIncline(p: Double) = (h - h * (p / inclineMax)).toFloat()

            // Grid.
            for (f in listOf(0f, 0.5f, 1f)) drawLine(Color(0x14FFFFFF), Offset(0f, h * f), Offset(w, h * f))

            // Selected step band.
            if (selected in steps.indices) {
                val s0 = steps.take(selected).sumOf { it.durationSeconds }
                drawRect(Color(0x1AFFFFFF), Offset(x(s0), 0f), Size(x(s0 + steps[selected].durationSeconds) - x(s0), h))
            }

            // The two lines; the one being edited is drawn thicker and on top.
            fun path(y: (WorkoutStep) -> Float): Path {
                val p = Path()
                var acc = 0
                steps.forEachIndexed { i, st ->
                    val x0 = x(acc); acc += st.durationSeconds; val x1 = x(acc)
                    if (i == 0) p.moveTo(x0, y(st)) else p.lineTo(x0, y(st))
                    p.lineTo(x1, y(st))
                }
                return p
            }
            val speedPath = path { ySpeed(it.speedKph) }
            val inclinePath = path { yIncline(it.inclinePercent) }
            if (lane == Lane.SPEED) {
                drawPath(inclinePath, InclineOrange.copy(alpha = 0.5f), style = Stroke(2.dp.toPx()))
                drawPath(speedPath, SpeedBlue, style = Stroke(4.dp.toPx()))
            } else {
                drawPath(speedPath, SpeedBlue.copy(alpha = 0.5f), style = Stroke(2.dp.toPx()))
                drawPath(inclinePath, InclineOrange, style = Stroke(4.dp.toPx()))
            }

            // Boundaries: grabbable handles.
            var acc = 0
            for (i in 0 until steps.size - 1) {
                acc += steps[i].durationSeconds
                val bx = x(acc)
                drawLine(Color(0x88FFFFFF), Offset(bx, 0f), Offset(bx, h), strokeWidth = 2.dp.toPx())
                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(bx, h / 2))
            }

            // Playhead.
            val px = (w * positionSeconds / total).toFloat()
            drawLine(Color(0xFFF43F5E), Offset(px, 0f), Offset(px, h), strokeWidth = 3.dp.toPx())
        }
    }
}

@Composable
private fun LevelRow(label: String, value: String, color: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        SquareButton("−", onMinus)
        Spacer(Modifier.width(8.dp))
        SquareButton("+", onPlus)
    }
}

@Composable
private fun SquareButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x26FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun LaneChip(label: String, color: Color, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Color(0xFF111111) else color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) color else color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
