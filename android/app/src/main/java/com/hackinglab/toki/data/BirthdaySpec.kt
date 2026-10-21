package com.hackinglab.toki.data

import java.time.*

/** Date-only annual event, with reminders interpreted in the device's current zone. */
data class BirthdaySpec(val month: Int, val day: Int, val birthYear: Int = 0,
    val hour: Int = 9, val minute: Int = 0, val leapDayPolicy: String = "feb28") {
    fun validate(today: LocalDate = LocalDate.now()) {
        require(runCatching { MonthDay.of(month, day) }.isSuccess) { "Choose a valid birthday date." }
        require(birthYear == 0 || birthYear in 1..today.year) { "Enter a birth year no later than this year, or leave it empty." }
        require(birthYear == 0 || runCatching { LocalDate.of(birthYear, month, day) }.isSuccess) { "That birth date does not exist in the selected year." }
        require(hour in 0..23 && minute in 0..59) { "Choose a valid reminder time." }
        require(leapDayPolicy in setOf("feb28", "mar1")) { "Choose a February 29 policy." }
    }
    fun date(year: Int): LocalDate = if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) {
        if (leapDayPolicy == "mar1") LocalDate.of(year, 3, 1) else LocalDate.of(year, 2, 28)
    } else LocalDate.of(year, month, day)
    fun next(after: Long, byDeadline: Boolean, zone: ZoneId = ZoneId.systemDefault()): OccurrencePlanner.Occurrence? {
        val current = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        validate(current)
        for (year in current.year..(current.year + 2)) {
            val date = date(year)
            val reminder = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
            val until = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if ((if (byDeadline) until else reminder) > after) {
                // atZone shifts a nonexistent reminder time forward by the DST gap.
                return OccurrencePlanner.Occurrence(reminder, reminder, until)
            }
        }
        return null
    }
    fun ageOn(occurrence: Long, zone: ZoneId = ZoneId.systemDefault()): Int? =
        if (birthYear == 0) null else (Instant.ofEpochMilli(occurrence).atZone(zone).year - birthYear).takeIf { it >= 0 }
    fun deadline(name: String, description: String, enabled: Boolean): Deadline {
        validate()
        require(name.isNotBlank() && name.trim().length <= 120) { "Enter a name up to 120 characters." }
        return Deadline().also {
            it.kind = "birthday"; it.title = "${name.trim()}’s birthday"; it.description = description
            it.birthdayMonth = month; it.birthdayDay = day; it.birthYear = birthYear
            it.birthdayHour = hour; it.birthdayMinute = minute; it.leapDayPolicy = leapDayPolicy
            it.enabled = enabled; it.allDay = true; it.recurrenceLoaded = true
            it.timezone = ZoneId.systemDefault().id
            it.startMillis = requireNotNull(next(System.currentTimeMillis(), true)).startMillis
        }
    }
    companion object {
        /** European day.month[.year], independent of the device's locale. */
        fun parseEuropean(value: String, hour: Int = 9, minute: Int = 0, leapDayPolicy: String = "feb28", today: LocalDate = LocalDate.now()): BirthdaySpec {
            val text = value.trim().removeSuffix(".")
            val match = Regex("""([0-9]{1,2})\.([0-9]{1,2})(?:\.([0-9]{4}))?""").matchEntire(text)
                ?: throw IllegalArgumentException("Enter day.month.year, for example 8.10.1968, or day.month if the year is unknown.")
            val yearText = match.groupValues[3]
            val year = if (yearText.isEmpty()) 0 else yearText.toInt().also { require(it > 0) { "Enter a valid four-digit birth year." } }
            return BirthdaySpec(match.groupValues[2].toInt(), match.groupValues[1].toInt(), year, hour, minute, leapDayPolicy).also { it.validate(today) }
        }
        fun from(event: Deadline) = BirthdaySpec(event.birthdayMonth, event.birthdayDay, event.birthYear,
            event.birthdayHour, event.birthdayMinute, event.leapDayPolicy)
    }
}
