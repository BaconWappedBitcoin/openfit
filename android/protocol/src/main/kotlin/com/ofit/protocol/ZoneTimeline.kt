package com.ofit.protocol

/**
 * The heart-rate zone over a workout, for the zone colour strip under the workout chart (spec 002
 * H5): a point each time the zone changes, on the workout clock, and the coloured stretches they make.
 */
class ZoneTimeline {
    data class Point(val seconds: Float, val zone: Int?)
    data class Segment(val fromSeconds: Float, val toSeconds: Float, val zone: Int)

    private val points = ArrayList<Point>()

    val isEmpty: Boolean get() = points.none { it.zone != null }

    /** The zone now ([zone] null without a reading). Only changes are kept; a jump back (a seek) starts over. */
    fun record(seconds: Float, zone: Int?) {
        if (points.isNotEmpty() && seconds < points.last().seconds - 1f) points.clear()
        if (points.lastOrNull()?.zone == zone && points.isNotEmpty()) return
        points += Point(seconds, zone)
    }

    fun clear() = points.clear()

    /** The stretches with a zone, up to [nowSeconds]; stretches without a reading are left out. */
    fun segments(nowSeconds: Float): List<Segment> =
        points.mapIndexedNotNull { i, p ->
            val zone = p.zone ?: return@mapIndexedNotNull null
            val end = (points.getOrNull(i + 1)?.seconds ?: nowSeconds).coerceAtMost(nowSeconds)
            if (end <= p.seconds) null else Segment(p.seconds, end, zone)
        }
}
