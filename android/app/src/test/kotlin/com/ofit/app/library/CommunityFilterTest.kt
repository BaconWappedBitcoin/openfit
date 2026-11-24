package com.ofit.app.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommunityFilterTest {
    private fun e(id: String, name: String, minutes: Int, video: Boolean = true, origin: String = "manual") =
        LibraryEntry(id, name, if (video) "v$id" else null, 10, minutes * 60, 4.0, 3, origin)

    private val list = listOf(
        e("1", "Interlaken lake", 39, origin = "analysis"),
        e("2", "Quick intervals", 15, video = false),
        e("3", "Dallas long run", 57),
    )

    @Test fun search() = assertEquals(listOf("1"), filterCommunity(list, "LAKE", CommunityFilter.ALL).map { it.id })

    @Test fun filters() {
        assertEquals(listOf("1", "3"), filterCommunity(list, "", CommunityFilter.VIDEO).map { it.id })
        assertEquals(listOf("2"), filterCommunity(list, "", CommunityFilter.SHORT).map { it.id })
        assertEquals(listOf("3"), filterCommunity(list, "", CommunityFilter.LONG).map { it.id })
        assertEquals(listOf("1"), filterCommunity(list, "", CommunityFilter.FROM_VIDEO).map { it.id })
    }
}
