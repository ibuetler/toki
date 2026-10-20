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

class PocketBase {
    companion object { const val BASE = "https://pocketbase.hacking-lab.com" }
    private fun request(path: String, token: String? = null, payload: JSONObject? = null): JSONObject {
        val connection = URI(BASE + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (payload != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw ApiFailure(status)
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
    fun deadlines(session: Session): List<Deadline> {
        val groups = all("groups", session.token).associate { it.getString("id") to it.getString("name") }
        val preferences = all("deadline_preferences", session.token)
            .filter { it.getString("user") == session.userId }.associateBy { it.getString("deadline") }
        return all("deadlines", session.token).map { json ->
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
    }
}
