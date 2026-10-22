package com.hackinglab.toki

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hackinglab.toki.data.*
import java.time.*
import java.time.format.DateTimeFormatter

private fun priorityColor(priority: String) = when (priority) {
    "urgent" -> Color(0xFFD54555); "high" -> Color(0xFFBE6B17); "low" -> Color(0xFF7B8490); else -> Color(0xFF4B83C6)
}
private fun taskDate(millis: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable internal fun TaskCard(task: TokiTask, busy: Boolean, now: Long, onComplete: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, focused: Boolean = false, revealVersion: Int = 0, onExpansionChanged: (Boolean) -> Unit = {}) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(focused) }
    LaunchedEffect(expanded) { onExpansionChanged(expanded) }
    LaunchedEffect(focused, revealVersion) { if (focused) expanded = true else if (revealVersion > 0) expanded = false }
    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("task-${task.id}"),
        shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, priorityColor(task.priority))) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = task.completed, onCheckedChange = onComplete, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = if (task.completed) "Reopen ${task.title}" else "Complete ${task.title}" })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Private · " + when {
                            task.completed -> "Completed"
                            task.reminderMillis == 0L -> "No reminder"
                            task.reminderMillis <= now -> "Reminder passed"
                            else -> taskDate(task.reminderMillis)
                        }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Surface(color = priorityColor(task.priority).copy(alpha = .12f), shape = CircleShape) {
                    Text(task.priority.replaceFirstChar { it.uppercase() }, Modifier.widthIn(max = 82.dp).padding(horizontal = 8.dp, vertical = 5.dp),
                        color = priorityColor(task.priority), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (expanded) {
                HorizontalDivider()
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (task.reminderMillis > 0) Text(
                    if (task.completed) "Completed. Its reminder is cancelled."
                    else if (task.reminderMillis <= now) "The reminder time has passed. This task stays active until you complete it."
                    else "Reminder ${taskDate(task.reminderMillis)}", style = MaterialTheme.typography.bodyMedium)
                if (task.notes.isNotBlank()) Text(task.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = onEdit, enabled = !busy) { Text("Edit task") }
                    TextButton(onClick = onDelete, enabled = !busy) { Text("Delete task", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable internal fun TaskForm(task: TokiTask?, busy: Boolean, error: String?, onClose: () -> Unit, onSave: (TaskDraft) -> Unit) {
    var title by rememberSaveable { mutableStateOf(task?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(task?.notes.orEmpty()) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var priority by rememberSaveable { mutableStateOf(task?.priority ?: "normal") }
    var reminder by rememberSaveable { mutableLongStateOf(task?.reminderMillis ?: 0L) }
    var localError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(Modifier.fillMaxSize().systemBarsPadding().imePadding().testTag("task-form"),
                contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (task == null) "New task" else "Edit task", style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") }
                } }
                item { Text("A thought now. One less thing to remember.", style = MaterialTheme.typography.bodyMedium) }
                item { OutlinedTextField(title, { title = it }, label = { Text("Task") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { Column {
                    Text("Priority", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(TaskPriority.values) { option -> FilterChip(selected = priority == option, onClick = { priority = option }, enabled = !busy,
                            label = { Text(option.replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = priorityColor(option).copy(alpha = .2f))) }
                    }
                } }
                item { Column {
                    TextButton(onClick = { notesOpen = !notesOpen }, enabled = !busy) { Text(if (notesOpen) "Hide notes" else if (notes.isBlank()) "+ Add notes" else "Edit notes") }
                    if (notesOpen) OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, enabled = !busy, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                } }
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me", Modifier.weight(1f))
                    Switch(checked = reminder > 0, onCheckedChange = { reminder = if (it) LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else 0L }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Remind me" })
                } }
                if (reminder > 0) item {
                    val value = Instant.ofEpochMilli(reminder).atZone(ZoneId.systemDefault())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d ->
                            reminder = value.withYear(y).withMonth(m + 1).withDayOfMonth(1).withDayOfMonth(d).toInstant().toEpochMilli()
                        }, value.year, value.monthValue - 1, value.dayOfMonth).show() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(value.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                        }
                        OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> reminder = value.withHour(h).withMinute(m).withSecond(0).withNano(0).toInstant().toEpochMilli() },
                            value.hour, value.minute, true).show() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(value.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                    }
                }
                (localError ?: error)?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                item { Button(onClick = {
                    try {
                        val draft = TaskDraft(title, notes, priority, reminder)
                        draft.validate()
                        require(reminder == 0L || reminder == task?.reminderMillis || reminder > System.currentTimeMillis()) { "Choose a future reminder time." }
                        localError = null
                        onSave(draft)
                    } catch (e: IllegalArgumentException) { localError = e.message }
                }, enabled = !busy && title.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (busy) "Saving…" else if (task == null) "Add task" else "Save task")
                } }
                item { Text("Private to you. Saving needs an internet connection. Priority alone does not ring an alarm.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
