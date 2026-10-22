package com.hackinglab.toki.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

class ApiFailure(val status: Int) : Exception(
    if (status == 400) "Check your email and password."
    else if (status == 401 || status == 403) "Your session is no longer valid. Sign in again."
    else "The server could not complete the request (HTTP $status)."
)

object EventTime {
    fun parse(value: String): Long = Instant.parse(value.replace(' ', 'T')).toEpochMilli()
    fun reminderAt(start: Long, minutes: Int): Long = Math.subtractExact(start, minutes.toLong() * 60_000)
}

data class DeadlineSnapshot(val records: List<Deadline>, val deletableIds: Set<String>, val birthdaysAvailable: Boolean, val tasks: List<TokiTask> = emptyList(), val tasksAvailable: Boolean = false)

class PocketBase(internal val connect: (String) -> HttpURLConnection = { URI(BASE + it).toURL().openConnection() as HttpURLConnection }) {
    companion object { const val BASE = "https://pocketbase.hacking-lab.com" }
    private fun request(path: String, token: String? = null, payload: JSONObject? = null, method: String? = null): JSONObject {
        val connection = connect(path)
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method ?: if (payload != null) "POST" else "GET"
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw ApiFailure(status)
            if (status == 204) return JSONObject()
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }
    fun login(email: String, password: String): Session {
        val json = request("/api/collections/users/auth-with-password", payload = JSONObject()
            .put("identity", email).put("password", password))
        val user = json.getJSONObject("record")
        return Session(json.getString("token"), user.getString("id"), user.getString("email"))
    }
    fun refresh(session: Session): Session {
        val response = try { request("/api/collections/users/auth-refresh", session.token, JSONObject()) }
            catch (error: ApiFailure) { if (error.status == 400) throw ApiFailure(401) else throw error }
        return session.copy(token = response.getString("token"))
    }
    private fun all(collection: String, token: String): List<JSONObject> {
        val records = mutableListOf<JSONObject>()
        var page = 1
        do {
            val response = request("/api/collections/$collection/records?page=$page&perPage=200", token)
            val items = response.getJSONArray("items")
            for (i in 0 until items.length()) records += items.getJSONObject(i)
            val pages = response.getInt("totalPages")
            page++
        } while (page <= pages)
        return records
    }
    private fun dates(json: JSONObject, field: String): String {
        if (json.isNull(field)) return ""
        val array = json.getJSONArray(field)
        require(array.length() <= 1000)
        return (0 until array.length()).joinToString("\n") {
            array.getString(it).also { value -> require(!value.contains('\n') && !value.contains('\r')) }
        }
    }
    fun createDeadline(session: Session, draft: NewEvent) {
        draft.birthday?.let { birthday ->
            birthday.validate()
            request("/api/collections/birthdays/records", session.token, JSONObject()
                .put("owner", session.userId).put("name", draft.title.trim()).put("description", draft.description)
                .put("month", birthday.month).put("day", birthday.day).put("birth_year", birthday.birthYear)
                .put("reminder_hour", birthday.hour).put("reminder_minute", birthday.minute)
                .put("leap_day_policy", birthday.leapDayPolicy).put("enabled", draft.enabled))
            return
        }
        val event = draft.deadline()
        request("/api/collections/deadlines/records", session.token, JSONObject()
            .put("owner", session.userId).put("group", "")
            .put("title", event.title).put("description", event.description)
            .put("start_at", Instant.ofEpochMilli(event.startMillis).toString())
            .put("timezone", event.timezone).put("enabled", event.enabled)
            .put("all_day", false).put("duration_minutes", draft.durationMinutes)
            .put("reminder_minutes", event.reminderMinutes).put("rrule", event.rrule))
    }
    private fun recordPath(id: String): String {
        val recordId = id.removePrefix("birthday:")
        require(recordId.matches(Regex("[a-zA-Z0-9]{15}"))) { "Invalid event identifier." }
        return "/api/collections/${if (id.startsWith("birthday:")) "birthdays" else "deadlines"}/records/$recordId"
    }
    fun editableEvent(session: Session, id: String): EditableEvent {
        val json = request(recordPath(id), session.token)
        val birthday = if (id.startsWith("birthday:")) BirthdaySpec(json.getInt("month"), json.getInt("day"),
            json.optInt("birth_year"), json.getInt("reminder_hour"), json.getInt("reminder_minute"), json.getString("leap_day_policy")) else null
        val zone = if (birthday != null) java.time.ZoneId.systemDefault() else java.time.ZoneId.of(json.getString("timezone"))
        val start = if (birthday != null) java.time.LocalDateTime.now(zone) else
            Instant.ofEpochMilli(EventTime.parse(json.getString("start_at"))).atZone(zone).toLocalDateTime()
        return EditableEvent(id, NewEvent(json.getString(if (birthday != null) "name" else "title"),
            json.optString("description"), start, zone.id, json.optInt("reminder_minutes", 5),
            json.optInt("duration_minutes", 60), json.getBoolean("enabled"), json.optString("rrule"), birthday), json.optString("group").isNotBlank())
    }
    fun updateEvent(session: Session, edit: EditableEvent, draft: NewEvent) {
        val changes = edit.changes(draft)
        if (changes.isNotEmpty()) request(recordPath(edit.id), session.token, JSONObject(changes), "PATCH")
    }
    fun deleteDeadline(session: Session, id: String) {
        val birthday = id.startsWith("birthday:")
        val recordId = if (birthday) id.removePrefix("birthday:") else id
        require(recordId.matches(Regex("[a-zA-Z0-9]{15}"))) { "Invalid event identifier." }
        val collection = if (birthday) "birthdays" else "deadlines"
        request("/api/collections/$collection/records/$recordId", session.token, method = "DELETE")
    }
    private fun taskPath(id: String = ""): String {
        require(id.isEmpty() || id.matches(Regex("[a-zA-Z0-9]{15}"))) { "Invalid task identifier." }
        return "/api/collections/tasks/records" + if (id.isEmpty()) "" else "/$id"
    }
    private fun task(json: JSONObject) = TokiTask().apply {
        id = json.getString("id"); title = json.getString("title"); notes = json.optString("notes")
        priority = json.getString("priority"); completed = json.getBoolean("completed")
        reminderMillis = json.optString("remind_at").takeIf { it.isNotBlank() }?.let(EventTime::parse) ?: 0
        createdMillis = EventTime.parse(json.getString("created"))
    }
    fun saveTask(session: Session, draft: TaskDraft, id: String? = null): TokiTask {
        draft.validate()
        val payload = JSONObject().put("title", draft.title.trim()).put("notes", draft.notes).put("priority", draft.priority)
            .put("remind_at", if (draft.reminderMillis == 0L) "" else Instant.ofEpochMilli(draft.reminderMillis).toString())
        if (id == null) payload.put("owner", session.userId).put("completed", false)
        return task(request(taskPath(id.orEmpty()), session.token, payload, if (id == null) "POST" else "PATCH"))
    }
    fun completeTask(session: Session, id: String, completed: Boolean): TokiTask =
        task(request(taskPath(id), session.token, JSONObject().put("completed", completed), "PATCH"))
    fun deleteTask(session: Session, id: String) { request(taskPath(id), session.token, method = "DELETE") }
    fun deadlines(session: Session): DeadlineSnapshot {
        val groupRecords = all("groups", session.token)
        val groups = groupRecords.associate { it.getString("id") to it.getString("name") }
        val adminGroups = groupRecords.filter { it.optString("owner") == session.userId }.map { it.getString("id") }.toMutableSet()
        all("group_members", session.token).filter { it.optString("user") == session.userId && it.optString("role") == "admin" }
            .forEach { adminGroups += it.getString("group") }
        val deletableIds = mutableSetOf<String>()
        val preferences = all("deadline_preferences", session.token)
            .filter { it.getString("user") == session.userId }.associateBy { it.getString("deadline") }
        val records = all("deadlines", session.token).map { json ->
            if (DeletePermission.allowed(session.userId, json.optString("owner"), json.optString("group"), adminGroups))
                deletableIds += json.getString("id")
            val preference = preferences[json.getString("id")]
            Deadline().apply {
                id = json.getString("id")
                title = json.getString("title")
                description = json.optString("description")
                groupName = groups[json.optString("group")] ?: ""
                timezone = json.getString("timezone")
                rrule = json.optString("rrule")
                exdates = dates(json, "exdates")
                rdates = dates(json, "rdates")
                recurrenceLoaded = true
                allDay = json.optBoolean("all_day")
                startMillis = EventTime.parse(json.getString("start_at"))
                reminderMinutes = (preference?.getInt("reminder_minutes") ?: json.getInt("reminder_minutes"))
                    .also { require(it in 0..525600) }
                enabled = json.getBoolean("enabled") && (preference?.getBoolean("enabled") ?: true)
            }
        }
        var birthdaysAvailable = true
        val birthdays = try { all("birthdays", session.token) } catch (error: ApiFailure) {
            if (error.status != 404) throw error
            birthdaysAvailable = false
            emptyList()
        }
        val birthdayRecords = birthdays.map { json ->
            Deadline().apply {
                kind = "birthday"; id = "birthday:" + json.getString("id")
                title = json.getString("name") + "’s birthday"; description = json.optString("description")
                birthdayMonth = json.getInt("month"); birthdayDay = json.getInt("day"); birthYear = json.optInt("birth_year")
                birthdayHour = json.getInt("reminder_hour"); birthdayMinute = json.getInt("reminder_minute")
                leapDayPolicy = json.getString("leap_day_policy"); enabled = json.getBoolean("enabled")
                timezone = java.time.ZoneId.systemDefault().id; allDay = true; recurrenceLoaded = true
                startMillis = runCatching { BirthdaySpec.from(this).next(System.currentTimeMillis(), true)?.startMillis ?: 0 }.getOrDefault(0)
                if (json.getString("owner") == session.userId) deletableIds += id
            }
        }
        var tasksAvailable = true
        val tasks = try { all("tasks", session.token).map(::task) } catch (error: ApiFailure) {
            if (error.status != 404) throw error
            tasksAvailable = false
            emptyList()
        }
        val calendars = try { all("calendar_events", session.token) } catch (error: ApiFailure) {
            if (error.status != 404) throw error
            emptyList()
        }
        val imported = calendars.map { json ->
            Deadline().apply {
                kind = "calendar"; id = "calendar:" + json.getString("id")
                title = json.getString("title")
                sourceNames = json.getString("source_names")
                description = listOf(json.optString("description"), json.optString("location").takeIf { it.isNotBlank() }?.let { "Location: $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
                timezone = json.getString("timezone")
                startMillis = EventTime.parse(json.getString("start_at"))
                endMillis = EventTime.parse(json.getString("end_at"))
                allDay = json.getBoolean("all_day")
                reminderMinutes = 5; enabled = !allDay; recurrenceLoaded = true
            }
        }
        return DeadlineSnapshot(records + birthdayRecords + imported, deletableIds, birthdaysAvailable, tasks, tasksAvailable)
    }
}
