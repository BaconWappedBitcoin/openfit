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
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.horizontalScroll
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
    /** Saves the workout with its video: a YouTube id or a video file's content URI (one or neither). */
    onSave: (WorkoutProgram, String?, String?) -> Unit,
    onBack: () -> Unit,
    initialVideoFile: String? = null,
) {
    val units = LocalUnits.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var youtube by remember { mutableStateOf(if (initialVideoFile == null) videoId else null) }
    var videoFile by remember { mutableStateOf(initialVideoFile) }
    // A video from the device: kept with a lasting read permission, so the workout can play it later.
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            videoFile = uri.toString()
            youtube = null
        }
    }
    var zoom by remember { mutableIntStateOf(1) }
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
            com.ofit.app.ui.BackButton(onClick = onBack)
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
            Button(onClick = { onSave(WorkoutProgram(name.trim().ifBlank { "My workout" }, steps.toList()), youtube, videoFile) }) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Video, with play / pause and time.
            Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The video takes the room left above its controls, so Play and the file button stay on screen.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val file = videoFile
                    val yt = youtube
                    if (file != null) {
                        val position by rememberUpdatedState(positionMillis)
                        LocalVideoPlayer(
                            uri = android.net.Uri.parse(file),
                            playing = playing,
                            positionMillis = { position },
                            seekRequest = seekRequest,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (yt != null) {
                        val position by rememberUpdatedState(positionMillis)
                        YouTubePlayer(
                            videoId = yt,
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
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("video/*")) }) {
                        Text(if (videoFile != null) "Change video file" else "Video file…")
                    }
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
                        "Tap the timeline to move the playhead. Drag a step up or down to change it. Drag a grip ‹ › under the chart " +
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
            Spacer(Modifier.weight(1f))
            // Zoom: long workouts with many short steps need a closer look. The view follows the playhead.
            Text("Zoom", style = MaterialTheme.typography.labelLarge)
            SquareButton("−") { zoom = (zoom / 2).coerceAtLeast(1) }
            Text("${zoom}×", fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
            SquareButton("+") { zoom = (zoom * 2).coerceAtMost(16) }
        }

        // The whole workout, small: tap or drag along it to jump anywhere when zoomed in.
        if (zoom > 1) {
            Overview(
                steps = steps,
                positionSeconds = positionMillis / 1000.0,
                zoom = zoom,
                onSeek = { seconds -> seekTo(seconds) },
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
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
            zoom = zoom,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (zoom > 1) 212.dp else 240.dp)
                .systemGestureExclusion(),
        )
    }
}

