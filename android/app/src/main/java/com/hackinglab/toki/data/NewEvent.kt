package com.hackinglab.toki.data

import java.time.LocalDateTime
import java.time.ZoneId

/** Local form values are converted once, using the event's zone rather than the phone's. */
data class NewEvent(val title: String, val description: String, val localStart: LocalDateTime,
    val timezone: String = ZoneId.systemDefault().id, val reminderMinutes: Int = 5,
    val durationMinutes: Int = 60, val enabled: Boolean = true, val rrule: String = "", val birthday: BirthdaySpec? = null) {
    fun deadline(): Deadline {
        birthday?.let { return it.deadline(title, description, enabled) }
        require(title.isNotBlank()) { "Enter an event title." }
        require(title.trim().length <= 200) { "Keep the title within 200 characters." }
        val zone = try { ZoneId.of(timezone) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid IANA time zone.") }
        require(zone.rules.getValidOffsets(localStart).isNotEmpty()) { "This time does not exist because the clocks change. Choose another time." }
        require(reminderMinutes in 0..525600) { "Reminder must be between 0 and 525600 minutes." }
        require(durationMinutes in 0..525600) { "Duration must be between 0 and 525600 minutes." }
        return Deadline().also {
            it.title = title.trim(); it.description = description; it.timezone = timezone
            it.startMillis = localStart.atZone(zone).toInstant().toEpochMilli()
            it.enabled = enabled; it.reminderMinutes = reminderMinutes; it.rrule = rrule.trim()
            it.recurrenceLoaded = true
        }
    }
}
