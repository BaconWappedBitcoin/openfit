package com.ofit.app.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChartRunsTest {
    @Test
    fun `missing readings break the line`() {
        val runs = chartRuns(listOf(0 to 100.0, 10 to 110.0, 20 to null, 30 to 120.0, 40 to 125.0))
        assertEquals(listOf(listOf(0 to 100.0, 10 to 110.0), listOf(30 to 120.0, 40 to 125.0)), runs)
    }

    @Test
    fun `a lone reading stays a single point`() {
        val runs = chartRuns(listOf(0 to null, 10 to null, 20 to 110.0, 30 to null))
        assertEquals(listOf(listOf(20 to 110.0)), runs)
    }

    @Test
    fun `a jump in the clock (skip or seek) breaks the line`() {
        val runs = chartRuns(listOf(0 to 4.0, 10 to 4.0, 643 to 5.0, 653 to 5.0))
        assertEquals(2, runs.size)
    }

    @Test
    fun `nothing to draw`() = assertEquals(emptyList<List<Pair<Int, Double>>>(), chartRuns(listOf(0 to null)))
}
