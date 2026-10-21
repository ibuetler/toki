package com.hackinglab.toki

import com.hackinglab.toki.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class EditableEventTest {
    private val draft = NewEvent("Meeting", "Notes", LocalDateTime.of(2026, 10, 25, 2, 30), "Europe/Zurich",
        reminderMinutes = 30, durationMinutes = 120, rrule = "FREQ=WEEKLY;BYDAY=MO,FR")
    @Test fun unchangedEditIsNoOp() { assertTrue(EditableEvent("id", draft).changes(draft).isEmpty()) }
    @Test fun renamePreservesAmbiguousInstantAndAdvancedFields() {
        val changes = EditableEvent("id", draft, true).changes(draft.copy(title = "Renamed"))
        assertEquals(mapOf("title" to "Renamed"), changes)
        for (key in listOf("owner", "group", "start_at", "all_day", "exdates", "rdates", "reminder_minutes", "rrule")) assertFalse(changes.containsKey(key))
    }
    @Test fun timingChangesKeepSavedZone() {
        val changes = EditableEvent("id", draft).changes(draft.copy(localStart = LocalDateTime.of(2026, 12, 20, 16, 30), reminderMinutes = 10))
        assertEquals("2026-12-20T15:30:00Z", changes["start_at"])
        assertEquals(10, changes["reminder_minutes"])
        assertFalse(changes.containsKey("timezone"))
    }
    @Test fun birthdayEditsIncludeEuropeanYearAndPolicy() {
        val before = draft.copy(title = "Alex", birthday = BirthdaySpec(2, 29, 1968))
        val changes = EditableEvent("birthday:id", before).changes(before.copy(birthday = BirthdaySpec(2, 29, 1972, 10, 15, "mar1")))
        assertEquals(mapOf("birth_year" to 1972, "reminder_hour" to 10, "reminder_minute" to 15, "leap_day_policy" to "mar1"), changes)
    }
    @Test(expected = IllegalArgumentException::class) fun cannotConvertEventType() {
        EditableEvent("id", draft).changes(draft.copy(birthday = BirthdaySpec(10, 8)))
    }
}
