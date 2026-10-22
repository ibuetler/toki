package com.hackinglab.toki

import com.hackinglab.toki.data.*
import org.junit.Assert.*
import org.junit.Test

class TitlePatternTest {
    @Test fun wildcardsMatchWholeTitleWithoutRegexSyntax() {
        assertTrue(TitlePattern.matches("Marina in CH", "Marina *"))
        assertTrue(TitlePattern.matches(" MARINA in CH ", " marina * "))
        assertTrue(TitlePattern.matches("My Vacation plan", "*Vacation*"))
        assertTrue(TitlePattern.matches("Team A", "Team ?"))
        assertFalse(TitlePattern.matches("Team AB", "Team ?"))
        assertFalse(TitlePattern.matches("With Marina in CH", "Marina *"))
        assertFalse(TitlePattern.matches("Marina in CH", "Marina"))
        assertFalse(TitlePattern.matches("Anything", ""))
        assertTrue(TitlePattern.matches("A [B] (C).", "A [B] (C)."))
        assertFalse(TitlePattern.matches("Marina CH", "Marina *", exact = true))
    }
    @Test fun previewCountsOnlyImportedEventsAndSelectedSource() {
        val source = CalendarSource(id = "ost", name = "OST")
        val imported = Deadline().apply { kind = "calendar"; title = "Marina in CH"; sourceNames = "OST · Hacking-Lab"; hidden = true }
        val manual = Deadline().apply { title = "Marina in CH" }
        assertEquals(1, TitlePattern.preview(listOf(imported, manual), CalendarRule(pattern = "Marina *", source = "ost"), listOf(source)))
        assertEquals(0, TitlePattern.preview(listOf(imported), CalendarRule(pattern = "Marina *", source = "missing"), listOf(source)))
    }
    @Test fun malformedCalendarUrlsAreRejected() {
        for (url in listOf("http://example.com/a.ics", "https://user:pass@example.com/a.ics", "https://example.com:8090/a.ics")) {
            try { CalendarSource(name = "Example", url = url).validate(); fail("Expected invalid URL") } catch (_: IllegalArgumentException) { }
        }
    }
}
