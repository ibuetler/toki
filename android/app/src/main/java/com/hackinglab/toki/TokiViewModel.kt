package com.hackinglab.toki

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackinglab.toki.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EventPreview(val nextStart: Long? = null, val problem: String? = null, val nextReminderStart: Long? = null)

data class TokiState(val deletableIds: Set<String> = emptySet(), val deletePermissionsLoaded: Boolean = false, val createError: String? = null, val createdVersion: Int = 0, val ready: Boolean = false, val session: Session? = null,
    val deadlines: List<Deadline> = emptyList(), val busy: Boolean = false,
    val message: String? = null, val lastSync: Long = 0, val scheduled: Int = 0,
    val exactAllowed: Boolean = false, val notificationsAllowed: Boolean = false, val fullScreenAllowed: Boolean = false, val testAt: Long = 0, val testDelay: Long = -1, val previews: Map<String, EventPreview> = emptyMap())

class TokiViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = TokiGraph.get(application)
    private val mutable = MutableStateFlow(TokiState())
    val state = mutable.asStateFlow()
    init {
        action { reload() }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = mutable.value
                val now = System.currentTimeMillis()
                if (!current.busy && current.deadlines.any { event ->
                    val preview = current.previews[event.id]
                    preview?.nextStart?.let { it <= now } == true ||
                        preview?.nextReminderStart?.let { EventTime.reminderAt(it, event.reminderMinutes) <= now } == true
                }) {
                    val previews = withContext(Dispatchers.Default) { eventPreviews(current.deadlines, now) }
                    if (!mutable.value.busy && mutable.value.deadlines === current.deadlines)
                        mutable.value = mutable.value.copy(previews = previews)
                }
            }
        }
    }
    private fun eventPreviews(records: List<Deadline>, now: Long): Map<String, EventPreview> = records.associate { event -> event.id to try {
        EventPreview(nextStart = OccurrencePlanner.nextEvent(event, now)?.startMillis,
            nextReminderStart = OccurrencePlanner.next(event, now)?.startMillis)
    } catch (error: Exception) { EventPreview(problem = error.message ?: "Invalid recurrence settings") } }

    private fun reload() {
        val count = graph.reschedule()
        val records = graph.database.deadlines().all()
        val previews = eventPreviews(records, System.currentTimeMillis())
        mutable.value = mutable.value.copy(deletableIds = graph.prefs.getStringSet("deletableIds", emptySet()).orEmpty().toSet(), deletePermissionsLoaded = graph.prefs.contains("deletableIds"), ready = true, session = graph.sessions.load(),
            deadlines = records, previews = previews, lastSync = graph.prefs.getLong("lastSync", 0),
            scheduled = count, testAt = graph.reminders.pendingTestAt(), testDelay = graph.reminders.lastTestDelay(), fullScreenAllowed = graph.reminders.fullScreenAllowed(), exactAllowed = graph.reminders.exactAllowed(), notificationsAllowed = graph.reminders.notificationsAllowed())
    }
    private fun action(work: () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { work() } }
            catch (error: Exception) {
                if (error is ApiFailure && error.status in listOf(401, 403)) withContext(Dispatchers.IO) { graph.logout(); reload() }
                mutable.value = mutable.value.copy(message = when (error) {
                    is ApiFailure -> error.message
                    is java.io.IOException -> "Cannot reach PocketBase. Cached deadlines remain available; try Sync when online."
                    else -> "Could not complete the operation. Please try again."
                }, ready = true)
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun deleteEvent(id: String) {
        if (mutable.value.busy || id !in mutable.value.deletableIds) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            var deleted = false
            try {
                withContext(Dispatchers.IO) {
                    val session = graph.api.refresh(requireNotNull(graph.sessions.load()))
                    graph.sessions.save(session)
                    graph.api.deleteDeadline(session, id)
                    deleted = true
                    graph.removeLocalEvent(id)
                    reload()
                }
                mutable.value = mutable.value.copy(message = "Event deleted. Its future reminders have been cancelled on this phone.")
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(message = when {
                    deleted -> "Event deleted in PocketBase, but local cleanup failed. Tap Sync to remove its cached reminder."
                    error is ApiFailure && error.status in listOf(403, 404) -> "The event was not found or you no longer have permission to delete it. Tap Sync to refresh."
                    error is ApiFailure && error.status == 401 -> "Your session expired. Sign in again before deleting."
                    error is java.io.IOException -> "Could not confirm deletion. Tap Sync to check whether the event still exists before retrying."
                    else -> "Could not delete the event. Please try again."
                })
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun createEvent(draft: NewEvent) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, createError = null, message = null)
        viewModelScope.launch {
            var saved = false
            try {
                withContext(Dispatchers.IO) {
                    val event = draft.deadline()
                    require(OccurrencePlanner.next(event, System.currentTimeMillis()) != null) {
                        "Choose an event with a future reminder time."
                    }
                    val session = graph.api.refresh(requireNotNull(graph.sessions.load()))
                    graph.sessions.save(session)
                    graph.api.createDeadline(session, draft)
                    saved = true
                    mutable.value = mutable.value.copy(createdVersion = mutable.value.createdVersion + 1)
                    graph.sync(session)
                    reload()
                }
                mutable.value = mutable.value.copy(message = "Event created and synced.")
            } catch (error: Exception) {
                val text = when {
                    saved -> "Event saved in PocketBase, but sync failed. Tap Sync to download it; do not create it again."
                    error is IllegalArgumentException -> error.message ?: "Check the event details."
                    error is ApiFailure && error.status == 400 -> "PocketBase rejected the event. Check its details."
                    error is ApiFailure && error.status == 403 -> "You do not have permission to create this event."
                    error is ApiFailure -> error.message ?: "Could not save the event."
                    error is java.io.IOException -> "Could not confirm whether the event was saved. Sync and check before retrying."
                    else -> "Could not create the event. Check your connection and sign-in."
                }
                mutable.value = if (saved) mutable.value.copy(message = text) else mutable.value.copy(createError = text)
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun login(email: String, password: String) = action {
        val session = graph.api.login(email.trim(), password)
        graph.logout()
        graph.sessions.save(session)
        reload()
        graph.sync(session)
        reload()
    }
    fun sync() = action { graph.sessions.load()?.let { graph.sync(it) }; reload() }
    fun logout() = action { graph.logout(); reload() }
    fun permissionsChanged() = action { reload() }
    fun testReminder() = action {
        val at = graph.reminders.test()
        mutable.value = mutable.value.copy(testAt = at, testDelay = -1,
            message = "Test alarm scheduled. The target time is shown below; you can lock your phone now.")
    }
}
