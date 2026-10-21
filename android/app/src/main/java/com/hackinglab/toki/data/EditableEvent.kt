package com.hackinglab.toki.data

import java.time.Instant

/** A fresh server draft, before personal reminder preferences are applied. */
data class EditableEvent(val id: String, val original: NewEvent, val shared: Boolean = false) {
    fun changes(draft: NewEvent): Map<String, Any> {
        require((original.birthday == null) == (draft.birthday == null)) { "The event type cannot be changed." }
        val event = draft.deadline()
        if (draft.birthday == null && (original.rrule != draft.rrule || original.localStart != draft.localStart))
            OccurrencePlanner.next(event, System.currentTimeMillis()) // Validate recurrence; past events may legitimately have no next alarm.
        val changes = linkedMapOf<String, Any>()
        fun change(key: String, before: Any, after: Any) { if (before != after) changes[key] = after }
        change(if (draft.birthday == null) "title" else "name", original.title.trim(), draft.title.trim())
        change("description", original.description, draft.description)
        change("enabled", original.enabled, draft.enabled)
        val birthday = draft.birthday
        if (birthday != null) {
            val before = requireNotNull(original.birthday)
            change("month", before.month, birthday.month); change("day", before.day, birthday.day)
            change("birth_year", before.birthYear, birthday.birthYear)
            change("reminder_hour", before.hour, birthday.hour); change("reminder_minute", before.minute, birthday.minute)
            change("leap_day_policy", before.leapDayPolicy, birthday.leapDayPolicy)
        } else {
            if (original.localStart != draft.localStart || original.timezone != draft.timezone)
                changes["start_at"] = Instant.ofEpochMilli(event.startMillis).toString()
            change("timezone", original.timezone, draft.timezone)
            change("reminder_minutes", original.reminderMinutes, draft.reminderMinutes)
            change("duration_minutes", original.durationMinutes, draft.durationMinutes)
            change("rrule", original.rrule, draft.rrule.trim())
        }
        // Owner, group, all_day, recurrence exceptions and personal preferences stay untouched.
        return changes
    }
}
