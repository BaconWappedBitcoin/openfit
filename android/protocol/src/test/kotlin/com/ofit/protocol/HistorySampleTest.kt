package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistorySampleTest {

    @Test
    fun `samples survive the history file`() {
        val samples = listOf(
            HistorySample(0, 4.5, 0.0, null),
            HistorySample(10, 8.04, 2.5, 141),
            HistorySample(20, 8.0, -1.0, 150),
        )
        val entry = HistoryEntry(1L, "Run", 600, 1000.0, 50.0, 0.0, 140, 170, "COMPLETED", samples = samples)
        val back = HistoryCodec.decode(HistoryCodec.encode(listOf(entry))).single()
        assertEquals(samples, back.samples)
    }

    @Test
    fun `old entries have no samples`() {
        val old = HistoryCodec.decode("""[{"startedAt":2,"name":"Old","durationSeconds":120,"distanceMeters":0}]""").single()
        assertTrue(old.samples.isEmpty())
    }
}