/** The whole workout in one thin strip, with the zoomed window and the playhead marked. */
@Composable
private fun Overview(
    steps: List<WorkoutStep>,
    positionSeconds: Double,
    zoom: Int,
    onSeek: (Double) -> Unit,
    modifier: Modifier,
) {
    val total = steps.sumOf { it.durationSeconds }.coerceAtLeast(1)
    val speedMax = steps.maxOfOrNull { it.speedKph }?.coerceAtLeast(1.0) ?: 1.0
    val currentTotal by rememberUpdatedState(total)
    Canvas(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF15151B))
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek(currentTotal * (offset.x / size.width).toDouble()) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onSeek(currentTotal * (change.position.x / size.width).coerceIn(0f, 1f).toDouble())
                    change.consume()
                }
            },
    ) {
        val w = size.width
        val h = size.height
        var acc = 0
        steps.forEach { st ->
            val x0 = w * acc / total; acc += st.durationSeconds; val x1 = w * acc / total
            val top = h - h * (st.speedKph / speedMax).toFloat() * 0.9f
            drawRect(SpeedBlue.copy(alpha = 0.45f), Offset(x0, top), Size(x1 - x0, h - top))
        }
        // The part shown in the timeline below, centred on the playhead like the timeline is.
        val windowW = w / zoom
        val px = (w * positionSeconds / total).toFloat()
        val left = (px - windowW / 2).coerceIn(0f, w - windowW)
        drawRect(Color(0x33FFFFFF), Offset(left, 0f), Size(windowW, h))
        drawRect(Color.White, Offset(left, 0f), Size(windowW, h), style = Stroke(1.5.dp.toPx()))
        drawLine(Color(0xFFF43F5E), Offset(px, 0f), Offset(px, h), strokeWidth = 2.dp.toPx())
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
    zoom: Int = 1,
    modifier: Modifier,
) {
    val total = steps.sumOf { it.durationSeconds }.coerceAtLeast(1)
    val currentTotal by rememberUpdatedState(total)
    val speedMax = limits.effectiveMaxSpeedKph
    val inclineMax = limits.effectiveMaxInclinePercent.coerceAtLeast(1.0)
    // Downhill too, when the treadmill (or the workout) goes below 0 %.
    val inclineMin = minOf(limits.machineMinInclinePercent ?: 0.0, steps.minOfOrNull { it.inclinePercent } ?: 0.0, 0.0)
    val grab = with(LocalDensity.current) { 24.dp.toPx() }
    // A strip along the bottom holds the step-edge grips (drag left/right); the lines sit above it,
    // so dragging a level to 0 % never reaches the screen's bottom edge (the taskbar gesture).
    val gripBand = with(LocalDensity.current) { 34.dp.toPx() }
    val currentSteps by rememberUpdatedState(steps)
    val currentLane by rememberUpdatedState(lane)

    // Zoomed in, the timeline is wider than the screen; it scrolls (by the app, not the finger, so a
    // drag still edits) to keep the playhead in the middle.
    val scroll = androidx.compose.foundation.rememberScrollState()
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF15151B))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp)),
    ) {
        val viewport = maxWidth
        val density = LocalDensity.current
        LaunchedEffect(zoom, positionSeconds, total) {
            val contentPx = with(density) { (viewport * zoom).toPx() }
            val viewPx = with(density) { viewport.toPx() }
            val target = (contentPx * positionSeconds / total - viewPx / 2).toInt()
            scroll.scrollTo(target.coerceIn(0, scroll.maxValue.coerceAtLeast(0)))
        }
        Canvas(
            Modifier
                .fillMaxHeight()
                .horizontalScroll(scroll, enabled = false)
                .width(viewport * zoom)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset -> onTapAt(currentTotal * (offset.x / size.width).toDouble()) }
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
                                // Only a grip in the bottom strip moves a step's edge, so a drag
                                // anywhere else always edits the step's level.
                                if (abs(size.width * acc / t.toFloat() - start.x) < grab &&
                                    start.y > size.height - gripBand - 8f
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
                                val chart = size.height - gripBand
                                val share = (1f - change.position.y / chart).coerceIn(0f, 1f)
                                val value = if (currentLane == Lane.SPEED) speedMax * share else inclineMin + (inclineMax - inclineMin) * share
                                onLevel(stepIndex, value)
                            }
                            change.consume()
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height - gripBand
            fun x(sec: Int) = w * sec / total.toFloat()
            fun ySpeed(kph: Double) = (h - h * (kph / speedMax)).toFloat()
            fun yIncline(p: Double) = (h - h * ((p - inclineMin) / (inclineMax - inclineMin))).toFloat()

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
                drawLine(Color(0x55FFFFFF), Offset(bx, 0f), Offset(bx, h), strokeWidth = 1.5.dp.toPx())
                // A grip below the chart that reads as "drag sideways": a pill with ‹ › marks.
                val gy = h + gripBand / 2
                val gw = 11.dp.toPx()
                val gh = 9.dp.toPx()
                drawRoundRect(
                    Color(0xFFE5E7EB),
                    topLeft = Offset(bx - gw, gy - gh),
                    size = Size(gw * 2, gh * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(gh, gh),
                )
                val a = 3.5.dp.toPx()
                val dark = Color(0xFF15151B)
                val sw = 1.6.dp.toPx()
                drawLine(dark, Offset(bx - 3 * a / 2, gy), Offset(bx - a / 2, gy - a), sw)
                drawLine(dark, Offset(bx - 3 * a / 2, gy), Offset(bx - a / 2, gy + a), sw)
                drawLine(dark, Offset(bx + 3 * a / 2, gy), Offset(bx + a / 2, gy - a), sw)
                drawLine(dark, Offset(bx + 3 * a / 2, gy), Offset(bx + a / 2, gy + a), sw)
            }
            // The grip strip's floor line.
            drawLine(Color(0x22FFFFFF), Offset(0f, h), Offset(w, h))

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
