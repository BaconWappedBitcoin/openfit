package com.ofit.app.history

import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ofit.protocol.HistoryEntry
import com.ofit.protocol.HistorySample
import com.ofit.protocol.UnitSystem

private val SpeedBlue = Color(0xFF38BDF8)
private val InclineOrange = Color(0xFFFB923C)
private val HeartRed = Color(0xFFF43F5E)

/** One finished workout in full: its numbers, and speed, incline and heart rate over time. */
@Composable
fun WorkoutDetail(
    entry: HistoryEntry,
    units: UnitSystem,
    onClose: () -> Unit,
    title: String? = null,
    onRepeat: (() -> Unit)? = null,
    /** Rate the workout (P15): stars 1-5 and how it felt; null shows the rating read-only. */
    onRate: ((stars: Int?, feel: String?) -> Unit)? = null,
    /**
     * After a workout (user request 2026-11-24): it is saved by default, and "Don't save" takes it
     * out of History again (and out of Health Connect, if it was sent).
     */
    onDiscard: (() -> Unit)? = null,
) {
    var confirmDiscard by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmDiscard && onDiscard != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Don't save this workout?") },
            text = { Text("It will be removed from History, your streaks and badges, and Health Connect if it was sent there.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Don't save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDiscard = false }) { Text("Keep it") }
            },
        )
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var exported by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    // Export: the runner picks where the file goes (Downloads, Drive…), then uploads it to Strava,
    // Garmin Connect or any site that takes TCX.
    val saver = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/vnd.garmin.tcx+xml"),
    ) { uri ->
        if (uri != null) {
            exported = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(com.ofit.protocol.TcxWriter.write(entry).toByteArray()) }
                "Saved. Upload the file to Strava, Garmin Connect or any site that takes TCX."
            }.getOrElse { "Could not save the file: ${it.message}" }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onClose) { Text(if (title != null) "Done" else "Close") }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    title?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    Text(entry.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                            .format(java.util.Date(entry.startedAtMillis)) +
                            (if (entry.noTreadmill) " · no treadmill" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0x99FFFFFF),
                    )
                }
                if (onDiscard != null) {
                    androidx.compose.material3.TextButton(onClick = { confirmDiscard = true }) { Text("Don't save") }
                    Spacer(Modifier.width(8.dp))
                }
                if (onRepeat != null) {
                    androidx.compose.material3.Button(onClick = onRepeat) { Text("Repeat workout") }
                    Spacer(Modifier.width(12.dp))
                }
                androidx.compose.material3.OutlinedButton(onClick = { saver.launch(com.ofit.protocol.TcxWriter.fileName(entry)) }) {
                    Text("Export TCX")
                }
            }
            exported?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Big("%d:%02d".format(entry.durationSeconds / 60, entry.durationSeconds % 60), "time")
                Big(units.distanceValueText(entry.distanceMeters), units.distanceUnitLong)
                Big(units.paceText(entry.averageSpeedKph), "avg pace${units.paceUnit}")
                Big(entry.calories?.let { "%.0f".format(it) } ?: "--", "cals (est)")
                Big(entry.averageHeartRate?.toString() ?: "--", "avg bpm" + (entry.maxHeartRate?.let { " · max $it" } ?: ""))
            }

            RatingCard(entry, onRate)

            val samples = entry.samples
            if (samples.size < 2) {
                Text(
                    "No charts for this workout: charts are kept for workouts from now on.",
                    color = Color(0x99FFFFFF),
                )
            } else {
                val total = (samples.last().seconds).coerceAtLeast(1)
                Chart("Speed (${units.speedUnit})", SpeedBlue, samples, total) { units.speedFromKph(it.speedKph) }
                Chart("Incline (%)", InclineOrange, samples, total) { it.inclinePercent }
                if (samples.any { it.heartRate != null }) {
                    Chart("Heart rate (bpm)", HeartRed, samples, total, fromZero = false) { it.heartRate?.toDouble() }
                }
            }
        }
    }
}

/**
 * "How was it?" after a workout (P15): stars, and whether it felt too easy, just right or too hard,
 * kept with the workout in History. Read-only when [onRate] is null and something was rated.
 */
