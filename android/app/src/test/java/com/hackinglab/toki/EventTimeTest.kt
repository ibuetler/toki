package com.hackinglab.toki

import com.hackinglab.toki.data.EventTime
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class EventTimeTest {
    @Test fun pocketBaseTimestampKeepsUtcInstant() {
        assertEquals(Instant.parse("2026-10-26T08:00:00Z").toEpochMilli(), EventTime.parse("2026-10-26 08:00:00.000Z"))
    }
    @Test fun reminderOffsetCrossesDateBoundary() {
        val start = EventTime.parse("2026-10-26 00:02:00.000Z")
        assertEquals(Instant.parse("2026-10-25T23:57:00Z").toEpochMilli(), EventTime.reminderAt(start, 5))
        assertEquals(start, EventTime.reminderAt(start, 0))
    }
    @Test fun unsupportedRecurrenceNeverSchedulesAsOneOff() {
        assertTrue(EventTime.supportsReminder("", false))
        assertFalse(EventTime.supportsReminder("FREQ=YEARLY", false))
        assertFalse(EventTime.supportsReminder("", true))
    }
}
