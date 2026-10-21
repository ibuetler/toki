package com.hackinglab.toki

import com.hackinglab.toki.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

class TaskTransportTest {
    private class Connection(val response: String, val status: Int = 200) : HttpURLConnection(URL("https://example.invalid")) {
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
    private val response = """{"id":"abcdefghijklmno","title":"A thought","notes":"","priority":"normal","completed":false,"remind_at":"","created":"2026-10-21 10:00:00.000Z"}"""
    @Test fun createIncludesOwnerAndNormalPriorityWithoutAlarm() {
        val connection = Connection(response)
        var path = ""
        val task = PocketBase { path = it; connection }.saveTask(session, TaskDraft("A thought"))
        val body = JSONObject(connection.body.toString("UTF-8"))
        assertEquals("POST", connection.requestMethod); assertEquals("/api/collections/tasks/records", path)
        assertEquals("user", body.getString("owner")); assertEquals("normal", body.getString("priority"))
        assertEquals("", body.getString("remind_at")); assertEquals(0L, task.reminderMillis)
    }
    @Test fun editCannotTransferOwnerOrChangeCompletionAndCanClearReminder() {
        val connection = Connection(response)
        PocketBase { connection }.saveTask(session, TaskDraft("A thought", priority = "urgent"), "abcdefghijklmno")
        val body = JSONObject(connection.body.toString("UTF-8"))
        assertEquals("PATCH", connection.requestMethod); assertFalse(body.has("owner")); assertFalse(body.has("completed"))
        assertEquals("urgent", body.getString("priority")); assertEquals("", body.getString("remind_at"))
    }
    @Test fun completionAndRestoreOnlyPatchCompletion() {
        for (completed in listOf(true, false)) {
            val connection = Connection(response)
            PocketBase { connection }.completeTask(session, "abcdefghijklmno", completed)
            val body = JSONObject(connection.body.toString("UTF-8"))
            assertEquals("PATCH", connection.requestMethod); assertEquals(1, body.length()); assertEquals(completed, body.getBoolean("completed"))
        }
    }
    @Test fun deleteHandlesEmpty204Response() {
        val connection = Connection("", 204)
        PocketBase { connection }.deleteTask(session, "abcdefghijklmno")
        assertEquals("DELETE", connection.requestMethod)
    }
    @Test fun missingTasksCollectionAllowsOlderBackends() {
        val api = PocketBase { path -> if (path.contains("/tasks/")) Connection("", 404) else Connection("""{"items":[],"totalPages":1}""") }
        val snapshot = api.deadlines(session)
        assertFalse(snapshot.tasksAvailable); assertTrue(snapshot.tasks.isEmpty())
    }
    @Test fun taskServerFailureAbortsSnapshotRatherThanDroppingTasks() {
        val api = PocketBase { path -> if (path.contains("/tasks/")) Connection("", 500) else Connection("""{"items":[],"totalPages":1}""") }
        try { api.deadlines(session); fail("Snapshot must fail") } catch (error: ApiFailure) { assertEquals(500, error.status) }
    }
}
