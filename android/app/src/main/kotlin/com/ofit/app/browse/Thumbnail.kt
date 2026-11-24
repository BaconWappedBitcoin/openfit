package com.ofit.app.browse

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import com.ofit.protocol.WorkoutStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * YouTube thumbnails, fetched from YouTube's public image host by video id and kept in memory.
 * No image library: one small HTTP GET per thumbnail is all this needs.
 */
private object ThumbnailCache {
    private val cache = LruCache<String, Bitmap>(48)

    fun get(id: String): Bitmap? = cache.get(id)

    suspend fun load(id: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(id)?.let { return@withContext it }
        runCatching {
            val connection = URL("https://i.ytimg.com/vi/$id/hqdefault.jpg").openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
                .also { connection.disconnect() }
        }.getOrNull()?.also { cache.put(id, it) }
    }
}

/** A workout's picture: its video's thumbnail, or a drawing of its incline profile. */
@Composable
fun WorkoutArt(videoId: String?, steps: List<WorkoutStep>, accent: Color, modifier: Modifier = Modifier) {
    var bitmap by remember(videoId) { mutableStateOf(videoId?.let { ThumbnailCache.get(it) }) }
    LaunchedEffect(videoId) {
        if (videoId != null && bitmap == null) bitmap = ThumbnailCache.load(videoId)
    }
    Box(modifier) {
        val image = bitmap
        if (image != null) {
            Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            ProfileArt(steps, accent, Modifier.fillMaxSize())
        }
    }
}

/** The incline profile as a filled mountain over a dark gradient. */
@Composable
private fun ProfileArt(steps: List<WorkoutStep>, accent: Color, modifier: Modifier) {
    Canvas(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFF1C1C24), Color(0xFF0E0E12)))),
    ) {
        if (steps.isEmpty()) return@Canvas
        val total = steps.sumOf { it.durationSeconds }.coerceAtLeast(1)
        val maxIncline = (steps.maxOf { it.inclinePercent }).coerceAtLeast(1.0)
        val top = size.height * 0.25f
        val bottom = size.height * 0.92f
        fun y(p: Double) = (bottom - (bottom - top) * (p / maxIncline)).toFloat()
        val path = Path().apply { moveTo(0f, bottom) }
        var t = 0
        steps.forEach { step ->
            val x0 = size.width * t / total
            t += step.durationSeconds
            val x1 = size.width * t / total
            path.lineTo(x0, y(step.inclinePercent))
            path.lineTo(x1, y(step.inclinePercent))
        }
        path.lineTo(size.width, bottom)
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.08f)), startY = top, endY = bottom))
        drawPath(path, accent, style = Stroke(width = 2f))
        drawLine(Color(0x22FFFFFF), Offset(0f, bottom), Offset(size.width, bottom))
    }
}
