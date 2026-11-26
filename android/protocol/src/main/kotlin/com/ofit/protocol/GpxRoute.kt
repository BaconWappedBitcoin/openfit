package com.ofit.protocol

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A GPX route as an incline workout (P6): the track (or route) points with elevation are cut into
 * segments of about [SEGMENT_METERS], each becomes a step at the segment's average grade, and
 * neighbouring steps of nearly the same grade are merged. The belt runs at one speed the runner
 * picks (they can change it in the editor); the treadmill's own limits clamp the grades later.
 */
object GpxRoute {
    data class Point(val lat: Double, val lon: Double, val ele: Double)

    data class Route(val name: String, val points: List<Point>) {
        val distanceMeters: Double get() = points.zipWithNext { a, b -> distance(a, b) }.sum()
        val climbMeters: Double get() = points.zipWithNext { a, b -> (b.ele - a.ele).coerceAtLeast(0.0) }.sum()
    }

    const val SEGMENT_METERS = 200.0
    /** Steps whose grades differ by less than this are merged. */
    const val MERGE_PERCENT = 0.75
    /** GPX elevation is noisy; real paths are rarely steeper than this. */
    const val MAX_GRADE = 15.0

    class GpxException(message: String) : Exception(message)

    private val POINT = Regex("""<(?:\w+:)?(trkpt|rtept)\b([^>]*?)(/>|>(.*?)</(?:\w+:)?\1>)""", RegexOption.DOT_MATCHES_ALL)
    private val LAT = Regex("""\blat\s*=\s*["']([-0-9.eE+]+)["']""")
    private val LON = Regex("""\blon\s*=\s*["']([-0-9.eE+]+)["']""")
    private val ELE = Regex("""<(?:\w+:)?ele>\s*([-0-9.eE+]+)\s*</(?:\w+:)?ele>""")
    private val NAME = Regex("""<(?:\w+:)?name>\s*(?:<!\[CDATA\[)?(.*?)(?:]]>)?\s*</(?:\w+:)?name>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(gpx: String, fallbackName: String = "Imported route"): Route {
        val points = POINT.findAll(gpx).mapNotNull { m ->
            val attrs = m.groupValues[2]
            val lat = LAT.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = LON.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val ele = ELE.find(m.groupValues[4])?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            Point(lat, lon, ele)
        }.toList()
        if (points.size < 2) throw GpxException("No points with elevation in this file. OpenFit needs a GPX track or route with <ele> heights.")
        val name = NAME.find(gpx)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: fallbackName
        return Route(name, points)
    }

    /**
     * The route's outline for the 3D trail map (2026-11-25): about [count] points evenly spaced
     * along it, flattened (equirectangular, fine at route scale), centred and scaled so the longer
     * side spans 1, north up (y grows southward, as on a screen), as "x,y;x,y;..." with 3 decimals.
     */
    fun outline(route: Route, count: Int = 160): String {
        val pts = route.points
        val lat0 = pts.sumOf { it.lat } / pts.size * PI / 180
        val xy = pts.map { (it.lon * cos(lat0)) to -it.lat }
        val dist = DoubleArray(pts.size)
        for (i in 1 until pts.size) dist[i] = dist[i - 1] + distance(pts[i - 1], pts[i])
        val total = dist.last().coerceAtLeast(1e-9)
        val sampled = (0 until count).map { k ->
            val d = total * k / (count - 1)
            var i = dist.indexOfFirst { it >= d }.let { if (it <= 0) 1 else it }
            if (i >= dist.size) i = dist.size - 1
            val span = dist[i] - dist[i - 1]
            val t = if (span <= 0) 0.0 else ((d - dist[i - 1]) / span).coerceIn(0.0, 1.0)
            (xy[i - 1].first + (xy[i].first - xy[i - 1].first) * t) to (xy[i - 1].second + (xy[i].second - xy[i - 1].second) * t)
        }
        val minX = sampled.minOf { it.first }; val maxX = sampled.maxOf { it.first }
        val minY = sampled.minOf { it.second }; val maxY = sampled.maxOf { it.second }
        val span = maxOf(maxX - minX, maxY - minY).takeIf { it > 0 } ?: 1.0
        return sampled.joinToString(";") { (x, y) ->
            "%.3f,%.3f".format(java.util.Locale.ROOT, (x - (minX + maxX) / 2) / span, (y - (minY + maxY) / 2) / span)
        }
    }

    /** Great-circle distance in metres. */
    fun distance(a: Point, b: Point): Double {
        val r = 6_371_000.0
        val p1 = a.lat * PI / 180
        val p2 = b.lat * PI / 180
        val dp = p2 - p1
        val dl = (b.lon - a.lon) * PI / 180
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** The route as steps at [speedKph], about [SEGMENT_METERS] each before merging. */
    fun steps(route: Route, speedKph: Double): List<WorkoutStep> {
        // Cumulative distance and elevation along the route.
        val dist = DoubleArray(route.points.size)
        for (i in 1 until route.points.size) dist[i] = dist[i - 1] + distance(route.points[i - 1], route.points[i])
        val total = dist.last()
        if (total < 50) throw GpxException("This route is too short to make a workout from.")
        fun eleAt(d: Double): Double {
            var i = dist.indexOfFirst { it >= d }.let { if (it <= 0) 1 else it }
            if (i >= dist.size) i = dist.size - 1
            val span = dist[i] - dist[i - 1]
            val t = if (span <= 0) 0.0 else (d - dist[i - 1]) / span
            return route.points[i - 1].ele + (route.points[i].ele - route.points[i - 1].ele) * t
        }
        val count = (total / SEGMENT_METERS).roundToInt().coerceAtLeast(1)
        val length = total / count
        val raw = (0 until count).map { k ->
            val grade = (eleAt((k + 1) * length) - eleAt(k * length)) / length * 100
            grade.coerceIn(-MAX_GRADE, MAX_GRADE) to length
        }
        // Merge neighbours of nearly the same grade (weighted by length), so the belt does not fiddle.
        val merged = mutableListOf<Pair<Double, Double>>()
        raw.forEach { (g, l) ->
            val last = merged.lastOrNull()
            if (last != null && abs(last.first - g) < MERGE_PERCENT) {
                val len = last.second + l
                merged[merged.lastIndex] = ((last.first * last.second + g * l) / len) to len
            } else {
                merged += g to l
            }
        }
        return merged.map { (g, l) ->
            WorkoutStep(
                speedKph = speedKph,
                inclinePercent = (g * 2).roundToInt() / 2.0,
                durationSeconds = (l / (speedKph / 3.6)).roundToInt().coerceAtLeast(1),
            )
        }
    }
}
