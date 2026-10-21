package com.hackinglab.toki.data

import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.time.*
import java.util.TimeZone

/** Timed recurrence contract: event-zone wall time, gap skipped, overlap's first offset. */
object OccurrencePlanner {
    data class Occurrence(val startMillis: Long, val reminderMillis: Long, val visibleUntilMillis: Long = startMillis)
    fun next(event: Deadline, afterMillis: Long): Occurrence? = nextOccurrence(event, afterMillis, false)
    /** Agenda visibility follows the deadline, independently of reminder delivery. */
    fun nextEvent(event: Deadline, afterMillis: Long): Occurrence? = nextOccurrence(event, afterMillis, true)
    private fun nextOccurrence(event: Deadline, afterMillis: Long, byDeadline: Boolean): Occurrence? {
        if (event.kind == "birthday") return BirthdaySpec.from(event).next(afterMillis, byDeadline)
        require(event.rrule.isBlank() || event.recurrenceLoaded) { "Sync once to load recurrence exceptions." }
        require(!event.allDay) { "All-day reminder time is not configured yet." }
        require(event.reminderMinutes in 0..525600) { "Invalid reminder offset." }
        val zone = ZoneId.of(event.timezone)
        val threshold = Math.addExact(afterMillis, if (byDeadline) 0L else event.reminderMinutes.toLong() * 60_000)
        val excluded = dates(event.exdates, zone).toSet()
        val candidates = dates(event.rdates, zone).filter { it > threshold && it !in excluded }.toMutableList()
        // DTSTART belongs to the recurrence set even if BY* doesn't match it.
        if (event.startMillis > threshold && event.startMillis !in excluded) candidates += event.startMillis
        if (event.rrule.isNotBlank()) {
            require(event.startMillis % 1000 == 0L) { "Recurring start time must use whole seconds." }
            require(event.rrule.length <= 1024) { "Recurrence rule is too long." }
            val text = event.rrule.removePrefix("RRULE:")
            val parts = text.split(';').associate { val pair = it.split('=', limit = 2); require(pair.size == 2); pair[0] to pair[1] }
            require(parts["FREQ"] in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) { "Use a daily, weekly, monthly, or yearly rule." }
            require(parts["RSCALE"] == null || parts["RSCALE"] == "GREGORIAN") { "Only Gregorian recurrence is supported." }
            for (key in listOf("BYHOUR", "BYMINUTE", "BYSECOND")) require(parts[key]?.contains(',') != true) { "Multiple times per day are not supported." }
            val validated = RecurrenceRule(text, RecurrenceRule.RfcMode.RFC5545_STRICT)
            val count = parts["COUNT"]?.toInt()?.also { require(it in 1..10000) { "COUNT must be between 1 and 10000." } }
            // Invalid local DST times do not count toward RFC COUNT. Iterate a
            // count-less rule and count valid wall times ourselves in that case.
            val rule = if (count == null) validated else RecurrenceRule(
                text.split(';').filterNot { it.startsWith("COUNT=") }.joinToString(";"), RecurrenceRule.RfcMode.RFC5545_STRICT)
            val iterator = rule.iterator(DateTime(TimeZone.getTimeZone(zone), event.startMillis))
            // Back up over a possible DST overlap before filtering by the actual instant.
            if (count == null) iterator.fastForward(threshold - 86_400_000)
            var scanned = 0
            var validInstances = 0
            while (iterator.hasNext()) {
                require(++scanned <= (count ?: 0) + 4096) { "Too many excluded occurrences." }
                val date = iterator.nextDateTime()
                val wall = LocalDateTime.of(date.year, date.month + 1, date.dayOfMonth, date.hours, date.minutes, date.seconds)
                val instant = resolve(wall, zone) ?: continue
                if (count != null && ++validInstances > count) break
                if (instant > threshold && instant !in excluded) { candidates += instant; break }
            }
        }
        val start = candidates.minOrNull() ?: return null
        return Occurrence(start, EventTime.reminderAt(start, event.reminderMinutes))
    }
    private fun dates(value: String, zone: ZoneId): List<Long> {
        val entries = value.lineSequence().filter { it.isNotBlank() }.toList()
        require(entries.size <= 1000) { "Too many recurrence exceptions." }
        return entries.mapNotNull { text ->
            // Offset/Z values are absolute; unqualified ISO strings use the event zone.
            runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
                .getOrElse { resolve(LocalDateTime.parse(text), zone) }
        }
    }
    private fun resolve(wall: LocalDateTime, zone: ZoneId): Long? {
        val offsets = zone.rules.getValidOffsets(wall)
        return offsets.firstOrNull()?.let { wall.toInstant(it).toEpochMilli() }
    }
}
