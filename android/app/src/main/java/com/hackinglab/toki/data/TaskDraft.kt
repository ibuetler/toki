package com.hackinglab.toki.data

/** Task reminders are one-off instants; priority never schedules an alarm. */
data class TaskDraft(val title: String, val notes: String = "", val priority: String = "normal", val reminderMillis: Long = 0) {
    fun validate() {
        require(title.isNotBlank() && title.trim().length <= 200) { "Enter a title of up to 200 characters." }
        require(notes.length <= 5000) { "Keep notes within 5000 characters." }
        require(priority in TaskPriority.values) { "Choose a valid priority." }
        require(reminderMillis >= 0) { "Choose a valid reminder time." }
    }
    companion object {
        fun from(task: TokiTask) = TaskDraft(task.title, task.notes, task.priority, task.reminderMillis)
    }
}
object TaskPriority {
    val values = listOf("urgent", "high", "normal", "low")
    fun sorted(tasks: List<TokiTask>) = tasks.sortedWith(compareBy<TokiTask> { values.indexOf(it.priority).takeIf { rank -> rank >= 0 } ?: 2 }
        .thenByDescending { it.createdMillis }.thenBy { it.id })
}
fun TokiTask.alarm(): Deadline? = if (completed || reminderMillis == 0L) null else Deadline().also {
    it.id = "task:$id"; it.kind = "task"; it.title = title; it.description = notes
    it.startMillis = reminderMillis; it.enabled = true; it.recurrenceLoaded = true
}
