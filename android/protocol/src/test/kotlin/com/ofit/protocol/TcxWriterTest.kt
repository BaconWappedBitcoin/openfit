package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.xml.parsers.DocumentBuilderFactory

class TcxWriterTest {

    private val entry = HistoryEntry(
        startedAtMillis = 1_795_500_000_000,
        name = "Hills & <stuff>",
        durationSeconds = 30,
        distanceMeters = 60.0,
        calories = 4.4,
        verticalGainMeters = 0.0,
        averageHeartRate = 120,
        maxHeartRate = 130,
        outcome = "COMPLETED",
        samples = listOf(
            HistorySample(0, 7.2, 0.0, 110),
            HistorySample(10, 7.2, 1.0, 120),
            HistorySample(20, 7.2, 1.0, null),
            HistorySample(30, 7.2, 0.0, 130),
        ),
    )

    private fun parse(xml: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(xml.byteInputStream())

    @Test
    fun `writes a well-formed file with the lap totals`() {
        val doc = parse(TcxWriter.write(entry))
        assertEquals("TrainingCenterDatabase", doc.documentElement.localName)
        assertEquals("30", doc.getElementsByTagNameNS("*", "TotalTimeSeconds").item(0).textContent)
        assertEquals("4", doc.getElementsByTagNameNS("*", "Calories").item(0).textContent)
        assertEquals(4, doc.getElementsByTagNameNS("*", "Trackpoint").length)
    }

    @Test
    fun `distance per point ends at the workout's distance`() {
        val doc = parse(TcxWriter.write(entry))
        val distances = doc.getElementsByTagNameNS("*", "DistanceMeters")
        // The first is the lap's; the last is the last trackpoint's.
        assertEquals("60.0", distances.item(distances.length - 1).textContent)
        assertEquals("0.0", distances.item(1).textContent)
    }

    @Test
    fun `heart rate only where known, and the name is escaped`() {
        val xml = TcxWriter.write(entry)
        assertEquals(3 + 2, Regex("<Value>").findAll(xml).count(), "3 points + avg + max")
        assertTrue(xml.contains("Hills &amp; &lt;stuff&gt;"))
        parse(xml)
    }

    @Test
    fun `a safe file name`() {
        assertEquals("2026-11-24-Hills--stuff.tcx".length, TcxWriter.fileName(entry).length)
        assertTrue(TcxWriter.fileName(entry).matches(Regex("[0-9-]+-[A-Za-z0-9_-]+\\.tcx")))
    }
}
