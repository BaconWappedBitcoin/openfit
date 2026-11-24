package com.ofit.app.workout

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * The cool-down picture (user request 2026-11-24): instead of the video frozen on its last frame,
 * a calm drawn path at dusk that rolls towards the runner at the belt's speed, between grass, hills
 * and trees. The coach walks on it.
 */
@Composable
fun CoolDownScene(speedKph: Double, modifier: Modifier = Modifier) {
    val speed by rememberUpdatedState(speedKph)
    var travelled by remember { mutableFloatStateOf(0f) } // metres
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) travelled += (speed.coerceAtLeast(0.0) / 3.6).toFloat() * (now - last) / 1e9f
                last = now
            }
        }
    }
    // The path's vanishing point sits under the coach, who stands a little left of the middle.
    val coachShift = with(LocalDensity.current) { 90.dp.toPx() }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.46f
        val cx = w / 2f - coachShift
        // Sky: dusk, from deep blue to a warm glow at the horizon.
        drawRect(Brush.verticalGradient(0f to Color(0xFF1B2A4A), 0.7f to Color(0xFF5B5A8A), 1f to Color(0xFFE8A87C), endY = horizon), size = size.copy(height = horizon))
        drawCircle(Color(0x55FFD9A0), radius = h * 0.09f, center = Offset(w * 0.72f, horizon - h * 0.02f))
        // Hills, far and near, drifting a little so the scene is alive.
        fun hills(color: Color, height: Float, waves: Float, drift: Float) {
            val p = Path().apply {
                moveTo(0f, horizon)
                var x = 0f
                while (x <= w) {
                    val y = horizon - height * (0.55f + 0.45f * sin((x / w) * waves * 6.283f + drift))
                    lineTo(x, y)
                    x += 8f
                }
                lineTo(w, horizon)
                close()
            }
            drawPath(p, color)
        }
        hills(Color(0xFF3B4870), h * 0.09f, 2.3f, travelled * 0.004f)
        hills(Color(0xFF2C4A3E), h * 0.055f, 3.7f, 1.3f + travelled * 0.01f)
        // Ground.
        drawRect(Brush.verticalGradient(0f to Color(0xFF2F5A3A), 1f to Color(0xFF1E3D26), startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = size.copy(height = h - horizon))

        // Perspective: something z metres ahead is drawn at horizon + (h - horizon) * NEAR / z.
        val near = 1.6f
        fun yAt(z: Float) = horizon + (h - horizon) * near / z
        val halfAtNear = w * 0.20f
        fun halfAt(z: Float) = halfAtNear * near / z
        // The path itself.
        val path = Path().apply {
            moveTo(cx - halfAtNear, h)
            lineTo(cx - 2f, horizon)
            lineTo(cx + 2f, horizon)
            lineTo(cx + halfAtNear, h)
            close()
        }
        drawPath(path, Brush.verticalGradient(0f to Color(0xFF8C7A63), 1f to Color(0xFFB59E7E), startY = horizon, endY = h))
        // Stripes across the path and posts beside it, coming towards the runner.
        val spacing = 3f
        val offset = travelled % spacing
        var z = spacing - offset + near * 0.9f
        while (z < 120f) {
            val y = yAt(z)
            val half = halfAt(z)
            val fade = (1f - z / 120f).coerceIn(0f, 1f)
            drawLine(Color(0xFF9E8A6E).copy(alpha = 0.8f * fade), Offset(cx - half, y), Offset(cx + half, y), strokeWidth = (6f * near / z).coerceAtLeast(1f))
            z += spacing
        }
        // Trees every 12 m on both sides, far ones first so near ones are drawn on top.
        val treeGap = 12f
        val trees = mutableListOf<Float>()
        var t = treeGap - (travelled % treeGap) + near * 0.6f
        while (t < 150f) { trees += t; t += treeGap }
        for (tz in trees.sortedDescending()) {
            val y = yAt(tz)
            val s = near / tz
            val fade = (1f - tz / 150f).coerceIn(0f, 1f)
            for (side in listOf(-1f, 1f)) {
                val x = cx + side * halfAtNear * 2.6f * s
                val trunk = h * 0.9f * s
                drawLine(Color(0xFF4A3424).copy(alpha = fade), Offset(x, y), Offset(x, y - trunk * 0.5f), strokeWidth = (h * 0.05f * s).coerceAtLeast(1f))
                drawCircle(Color(0xFF2E6B3E).copy(alpha = fade), radius = h * 0.32f * s, center = Offset(x, y - trunk * 0.75f))
            }
        }
    }
}
