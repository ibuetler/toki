package com.hackinglab.toki

import com.hackinglab.toki.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class CalendarTransportTest {
    private fun api(calendarStatus: Int = 200): PocketBase = PocketBase { path ->
        val calendar = path.contains("/calendar_events/")
        val second = path.contains("page=2&")
        val row = JSONObject().put("id", if (second) "second" else "first")
            .put("title", "Shared meeting").put("description", "Notes").put("location", "Room A")
            .put("source_names", "Compass Security · OST").put("timezone", "UTC")
            .put("start_at", "2026-10-22 10:00:00.000Z").put("end_at", "2026-10-22 11:00:00.000Z")
            .put("all_day", second)
        val body = JSONObject().put("totalPages", if (calendar) 2 else 1)
            .put("items", if (calendar) JSONArray().put(row) else JSONArray()).toString()
        object : HttpURLConnection(URL("https://example.invalid")) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = if (calendar) calendarStatus else 200
            override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        }
    }
    @Test fun importedPagesMapToReadOnlyEventsWithSourcesAndFixedReminders() {
        val result = api().deadlines(Session("token", "owner", "owner@example.invalid"))
        assertEquals(2, result.records.size)
        val timed = result.records.first()
        assertEquals("calendar:first", timed.id)
        assertEquals("calendar", timed.kind)
        assertEquals("Compass Security · OST", timed.sourceNames)
        assertTrue(timed.description.contains("Room A"))
        assertEquals(5, timed.reminderMinutes)
        assertTrue(timed.enabled)
        assertFalse(result.records.last().enabled)
        assertTrue(result.records.last().allDay)
        assertTrue(result.deletableIds.isEmpty())
    }
    @Test fun oldBackendWithoutCalendarsStillSyncs() {
        assertTrue(api(404).deadlines(Session("token", "owner", "owner@example.invalid")).records.isEmpty())
    }
    @Test fun calendarFailureAbortsSnapshotInsteadOfErasingImports() {
        try {
            api(503).deadlines(Session("token", "owner", "owner@example.invalid"))
            fail("Expected a failed snapshot")
        } catch (error: ApiFailure) { assertEquals(503, error.status) }
    }
}