@Composable
private fun RatingCard(entry: HistoryEntry, onRate: ((Int?, String?) -> Unit)?) {
    var stars by androidx.compose.runtime.remember(entry.startedAtMillis) { androidx.compose.runtime.mutableStateOf(entry.stars) }
    var feel by androidx.compose.runtime.remember(entry.startedAtMillis) { androidx.compose.runtime.mutableStateOf(entry.feel) }
    if (onRate == null && stars == null && feel == null) return
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column {
            Text("How was it?", style = MaterialTheme.typography.labelLarge, color = Color(0x99FFFFFF))
            Row {
                (1..5).forEach { n ->
                    val on = (stars ?: 0) >= n
                    androidx.compose.material3.Icon(
                        if (on) androidx.compose.material.icons.Icons.Filled.Star else androidx.compose.material.icons.Icons.Outlined.StarBorder,
                        contentDescription = "$n star" + if (n == 1) "" else "s",
                        tint = if (on) Color(0xFFFFC857) else Color(0x66FFFFFF),
                        modifier = Modifier
                            .size(48.dp)
                            .then(if (onRate != null) Modifier.clickable { stars = n; onRate(n, feel) } else Modifier)
                            .padding(6.dp),
                    )
                }
            }
        }
        Column {
            Text("How did it feel?", style = MaterialTheme.typography.labelLarge, color = Color(0x99FFFFFF))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HistoryEntry.FEEL_EASY to "Too easy", HistoryEntry.FEEL_RIGHT to "Just right", HistoryEntry.FEEL_HARD to "Too hard").forEach { (key, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = feel == key,
                        onClick = { if (onRate != null) { feel = key; onRate(stars, key) } },
                        enabled = onRate != null || feel == key,
                        label = { Text(label) },
                    )
                }
            }
        }
        if (onRate != null && stars == null && feel == null) {
            Text("Rate it to see how your workouts feel over time.", color = Color(0x99FFFFFF), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Big(value: String, label: String) {
    Column {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0x99FFFFFF))
    }
}

/**
 * The stretches of a chart to draw: consecutive readings, broken where a reading is missing or
 * where the clock jumps (a skip or a seek), so a gap is drawn as a gap and never as a slope from
 * zero (seen 2026-11-24: one heart-rate reading drew a ramp across the whole run).
 */
internal fun chartRuns(points: List<Pair<Int, Double?>>, maxGapSeconds: Int = 60): List<List<Pair<Int, Double>>> {
    val runs = mutableListOf<List<Pair<Int, Double>>>()
    var current = mutableListOf<Pair<Int, Double>>()
    points.forEach { (t, v) ->
        val last = current.lastOrNull()
        if (v == null || (last != null && t - last.first > maxGapSeconds)) {
            if (current.isNotEmpty()) runs += current
            current = mutableListOf()
        }
        if (v != null) current += t to v
    }
    if (current.isNotEmpty()) runs += current
    return runs
}

/** A filled line over the workout's time, with its low and high value written on the left. */
@Composable
private fun Chart(
    title: String,
    color: Color,
    samples: List<HistorySample>,
    totalSeconds: Int,
    /** Start the scale at zero (speed, incline); heart rate scales to its own range. */
    fromZero: Boolean = true,
    value: (HistorySample) -> Double?,
) {
    val values = samples.mapNotNull(value)
    if (values.isEmpty()) return
    val lo = if (fromZero) minOf(0.0, values.min()) else values.min() - 10.0
    val hi = values.max().let { if (it - lo < 1.0) lo + 1.0 else it }
    val runs = chartRuns(samples.map { it.seconds to value(it) })
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            val few = values.size < samples.size / 2
            Text(
                "low %.0f · high %.0f".format(values.min(), values.max()).let { if (fromZero) "low %.1f · high %.1f".format(values.min(), values.max()) else it } +
                    if (few) " · read ${values.size} of ${samples.size} times (no reading the rest)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0x99FFFFFF),
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF15151B)),
        ) {
            val w = size.width
            val h = size.height
            val pad = 8.dp.toPx()
            fun x(s: Int) = w * s / totalSeconds.toFloat()
            fun y(v: Double) = (h - pad - (h - 2 * pad) * ((v - lo) / (hi - lo))).toFloat()
            // Axis labels: the value at the bottom, middle and top on the left, the workout time
            // along the bottom, with a light grid.
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 11.sp.toPx()
                setColor(android.graphics.Color.argb(150, 255, 255, 255))
            }
            val canvas = drawContext.canvas.nativeCanvas
            listOf(lo, (lo + hi) / 2, hi).forEach { v ->
                val yy = y(v)
                drawLine(Color(0x10FFFFFF), Offset(0f, yy), Offset(w, yy))
                val label = if (!fromZero || hi - lo >= 20) "%.0f".format(v) else "%.1f".format(v)
                canvas.drawText(label, 6.dp.toPx(), (yy - 4.dp.toPx()).coerceAtLeast(paint.textSize), paint)
            }
            // Time grid: every 5 min (every minute for a short workout).
            val gridStep = if (totalSeconds <= 600) 60 else 300
            var m = gridStep
            while (m < totalSeconds) {
                drawLine(Color(0x14FFFFFF), Offset(x(m), 0f), Offset(x(m), h))
                // No label so close to the right edge that it would be cut off.
                if (x(m) < w - 48.dp.toPx()) canvas.drawText("%d:%02d".format(m / 60, 0), x(m) + 4.dp.toPx(), h - 4.dp.toPx(), paint)
                m += gridStep
            }
            runs.forEach { run ->
                if (run.size == 1) {
                    // A lone reading: a dot, not a line.
                    drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(run[0].first), y(run[0].second)))
                    return@forEach
                }
                val line = Path()
                run.forEachIndexed { i, (t, v) -> if (i == 0) line.moveTo(x(t), y(v)) else line.lineTo(x(t), y(v)) }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(x(run.last().first), h)
                    lineTo(x(run.first().first), h)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
                drawPath(line, color, style = Stroke(2.5.dp.toPx()))
            }
        }
    }
}
