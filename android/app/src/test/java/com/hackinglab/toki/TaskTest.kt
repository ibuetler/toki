package com.hackinglab.toki

import com.hackinglab.toki.data.*
import org.junit.Assert.*
import org.junit.Test

class TaskTest {
    private fun task(id: String, priority: String, created: Long) = TokiTask().apply {
        this.id = id; title = id; this.priority = priority; createdMillis = created
    }
    @Test fun prioritiesSortByUrgencyThenNewestFirst() {
        val records = listOf(task("normal", "normal", 9), task("oldHigh", "high", 1), task("low", "low", 12), task("urgent", "urgent", 0), task("newHigh", "high", 8))
        assertEquals(listOf("urgent", "newHigh", "oldHigh", "normal", "low"), TaskPriority.sorted(records).map { it.id })
    }
    @Test fun quickCaptureDefaultsToNormalWithoutReminder() {
        val draft = TaskDraft("A thought")
        draft.validate()
        assertEquals("normal", draft.priority); assertEquals(0L, draft.reminderMillis); assertEquals("", draft.notes)
    }
    @Test fun urgentPriorityAloneDoesNotScheduleAlarm() {
        assertNull(task("urgent", "urgent", 0).alarm())
    }
    @Test fun completingTaskCancelsAlarmWithoutRemovingDetails() {
        val task = task("one", "high", 0).apply { reminderMillis = System.currentTimeMillis() + 60_000 }
        val alarm = requireNotNull(task.alarm())
        assertEquals("task:one", alarm.id)
        assertEquals(task.reminderMillis, OccurrencePlanner.next(alarm, System.currentTimeMillis())?.reminderMillis)
        task.completed = true
        assertNull(task.alarm()); assertEquals("one", task.title)
        task.completed = false
        assertNotNull(task.alarm())
    }
    @Test fun pastReminderDoesNotRepeatOrCompleteTask() {
        val task = task("one", "normal", 0).apply { reminderMillis = System.currentTimeMillis() - 60_000 }
        assertNull(OccurrencePlanner.next(requireNotNull(task.alarm()), System.currentTimeMillis()))
        assertFalse(task.completed)
        assertEquals(task.reminderMillis, TaskDraft.from(task).reminderMillis)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidPriorityRejected() { TaskDraft("Thought", priority = "critical").validate() }
    @Test(expected = IllegalArgumentException::class) fun emptyTitleRejected() { TaskDraft(" ").validate() }
}
