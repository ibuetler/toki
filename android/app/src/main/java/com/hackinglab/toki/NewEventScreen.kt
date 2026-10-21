package com.hackinglab.toki

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hackinglab.toki.data.NewEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun NewEventScreen(busy: Boolean, serverError: String?, onClose: () -> Unit, onSave: (NewEvent) -> Unit, birthdaysAvailable: Boolean = true) {
    val initial = remember { LocalDateTime.now(ZoneId.systemDefault()).plusHours(1).withSecond(0).withNano(0) }
    var birthdayMode by rememberSaveable { mutableStateOf(false) }
    var birthdayDate by rememberSaveable { mutableStateOf("") }
    var birthdayTime by rememberSaveable { mutableStateOf("09:00") }
    var leapPolicy by rememberSaveable { mutableStateOf("feb28") }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var descriptionOpen by rememberSaveable { mutableStateOf(false) }
    var day by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime().toString()) }
    var reminder by rememberSaveable { mutableStateOf("5") }
    var duration by rememberSaveable { mutableStateOf("60") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var repeat by rememberSaveable { mutableStateOf("Does not repeat") }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(Modifier.fillMaxSize().testTag("new-event-form").systemBarsPadding().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("New event", style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") }
                } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !birthdayMode, onClick = { birthdayMode = false }, enabled = !busy, label = { Text("Event") })
                    FilterChip(selected = birthdayMode, onClick = { birthdayMode = true }, enabled = !busy && birthdaysAvailable, label = { Text("Birthday") })
                } }
                if (!birthdaysAvailable) item { Text("Birthday setup is pending on the backend. Tap Sync after the migration is installed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text(if (birthdayMode) "A birthday, remembered every year." else "A private event, just for you.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { OutlinedTextField(title, { title = it }, label = { Text(if (birthdayMode) "Name" else "Title") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
                item {
                    Column {
                        TextButton(onClick = { descriptionOpen = !descriptionOpen }, enabled = !busy) {
                            Text(if (descriptionOpen) "Hide description" else if (description.isBlank()) "+ Add description" else "Edit description")
                        }
                        if (descriptionOpen) OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") },
                            minLines = 2, maxLines = 5, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (birthdayMode) {
                    item { OutlinedTextField(birthdayDate, { birthdayDate = it }, label = { Text("Birth date") },
                        placeholder = { Text("8.10.1968") },
                        supportingText = { Text("Day.month.year, or day.month if the year is unknown.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedButton(onClick = {
                        val value = LocalTime.parse(birthdayTime)
                        TimePickerDialog(context, { _, h, m -> birthdayTime = LocalTime.of(h, m).toString() }, value.hour, value.minute, true).show()
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Remind me at $birthdayTime") } }
                    if (birthdayDate.trim().split('.').let { it.getOrNull(0)?.toIntOrNull() == 29 && it.getOrNull(1)?.toIntOrNull() == 2 }) item {
                        Column { Text("In non-leap years, celebrate on:", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = leapPolicy == "feb28", onClick = { leapPolicy = "feb28" }, enabled = !busy, label = { Text("February 28") })
                                FilterChip(selected = leapPolicy == "mar1", onClick = { leapPolicy = "mar1" }, enabled = !busy, label = { Text("March 1") })
                            }
                        }
                    }
                    item { Text("Repeats yearly. The reminder follows your phone’s local time. Birthdays remain visible all day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        val value = LocalDate.parse(day)
                        DatePickerDialog(context, { _, y, m, d -> day = LocalDate.of(y, m + 1, d).toString() }, value.year, value.monthValue - 1, value.dayOfMonth).show()
                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM yyyy"))) }
                    OutlinedButton(onClick = {
                        val value = LocalTime.parse(time)
                        TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).toString() }, value.hour, value.minute, true).show()
                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(time) }
                } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(reminder, { reminder = it }, label = { Text("Reminder (min before)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                    OutlinedTextField(duration, { duration = it }, label = { Text("Duration (min)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                } }
                item { Box {
                    OutlinedButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(repeat) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf("Does not repeat", "Every day", "Every week", "Every month", "Every year").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { repeat = option; menu = false })
                        }
                    }
                } }
                }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Enabled", style = MaterialTheme.typography.titleMedium); Text("Schedule reminders for this event.", style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = !busy)
                } }
                (error ?: serverError)?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                item { Button(onClick = {
                    try {
                        val birthday = if (birthdayMode) {
                            val reminderTime = LocalTime.parse(birthdayTime)
                            com.hackinglab.toki.data.BirthdaySpec.parseEuropean(birthdayDate, reminderTime.hour, reminderTime.minute, leapPolicy)
                        } else null
                        val draft = NewEvent(title, description, LocalDateTime.of(LocalDate.parse(day), LocalTime.parse(time)), ZoneId.systemDefault().id,
                            reminder.toIntOrNull() ?: throw IllegalArgumentException("Enter reminder minutes as a whole number."),
                            duration.toIntOrNull() ?: throw IllegalArgumentException("Enter duration minutes as a whole number."), enabled,
                            when (repeat) { "Every day" -> "FREQ=DAILY"; "Every week" -> "FREQ=WEEKLY"; "Every month" -> "FREQ=MONTHLY"; "Every year" -> "FREQ=YEARLY"; else -> "" }, birthday)
                        draft.deadline()
                        error = null
                        onSave(draft)
                    } catch (e: IllegalArgumentException) { error = e.message }
                }, enabled = !busy && title.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) "Saving…" else if (birthdayMode) "Create birthday" else "Create event") } }
                item { Text("An internet connection is needed to save. Your events will sync and reminders will be scheduled after saving.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
