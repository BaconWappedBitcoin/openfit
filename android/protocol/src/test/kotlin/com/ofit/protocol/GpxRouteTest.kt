package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GpxRouteTest {
    /** Points due north, 0.001 degrees apart (about 111 m each), with the given heights. */
    private fun gpx(heights: List<Double>, name: String? = "Hill loop", tag: String = "trkpt"): String = buildString {
        append("""<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">""")
        if (name != null) append("<metadata><name>$name</name></metadata>")
        append("<trk><trkseg>")
        heights.forEachIndexed { i, h -> append("""<$tag lat="${51.0 + i * 0.001}" lon="-0.1"><ele>$h</ele><time>2026-01-01T00:00:00Z</time></$tag>""") }
        append("</trkseg></trk></gpx>")
    }

    @Test
    fun `a climb then a descent becomes uphill and downhill steps`() {
        // About 1.1 km up 55 m (5 %), then 1.1 km flat, then 1.1 km down 33 m (-3 %).
        val heights = List(11) { it * 5.5 } + List(10) { 55.0 } + List(10) { 55.0 - (it + 1) * 3.3 }
        val route = GpxRoute.parse(gpx(heights))
        assertEquals("Hill loop", route.name)
        assertEquals(3_330.0, route.distanceMeters, 30.0)
        assertEquals(55.0, route.climbMeters, 0.01)
        val steps = GpxRoute.steps(route, speedKph = 5.0)
        // Three main steps, plus at most a short step where a segment straddles each change of grade.
        assertTrue(steps.size in 3..5, steps.toString())
        assertEquals(5.0, steps.first().inclinePercent, 0.5)
        assertTrue(steps.any { it.inclinePercent == 0.0 && it.durationSeconds > 600 }, steps.toString())
        assertEquals(-3.0, steps.last().inclinePercent, 0.5)
        assertTrue(steps.all { it.inclinePercent in -3.0..5.0 })
        // 3.33 km at 5 km/h is about 40 minutes.
        assertEquals(40.0 * 60, steps.sumOf { it.durationSeconds }.toDouble(), 60.0)
        assertTrue(steps.all { it.speedKph == 5.0 })
    }

    @Test
    fun `route points, namespaces, no name and bad files`() {
        val route = GpxRoute.parse(gpx(listOf(10.0, 11.0, 12.0), name = null, tag = "rtept"), fallbackName = "walk.gpx")
        assertEquals("walk.gpx", route.name)
        assertEquals(3, route.points.size)
        val prefixed = """<g:gpx xmlns:g="x"><g:trk><g:trkseg><g:trkpt lon="1.0" lat="2.0"><g:ele>5</g:ele></g:trkpt><g:trkpt lat='2.001' lon='1.0'><g:ele>6</g:ele></g:trkpt></g:trkseg></g:trk></g:gpx>"""
        assertEquals(2, GpxRoute.parse(prefixed).points.size)
        assertThrows<GpxRoute.GpxException> { GpxRoute.parse("""<gpx><trk><trkseg><trkpt lat="1" lon="1"/></trkseg></trk></gpx>""") }
        assertThrows<GpxRoute.GpxException> { GpxRoute.steps(GpxRoute.parse(gpx(listOf(1.0, 1.0)).replace("51.001", "51.0001")), 5.0) }
        // Noisy spikes are capped.
        val spiky = GpxRoute.steps(GpxRoute.parse(gpx(listOf(0.0, 100.0, 200.0))), 5.0)
        assertTrue(spiky.all { it.inclinePercent <= GpxRoute.MAX_GRADE })
    }

    @Test
    fun `the outline runs north up, fits in a unit box and keeps its shape`() {
        // An L: 1 km north, then 0.5 km east (lon steps scaled for latitude 51).
        val xml = buildString {
            append("<gpx><trk><trkseg>")
            for (i in 0..9) append("""<trkpt lat="${51.0 + i * 0.001}" lon="0.0"><ele>1</ele></trkpt>""")
            for (i in 1..5) append("""<trkpt lat="51.009" lon="${i * 0.00143}"><ele>1</ele></trkpt>""")
            append("</trkseg></trk></gpx>")
        }
        val pts = GpxRoute.outline(GpxRoute.parse(xml), count = 30).split(";").map { p -> p.split(",").map { it.toDouble() } }
        assertEquals(30, pts.size)
        assertTrue(pts.all { (x, y) -> x in -0.5..0.5 && y in -0.5..0.5 }, pts.toString())
        // Starts at the bottom (south), goes up the screen, then turns right (east).
        val (x0, y0) = pts.first(); val (x1, y1) = pts.last()
        assertTrue(y0 > 0.4 && y1 < -0.4, "north is up: $y0 -> $y1")
        assertTrue(x1 - x0 > 0.4, "ends east of the start: $x0 -> $x1")
        // About twice as tall as it is wide.
        val w = pts.maxOf { it[0] } - pts.minOf { it[0] }
        assertEquals(0.5, w, 0.06)
    }
}
