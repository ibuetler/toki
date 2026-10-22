package com.hackinglab.toki

import com.hackinglab.toki.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class OccurrencePlannerTest {
    private fun millis(text: String) = Instant.parse(text).toEpochMilli()
    private fun event(start: String, rule: String = "", zone: String = "Europe/Zurich") = Deadline().apply {
        recurrenceLoaded = true; id = "fixture"; startMillis = millis(start); timezone = zone; rrule = rule; reminderMinutes = 5; enabled = true
    }
    private fun upcoming(event: Deadline, after: Long, count: Int): List<String> {
        var cursor = after
        return buildList {
            repeat(count) {
                val next = OccurrencePlanner.next(event, cursor) ?: return@buildList
                add(Instant.ofEpochMilli(next.startMillis).toString())
                cursor = next.reminderMillis
            }
        }
    }
    @Test fun importedAllDayVisibleUntilEndWithoutAlarm() {
        val e = event("2026-10-21T22:00:00Z").apply {
            kind = "calendar"; allDay = true; enabled = false
            endMillis = millis("2026-10-23T22:00:00Z")
        }
        assertNull(OccurrencePlanner.next(e, millis("2026-10-20T00:00:00Z")))
        assertEquals(e.endMillis, OccurrencePlanner.nextEvent(e, millis("2026-10-23T12:00:00Z"))!!.visibleUntilMillis)
        assertNull(OccurrencePlanner.nextEvent(e, e.endMillis))
    }
    @Test fun importedMeetingUsesFiveMinuteAlarm() {
        val e = event("2026-10-22T10:00:00Z").apply { kind = "calendar" }
        assertEquals(millis("2026-10-22T09:55:00Z"), OccurrencePlanner.next(e, millis("2026-10-22T09:00:00Z"))!!.reminderMillis)
    }
    @Test fun agendaKeepsOneOffAfterReminderUntilDeadline() {
        val e = event("2026-10-20T14:30:00Z").apply { reminderMinutes = 30 }
        val afterAlarm = millis("2026-10-20T14:01:00Z")
        assertNull(OccurrencePlanner.next(e, afterAlarm))
        assertEquals(e.startMillis, OccurrencePlanner.nextEvent(e, afterAlarm)!!.startMillis)
        assertNull(OccurrencePlanner.nextEvent(e, e.startMillis))
    }
    @Test fun recurringAgendaKeepsCurrentDeadlineWhileAlarmAdvances() {
        val e = event("2026-10-20T14:30:00Z", "FREQ=WEEKLY").apply { reminderMinutes = 30 }
        val afterAlarm = millis("2026-10-20T14:01:00Z")
        assertEquals(e.startMillis, OccurrencePlanner.nextEvent(e, afterAlarm)!!.startMillis)
        val nextWeek = millis("2026-10-27T15:30:00Z") // Zurich DST ends between occurrences.
        assertEquals(nextWeek, OccurrencePlanner.next(e, afterAlarm)!!.startMillis)
        assertEquals(nextWeek, OccurrencePlanner.nextEvent(e, e.startMillis)!!.startMillis)
    }
    @Test fun sharedPythonAndKotlinFixtures() {
        val stream = javaClass.classLoader!!.getResourceAsStream("recurrence.tsv")!!
        stream.bufferedReader().useLines { lines -> lines.drop(1).forEach { line ->
            val c = line.split('\t')
            val e = event(c[1], c[3], c[2]).apply { exdates = c[4].replace(',', '\n'); rdates = c[5].replace(',', '\n') }
            val expected = c[7].split(',')
            assertEquals(c[0], expected, upcoming(e, millis(c[6]), expected.size + 1))
        } }
    }
    @Test fun nextReminderAfterDeliveryAdvancesOffline() {
        val e = event("2026-10-26T08:00:00Z", "FREQ=WEEKLY")
        val first = OccurrencePlanner.next(e, millis("2026-10-25T00:00:00Z"))!!
        val second = OccurrencePlanner.next(e, first.reminderMillis + 200)!!
        assertEquals("2026-11-02T08:00:00Z", Instant.ofEpochMilli(second.startMillis).toString())
    }
    @Test fun missedReminderDoesNotSchedulePastOccurrence() {
        val e = event("2026-10-26T08:00:00Z", "FREQ=WEEKLY")
        assertEquals(listOf("2026-11-02T08:00:00Z"), upcoming(e, millis("2026-10-26T07:56:00Z"), 1))
    }
    @Test fun springGapSkippedWithoutUsingUpCount() {
        val e = event("2027-03-21T01:30:00Z", "FREQ=WEEKLY;COUNT=3")
        assertEquals(listOf("2027-03-21T01:30:00Z", "2027-04-04T00:30:00Z", "2027-04-11T00:30:00Z"),
            upcoming(e, millis("2027-03-20T00:00:00Z"), 4))
    }
    @Test fun overlapUsesEarlierOffsetOnce() {
        val e = event("2026-10-18T00:30:00Z", "FREQ=WEEKLY;COUNT=3")
        assertEquals(listOf("2026-10-18T00:30:00Z", "2026-10-25T00:30:00Z", "2026-11-01T01:30:00Z"),
            upcoming(e, millis("2026-10-17T00:00:00Z"), 4))
    }
    @Test fun invalidRuleDoesNotBecomeOneOff() {
        assertThrows(Exception::class.java) { OccurrencePlanner.next(event("2026-10-26T08:00:00Z", "FREQ=NOPE"), 0) }
        assertThrows(Exception::class.java) { OccurrencePlanner.next(event("2026-10-26T08:00:00Z").apply { timezone = "Unknown/Zone" }, 0) }
    }
    @Test fun allDayRequiresPolicy() {
        assertThrows(IllegalArgumentException::class.java) { OccurrencePlanner.next(event("2026-10-26T08:00:00Z").apply { allDay = true }, 0) }
    }
    @Test fun migratedRecurringCacheRequiresSyncBeforeScheduling() {
        val e = event("2026-10-26T08:00:00Z", "FREQ=WEEKLY").apply { recurrenceLoaded = false }
        assertThrows(IllegalArgumentException::class.java) { OccurrencePlanner.next(e, 0) }
    }
    @Test fun oneOffCanHaveAdditionalDatesAndExceptions() {
        val e = event("2026-10-26T08:00:00Z").apply { exdates = "2026-10-26T09:00:00"; rdates = "2026-10-27T09:00:00" }
        assertEquals(listOf("2026-10-27T08:00:00Z"), upcoming(e, millis("2026-10-25T00:00:00Z"), 2))
    }
}
