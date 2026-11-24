package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ZoneTimerTest {

    private val zones = HeartRateZones.of(userMaxHr = 200, age = null)!!

    @Test
    fun `time goes to the zone of each reading`() {
        val timer = ZoneTimer()
        repeat(30) { timer.add(1_000, 90, zones) }   // 45 % -> below zone 1
        repeat(60) { timer.add(1_000, 130, zones) }  // 65 % -> zone 2
        repeat(90) { timer.add(1_000, 150, zones) }  // 75 % -> zone 3
        repeat(10) { timer.add(1_000, 195, zones) }  // 97 % -> zone 5
        assertEquals(listOf(30L, 0L, 60L, 90L, 0L, 10L), timer.seconds())
    }

    @Test
    fun `nothing counted without zones or a reading`() {
        val timer = ZoneTimer()
        timer.add(1_000, null, zones)
        timer.add(1_000, 140, null)
        timer.add(0, 140, zones)
        assertNull(timer.seconds())
    }

    @Test
    fun `history keeps the zone times`() {
        val entry = HistoryEntry(1L, "Run", 600, 1000.0, 50.0, 0.0, 140, 170, "COMPLETED", zoneSeconds = listOf(0L, 10L, 200L, 300L, 90L, 0L))
        val back = HistoryCodec.decode(HistoryCodec.encode(listOf(entry))).single()
        assertEquals(entry.zoneSeconds, back.zoneSeconds)
        val old = HistoryCodec.decode("""[{"startedAt":2,"name":"Old","durationSeconds":120,"distanceMeters":0}]""").single()
        assertNull(old.zoneSeconds)
    }
}
