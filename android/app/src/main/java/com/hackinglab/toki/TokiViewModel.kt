package com.hackinglab.toki

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackinglab.toki.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EventPreview(val nextStart: Long? = null, val problem: String? = null)

data class TokiState(val ready: Boolean = false, val session: Session? = null,
    val deadlines: List<Deadline> = emptyList(), val busy: Boolean = false,
    val message: String? = null, val lastSync: Long = 0, val scheduled: Int = 0,
    val exactAllowed: Boolean = false, val notificationsAllowed: Boolean = false, val fullScreenAllowed: Boolean = false, val testAt: Long = 0, val testDelay: Long = -1, val previews: Map<String, EventPreview> = emptyMap())

class TokiViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = TokiGraph.get(application)
    private val mutable = MutableStateFlow(TokiState())
    val state = mutable.asStateFlow()
    init { action { reload() } }
    private fun reload() {
        val count = graph.reschedule()
        val records = graph.database.deadlines().all()
        val previews = records.associate { event -> event.id to try {
            EventPreview(OccurrencePlanner.next(event, System.currentTimeMillis())?.startMillis)
        } catch (error: Exception) { EventPreview(problem = error.message ?: "Invalid recurrence settings") } }
        mutable.value = mutable.value.copy(ready = true, session = graph.sessions.load(),
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
