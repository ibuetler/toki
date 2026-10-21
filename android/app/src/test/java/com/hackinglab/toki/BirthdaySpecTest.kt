package com.hackinglab.toki

import com.hackinglab.toki.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class BirthdaySpecTest {
    private val zurich = ZoneId.of("Europe/Zurich")
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    @Test fun europeanDateIsDayFirstAndYearOptional() {
        val today = LocalDate.of(2026, 10, 21)
        assertEquals(BirthdaySpec(10, 8, 1968), BirthdaySpec.parseEuropean("8.10.1968", today = today))
        assertEquals(BirthdaySpec(10, 8, 1968), BirthdaySpec.parseEuropean("08.10.1968", today = today))
        assertEquals(BirthdaySpec(10, 8), BirthdaySpec.parseEuropean("8.10", today = today))
        assertEquals(BirthdaySpec(2, 29), BirthdaySpec.parseEuropean("29.2.", today = today))
    }
    @Test fun europeanDateRejectsInvalidOrAmbiguousInput() {
        for (text in listOf("31.4.1968", "29.2.1969", "8.10.68", "10/8/1968", "8.10.0000", "1.1.9999", "", "8.13"))
            assertThrows(IllegalArgumentException::class.java) { BirthdaySpec.parseEuropean(text, today = LocalDate.of(2026, 10, 21)) }
    }
    @Test fun birthdayReminderIsAnnualAtNineLocal() {
        val spec = BirthdaySpec(10, 30)
        assertEquals(at("2026-10-30T08:00:00Z"), spec.next(at("2026-10-21T00:00:00Z"), false, zurich)!!.reminderMillis)
        assertEquals(at("2027-10-30T07:00:00Z"), spec.next(at("2026-10-30T08:00:00Z"), false, zurich)!!.reminderMillis)
    }
    @Test fun birthdayStaysVisibleAllDayAfterReminder() {
        val spec = BirthdaySpec(10, 30)
        val occurrence = spec.next(at("2026-10-30T15:00:00Z"), true, zurich)!!
        assertEquals(at("2026-10-30T08:00:00Z"), occurrence.startMillis)
        assertEquals(at("2026-10-30T23:00:00Z"), occurrence.visibleUntilMillis)
        assertEquals(at("2027-10-30T07:00:00Z"), spec.next(occurrence.visibleUntilMillis, true, zurich)!!.startMillis)
    }
    @Test fun february29PoliciesAndLeapYears() {
        assertEquals(LocalDate.of(2027, 2, 28), BirthdaySpec(2, 29).date(2027))
        assertEquals(LocalDate.of(2027, 3, 1), BirthdaySpec(2, 29, leapDayPolicy = "mar1").date(2027))
        assertEquals(LocalDate.of(2028, 2, 29), BirthdaySpec(2, 29).date(2028))
        assertEquals(LocalDate.of(2028, 2, 29), BirthdaySpec(2, 29, leapDayPolicy = "mar1").date(2028))
    }
    @Test fun deviceZoneChangesReminderInstant() {
        val spec = BirthdaySpec(10, 30)
        val now = at("2026-10-21T00:00:00Z")
        assertEquals(at("2026-10-30T00:00:00Z"), spec.next(now, false, ZoneId.of("Asia/Tokyo"))!!.startMillis)
        assertEquals(at("2026-10-30T08:00:00Z"), spec.next(now, false, zurich)!!.startMillis)
    }
    @Test fun dstGapShiftsReminderForwardAndOverlapRingsOnce() {
        val spring = BirthdaySpec(3, 28, hour = 2, minute = 30)
        assertEquals(at("2027-03-28T01:30:00Z"), spring.next(at("2027-03-27T00:00:00Z"), false, zurich)!!.startMillis)
        val autumn = BirthdaySpec(10, 25, hour = 2, minute = 30)
        val first = autumn.next(at("2026-10-24T00:00:00Z"), false, zurich)!!
        assertEquals(at("2026-10-25T00:30:00Z"), first.startMillis)
        assertTrue(autumn.next(first.startMillis, false, zurich)!!.startMillis > at("2027-01-01T00:00:00Z"))
    }
    @Test fun birthYearIsOptionalAndAgeUsesObservedYear() {
        assertNull(BirthdaySpec(2, 29).ageOn(at("2027-02-28T08:00:00Z"), zurich))
        assertEquals(27, BirthdaySpec(2, 29, 2000).ageOn(at("2027-02-28T08:00:00Z"), zurich))
    }
    @Test fun invalidCalendarDatesAndBirthYearsAreRejected() {
        for (spec in listOf(BirthdaySpec(4, 31), BirthdaySpec(2, 29, 2001), BirthdaySpec(1, 1, 9999), BirthdaySpec(1, 1, hour = 24))) {
            assertThrows(IllegalArgumentException::class.java) { spec.validate(LocalDate.of(2026, 10, 21)) }
        }
    }
    @Test fun plannerUsesBirthdayPolicyWithoutEnablingOtherAllDayEvents() {
        val event = Deadline().apply { kind = "birthday"; allDay = true; birthdayMonth = 10; birthdayDay = 30 }
        assertNotNull(OccurrencePlanner.next(event, at("2026-10-21T00:00:00Z")))
        event.kind = "deadline"
        assertThrows(IllegalArgumentException::class.java) { OccurrencePlanner.next(event, at("2026-10-21T00:00:00Z")) }
    }
}
