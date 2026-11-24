package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryFilterTest {
    private val day = 24L * 3600 * 1000
    private val now = 100 * day
    private fun e(name: String, daysAgo: Long, seconds: Long, meters: Double, noTreadmill: Boolean = false, outcome: String = "COMPLETED", cal: Double? = null) =
        HistoryEntry(now - daysAgo * day, name, seconds, meters, cal, 0.0, null, null, outcome, noTreadmill)

    private val list = listOf(
        e("Interlaken lake run", 1, 1800, 5000.0, cal = 300.0),
        e("Hill climb", 10, 2400, 3000.0, noTreadmill = true, cal = 200.0),
        e("Intervals", 40, 1200, 4000.0, outcome = "ABANDONED", cal = 400.0),
    )

    @Test
    fun `search matches part of the name, any case`() {
        assertEquals(listOf("Interlaken lake run"), HistoryFilter.apply(list, "LAKE", nowMillis = now).map { it.name })
    }

    @Test
    fun `filters`() {
        assertEquals(listOf("Hill climb"), HistoryFilter.apply(list, show = HistoryFilter.Show.NO_TREADMILL, nowMillis = now).map { it.name })
        assertEquals(2, HistoryFilter.apply(list, show = HistoryFilter.Show.TREADMILL, nowMillis = now).size)
        assertEquals(2, HistoryFilter.apply(list, show = HistoryFilter.Show.COMPLETED, nowMillis = now).size)
        assertEquals(2, HistoryFilter.apply(list, show = HistoryFilter.Show.LAST_30_DAYS, nowMillis = now).size)
    }

    @Test
    fun `sorts`() {
        assertEquals("Interlaken lake run", HistoryFilter.apply(list, nowMillis = now).first().name)
        assertEquals("Hill climb", HistoryFilter.apply(list, sort = HistoryFilter.Sort.LONGEST, nowMillis = now).first().name)
        assertEquals("Interlaken lake run", HistoryFilter.apply(list, sort = HistoryFilter.Sort.FARTHEST, nowMillis = now).first().name)
        assertEquals("Intervals", HistoryFilter.apply(list, sort = HistoryFilter.Sort.MOST_CALORIES, nowMillis = now).first().name)
    }
}
