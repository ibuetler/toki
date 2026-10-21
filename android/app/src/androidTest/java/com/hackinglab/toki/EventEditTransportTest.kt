package com.hackinglab.toki

import com.hackinglab.toki.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

/** In-memory HTTP transport: no credentials, network, or real records. */
class EventEditTransportTest {
    private class Connection(val response: String = "{}", val status: Int = 200) : HttpURLConnection(URL("https://example.invalid")) {
        val body = ByteArrayOutputStream()
        override fun setRequestMethod(value: String) { method = value }
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun connect() {}
        override fun getOutputStream() = body
        override fun getInputStream() = response.byteInputStream()
        override fun getResponseCode() = status
    }
    private val session = Session("fake", "user", "preview@example.com")
    @Test fun androidHttpsConnectionAcceptsPatch() {
        // Android's concrete HTTPS implementation supports PATCH, unlike the abstract test stub.
        val connection = java.net.URI(PocketBase.BASE).toURL().openConnection() as HttpURLConnection
        try { connection.requestMethod = "PATCH"; assertEquals("PATCH", connection.requestMethod) }
        finally { connection.disconnect() }
    }
    @Test fun patchUsesCorrectMethodAndOnlyChangedFields() {
        val connection = Connection()
        var path = ""
        val api = PocketBase { path = it; connection }
        val draft = NewEvent("Old", "", LocalDateTime.of(2026, 12, 20, 16, 30), "Europe/Zurich")
        api.updateEvent(session, EditableEvent("abcdefghijklmno", draft), draft.copy(title = "New"))
        assertEquals("PATCH", connection.requestMethod)
        assertEquals("/api/collections/deadlines/records/abcdefghijklmno", path)
        val payload = JSONObject(connection.body.toString("UTF-8"))
        assertEquals(1, payload.length()); assertEquals("New", payload.getString("title"))
    }
    @Test fun birthdayPatchRoutesToBirthdays() {
        val connection = Connection()
        var path = ""
        val api = PocketBase { path = it; connection }
        val draft = NewEvent("Alex", "", LocalDateTime.now(), birthday = BirthdaySpec(10, 8, 1968))
        api.updateEvent(session, EditableEvent("birthday:abcdefghijklmno", draft), draft.copy(birthday = BirthdaySpec(10, 8, 1970)))
        assertEquals("PATCH", connection.requestMethod)
        assertEquals("/api/collections/birthdays/records/abcdefghijklmno", path)
        assertEquals(1970, JSONObject(connection.body.toString("UTF-8")).getInt("birth_year"))
    }
    @Test fun loadUsesServerDefaultsRatherThanCachedPreferences() {
        val connection = Connection("""{"title":"Server event","description":"Notes","start_at":"2026-12-20 15:30:00.123Z","timezone":"Europe/Zurich","reminder_minutes":45,"duration_minutes":90,"enabled":true,"rrule":"FREQ=WEEKLY;BYDAY=MO,FR","group":"family"}""")
        val edit = PocketBase { connection }.editableEvent(session, "abcdefghijklmno")
        assertEquals("GET", connection.requestMethod)
        assertEquals(45, edit.original.reminderMinutes); assertEquals(90, edit.original.durationMinutes)
        assertTrue(edit.shared); assertTrue(edit.original.enabled)
        assertFalse(edit.changes(edit.original.copy(description = "Changed")).containsKey("start_at"))
    }
    @Test fun serverRejectsPatchWithoutReportingSuccess() {
        val connection = Connection(status = 404)
        val draft = NewEvent("Old", "", LocalDateTime.now())
        try {
            PocketBase { connection }.updateEvent(session, EditableEvent("abcdefghijklmno", draft), draft.copy(title = "New"))
            fail("Expected rejected update")
        } catch (error: ApiFailure) { assertEquals(404, error.status) }
    }
}
