package com.ofit.protocol

import java.time.Instant
import java.util.Locale

/**
 * A finished workout as a Garmin Training Center file (TCX), which Strava, Garmin Connect and most
 * training sites import. A treadmill has no GPS, so there are no positions: each point carries the
 * time, the distance so far, the heart rate when known, and the speed.
 */
object TcxWriter {

    fun write(entry: HistoryEntry): String {
        val start = Instant.ofEpochMilli(entry.startedAtMillis)
        val samples = entry.samples
        // Distance per point, from the samples' speeds, scaled so the last point matches the
        // workout's recorded distance (the console's or the program's own).
        val raw = DoubleArray(samples.size)
        for (i in 1 until samples.size) {
            val dt = samples[i].seconds - samples[i - 1].seconds
            raw[i] = raw[i - 1] + samples[i - 1].speedKph / 3.6 * dt
        }
        val scale = if (samples.size > 1 && raw.last() > 0 && entry.distanceMeters > 0) entry.distanceMeters / raw.last() else 1.0

        fun n(v: Double, digits: Int = 1) = "%.${digits}f".format(Locale.ROOT, v)
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" """)
            append("""xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">""").append('\n')
            append("  <Activities>\n")
            append("    <Activity Sport=\"Running\">\n")
            append("      <Id>").append(start).append("</Id>\n")
            append("      <Lap StartTime=\"").append(start).append("\">\n")
            append("        <TotalTimeSeconds>").append(entry.durationSeconds).append("</TotalTimeSeconds>\n")
            append("        <DistanceMeters>").append(n(entry.distanceMeters)).append("</DistanceMeters>\n")
            append("        <Calories>").append(Math.round(entry.calories ?: 0.0)).append("</Calories>\n")
            entry.averageHeartRate?.let { append("        <AverageHeartRateBpm><Value>").append(it).append("</Value></AverageHeartRateBpm>\n") }
            entry.maxHeartRate?.let { append("        <MaximumHeartRateBpm><Value>").append(it).append("</Value></MaximumHeartRateBpm>\n") }
            append("        <Intensity>Active</Intensity>\n")
            append("        <TriggerMethod>Manual</TriggerMethod>\n")
            if (samples.isNotEmpty()) {
                append("        <Track>\n")
                samples.forEachIndexed { i, s ->
                    append("          <Trackpoint>\n")
                    append("            <Time>").append(start.plusSeconds(s.seconds.toLong())).append("</Time>\n")
                    append("            <DistanceMeters>").append(n(raw[i] * scale)).append("</DistanceMeters>\n")
                    s.heartRate?.let { append("            <HeartRateBpm><Value>").append(it).append("</Value></HeartRateBpm>\n") }
                    append("            <Extensions><ns3:TPX><ns3:Speed>").append(n(s.speedKph / 3.6, 2)).append("</ns3:Speed></ns3:TPX></Extensions>\n")
                    append("          </Trackpoint>\n")
                }
                append("        </Track>\n")
            }
            append("      </Lap>\n")
            append("      <Notes>").append(escape(entry.name + if (entry.noTreadmill) " (no treadmill)" else " (treadmill)")).append("</Notes>\n")
            append("    </Activity>\n")
            append("  </Activities>\n")
            append("</TrainingCenterDatabase>\n")
        }
    }

    /** A file name for the export: the date and the workout's name, safe for any file system. */
    fun fileName(entry: HistoryEntry): String {
        val date = Instant.ofEpochMilli(entry.startedAtMillis).toString().take(10)
        val name = entry.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '-').take(40).ifBlank { "workout" }
        return "$date-$name.tcx"
    }

    private fun escape(text: String) = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
