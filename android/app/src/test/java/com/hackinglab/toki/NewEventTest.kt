package com.hackinglab.toki

import com.hackinglab.toki.data.NewEvent
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class NewEventTest {
    @Test fun newEventsUseDeviceZone() {
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            val event = NewEvent("Tokyo", "", LocalDateTime.of(2026, 10, 20, 16, 30)).deadline()
            assertEquals("Asia/Tokyo", event.timezone)
            assertEquals(Instant.parse("2026-10-20T07:30:00Z").toEpochMilli(), event.startMillis)
            val explicit = NewEvent("Zurich", "", LocalDateTime.of(2026, 10, 20, 16, 30), timezone = "Europe/Zurich").deadline()
            assertEquals(Instant.parse("2026-10-20T14:30:00Z").toEpochMilli(), explicit.startMillis)
        } finally { java.util.TimeZone.setDefault(original) }
    }
    @Test fun defaultsAndZurichConversion() {
        val draft = NewEvent("  Meeting  ", "Notes", LocalDateTime.of(2026, 10, 20, 16, 30), timezone = "Europe/Zurich")
        val event = draft.deadline()
        assertTrue(event.enabled)
        assertEquals("Europe/Zurich", event.timezone)
        assertEquals("Meeting", event.title)
        assertEquals(Instant.parse("2026-10-20T14:30:00Z").toEpochMilli(), event.startMillis)
    }
    @Test fun disabledAndWinterTimeArePreserved() {
        val event = NewEvent("Winter", "", LocalDateTime.of(2026, 12, 20, 16, 30), timezone = "Europe/Zurich", enabled = false).deadline()
        assertFalse(event.enabled)
        assertEquals(Instant.parse("2026-12-20T15:30:00Z").toEpochMilli(), event.startMillis)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectInvalidZone() {
        NewEvent("Meeting", "", LocalDateTime.of(2026, 10, 20, 16, 30), timezone = "Europe/Zürich").deadline()
    }
    @Test(expected = IllegalArgumentException::class) fun rejectDaylightSavingGap() {
        NewEvent("Meeting", "", LocalDateTime.of(2027, 3, 28, 2, 30), timezone = "Europe/Zurich").deadline()
    }
}
