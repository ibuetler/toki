package com.hackinglab.toki

import com.hackinglab.toki.data.*
import org.junit.Assert.*
import org.junit.Test

class GlobalSearchTest {
    @Test fun hiddenCalendarEventsStayOutOfNormalSearch() {
        val hidden = Deadline().apply { id = "calendar:hidden"; kind = "calendar"; title = "Meeting"; this.hidden = true }
        val shown = Deadline().apply { id = "shown"; title = "Meeting" }
        assertEquals(listOf("shown"), GlobalSearch.find(listOf(hidden, shown), emptyList(), "meeting").map { it.id })
    }
    @Test fun searchesAllTypesAndCompletedTasksWithoutFilteringPastEvents() {
        val event = Deadline().apply { id = "one"; title = "Earlier meeting"; description = "needle"; startMillis = 1 }
        val birthday = Deadline().apply { id = "birthday:one"; kind = "birthday"; title = "Alex’s birthday"; description = "Needle" }
        val task = TokiTask().apply { id = "one"; title = "Finished thought"; notes = "NEEDLE"; completed = true }
        val results = GlobalSearch.find(listOf(event, birthday), listOf(task), "  needle  ")
        assertEquals(setOf("event", "birthday", "task"), results.map { it.kind }.toSet())
        assertEquals(3, results.map { it.key }.toSet().size)
        assertTrue(results.single { it.kind == "task" }.completed)
    }
    @Test fun importedSourceNamesAreSearchable() {
        val event = Deadline().apply { id = "calendar:one"; kind = "calendar"; title = "Review"; sourceNames = "Compass Security · OST" }
        val found = GlobalSearch.find(listOf(event), emptyList(), "compass")
        assertEquals(1, found.size)
        assertEquals("event", found.single().kind)
        assertEquals(event.sourceNames, found.single().detail)
    }
    @Test fun groupNamesAndTitlesMatchCaseInsensitively() {
        val event = Deadline().apply { id = "one"; title = "Review"; groupName = "Family" }
        assertEquals(1, GlobalSearch.find(listOf(event), emptyList(), "family").size)
        assertEquals(1, GlobalSearch.find(listOf(event), emptyList(), "REVIEW").size)
        assertTrue(GlobalSearch.find(listOf(event), emptyList(), "missing").isEmpty())
        assertTrue(GlobalSearch.find(listOf(event), emptyList(), " ").isEmpty())
    }
}
