package com.hackinglab.toki

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackinglab.toki.data.*
import com.hackinglab.toki.data.EventTime
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

private val Mint = Color(0xFFB6F4D8)
private val HeroColors = listOf(Color(0xFF122E2A), Color(0xFF236A58))
private fun date(value: Long, zone: String = ZoneId.systemDefault().id, pattern: String = "EEE, d MMM · HH:mm"): String =
    DateTimeFormatter.ofPattern(pattern).withZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))).format(Instant.ofEpochMilli(value))

@Composable
private fun Glyph(@DrawableRes resource: Int, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(painterResource(resource), contentDescription = null, modifier = modifier.size(22.dp), tint = tint)
}

@Composable
internal fun TokiScreen(state: TokiState, onLogin: (String, String) -> Unit, onSync: () -> Unit,
    onLogout: () -> Unit, onTest: () -> Unit, onNotifications: () -> Unit,
    onExact: () -> Unit, onFullScreen: () -> Unit, onCreate: (com.hackinglab.toki.data.NewEvent) -> Unit = {}, onDelete: (String) -> Unit = {}, onEdit: (String) -> Unit = {}, onCloseEdit: () -> Unit = {}, onUpdate: (com.hackinglab.toki.data.NewEvent) -> Unit = {}, onSaveTask: (TaskDraft, String?) -> Unit = { _, _ -> }, onCompleteTask: (String, Boolean) -> Unit = { _, _ -> }, onDeleteTask: (String) -> Unit = {}, onClearTaskError: () -> Unit = {}) {
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    state.deadlines.find { it.id == deletingId }?.let { event ->
        AlertDialog(onDismissRequest = { deletingId = null },
            title = { Text(if (event.rrule.isBlank() && event.kind != "birthday") "Delete event?" else "Delete recurring event?") },
            text = { Text("Delete “${event.title}” permanently?" +
                (if (event.rrule.isNotBlank() || event.kind == "birthday") " This deletes the entire series, including all future occurrences." else "") +
                (if (event.groupName.isNotBlank()) " It will be removed for everyone in ${event.groupName}." else "") +
                " Future reminders on this phone will be cancelled. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { deletingId = null; onDelete(event.id) }, enabled = !state.busy && event.id in state.deletableIds) {
                Text("Delete permanently", color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Cancel") } })
    }
    state.editing?.let { edit ->
        key(edit.id) { NewEventScreen(state.busy, state.editError, onCloseEdit, onUpdate, state.birthdaysAvailable, edit) }
    }
    var creatingBirthday by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.createdVersion) { creating = false }
    if (creating && state.session != null) NewEventScreen(state.busy, state.createError, { creating = false }, onCreate, state.birthdaysAvailable, birthday = creatingBirthday)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var taskList by rememberSaveable { mutableStateOf(false) }
    var completedTasks by rememberSaveable { mutableStateOf(false) }
    var taskForm by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.taskSavedVersion) { taskForm = false; editingTaskId = null }
    if (taskForm && state.session != null) key(editingTaskId) {
        TaskForm(state.tasks.find { it.id == editingTaskId }, state.busy, state.taskError,
            { taskForm = false; editingTaskId = null; onClearTaskError() }, { onSaveTask(it, editingTaskId) })
    }
    state.tasks.find { it.id == deletingTaskId }?.let { task ->
        AlertDialog(onDismissRequest = { deletingTaskId = null }, title = { Text("Delete task?") },
            text = { Text("Delete “${task.title}” permanently? Its future reminder will be cancelled.") },
            confirmButton = { TextButton(onClick = { deletingTaskId = null; onDeleteTask(task.id) }, enabled = !state.busy) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingTaskId = null }) { Text("Cancel") } })
    }
    var birthdayList by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("Upcoming") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.testAt) {
        while (true) { now = System.currentTimeMillis(); delay(if (state.testAt > now) 250 else 1000) }
    }
    val signedIn = state.session != null
    if (searchOpen && signedIn) AlertDialog(onDismissRequest = { searchOpen = false },
        title = { Text(if (taskList) "Find a task" else "Find an event") },
        text = { OutlinedTextField(query, { query = it }, label = { Text(if (taskList) "Task title or notes" else "Title, description or group") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") } }) },
        confirmButton = { TextButton(onClick = { searchOpen = false }) { Text("Show results") } },
        dismissButton = { TextButton(onClick = { query = ""; searchOpen = false }) { Text("Reset search") } })
    val permissionReady = state.notificationsAllowed && state.exactAllowed
    val visible = state.deadlines.filter { event ->
        val next = state.previews[event.id]?.nextStart
        val search = query.isBlank() || listOf(event.title, event.groupName, event.description).any { it.contains(query, true) }
        search && (if (birthdayList) event.kind == "birthday" else event.kind != "birthday") && when (filter) {
            "Today" -> next != null && Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).toLocalDate() == Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            "Groups" -> event.groupName.isNotBlank()
            "Upcoming" -> next != null && (state.previews[event.id]?.visibleUntil ?: next) > now
            else -> true
        }
    }.sortedWith(compareBy<Deadline> { state.previews[it.id]?.nextStart ?: it.startMillis }.thenBy { it.title.lowercase() }.thenBy { it.id })
    val next = (state.deadlines + state.tasks.mapNotNull { it.alarm() }).filter { it.enabled && state.previews[it.id]?.problem == null }
        .mapNotNull { e -> state.previews[e.id]?.let { it.nextReminderStart ?: it.nextStart }?.let { start -> e to EventTime.reminderAt(start, e.reminderMinutes) } }
        .filter { it.second > now }.minByOrNull { it.second }
    val nextEvent = state.deadlines.filter { it.kind != "birthday" }.mapNotNull { event ->
        state.previews[event.id]?.let { preview -> preview.nextStart?.takeIf { (preview.visibleUntil ?: it) > now }?.let { event to it } }
    }.minByOrNull { it.second }
    val nextBirthday = state.deadlines.filter { it.kind == "birthday" && state.previews[it.id]?.problem == null }.mapNotNull { event ->
        state.previews[event.id]?.let { preview -> preview.nextStart?.takeIf { (preview.visibleUntil ?: it) > now }?.let { event to it } }
    }.minByOrNull { it.second }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (signedIn && tab == 0) ExtendedFloatingActionButton(onClick = { if (!state.busy) { if (taskList) { if (state.tasksAvailable) { editingTaskId = null; onClearTaskError(); taskForm = true } } else { creatingBirthday = birthdayList; creating = true } } },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Text(if (taskList) "+  New task" else if (birthdayList) "+  New birthday" else "+  New event") }
        },
        bottomBar = {
            if (signedIn) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                listOf("Agenda" to R.drawable.ic_calendar, "Alarms" to R.drawable.ic_bell, "Account" to R.drawable.ic_person).forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Glyph(icon) }, label = { Text(label) })
                }
            }
        }) { insets ->
        LazyColumn(Modifier.fillMaxSize().testTag("agenda-content").padding(insets), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(if (signedIn && tab == 0) 8.dp else 20.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Glyph(R.drawable.ic_reminder) }
                        Column { Text("TOKI", fontWeight = FontWeight.Black, letterSpacing = 3.sp, style = MaterialTheme.typography.titleLarge)
                            Text("Make time for what matters.", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (signedIn) Row {
                        IconButton(onClick = { tab = 0; searchOpen = true }) {
                            Icon(painterResource(R.drawable.ic_search), if (taskList) "Search tasks" else "Search events", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onSync, enabled = !state.busy) {
                            Icon(painterResource(R.drawable.ic_sync), "Sync events", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (state.busy || !state.ready) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.message?.let { message -> item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            } }
            if (!signedIn) {
                item { Text("A little less rush.\nA little more presence.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
                item { Text("Private plans. Shared moments. An alarm when it matters.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.ready) item { LoginCard(state.busy, onLogin) }
                item { TextButton(onClick = { tab = if (tab == 1) 0 else 1 }) { Text(if (tab == 1) "Hide alarm setup" else "Set up & test alarms") } }
                if (tab == 1) item { AlarmSetup(state, now, onTest, onNotifications, onExact, onFullScreen) }
            } else when (tab) {
                0 -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(date(now, pattern = "EEEE, d MMMM").uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Your day, in focus.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    item {
                        val testFirst = state.testAt > now && (next == null || state.testAt < next.second)
                        val alarmAt = if (!permissionReady) null else if (testFirst) state.testAt else next?.second
                        val orderedTiles = listOf(true, false).sortedBy { alarm ->
                            (if (alarm) alarmAt else nextEvent?.second) ?: Long.MAX_VALUE
                        }
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            orderedTiles.forEach { alarm -> key(alarm) {
                            if (alarm) SummaryTile(
                                label = if (permissionReady) "NEXT ALARM" else "ALARM SETUP",
                                title = if (!permissionReady) "Finish setup" else if (testFirst) "Test alarm" else next?.first?.title ?: "No upcoming alarms",
                                at = alarmAt,
                                zone = ZoneId.systemDefault().id,
                                now = now, colors = HeroColors, accent = Mint, icon = R.drawable.ic_bell,
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("next-alarm-tile"), onClick = { tab = 1 })
                            else SummaryTile(label = "NEXT EVENT", title = nextEvent?.first?.title ?: "No upcoming events",
                                at = nextEvent?.second, zone = ZoneId.systemDefault().id,
                                now = now, colors = listOf(Color(0xFF173A70), Color(0xFF2864AB)), accent = Color(0xFFC7E4FF),
                                icon = R.drawable.ic_calendar, modifier = Modifier.weight(1f).fillMaxHeight().testTag("next-event-tile"))
                            } }
                        }
                    }
                    item { BirthdayTile(nextBirthday, now) { taskList = false; birthdayList = true; filter = "Upcoming"; query = "" } }
                    if (!permissionReady || !state.fullScreenAllowed) item {
                        Surface(onClick = { tab = 1 }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Glyph(R.drawable.ic_bell)
                                Text(if (!permissionReady) "Finish alarm setup to hear reminders" else "Enable the lock-screen alarm screen", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Glyph(R.drawable.ic_arrow)
                            }
                        }
                    }
                    if (query.isNotBlank()) item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Results for “$query”", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { query = "" }) { Text("Clear search") }
                        }
                    }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (taskList) "Your tasks" else "Your events", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val taskCount = state.tasks.count { it.completed == completedTasks && (query.isBlank() || it.title.contains(query, true) || it.notes.contains(query, true)) }
                        val noun = if (taskList) { if (taskCount == 1) "task" else "tasks" } else if (birthdayList) { if (visible.size == 1) "birthday" else "birthdays" } else { if (visible.size == 1) "event" else "events" }
                        Text("${if (taskList) taskCount else visible.size} $noun", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                    item { PrimaryTabRow(selectedTabIndex = if (taskList) 2 else if (birthdayList) 1 else 0, containerColor = Color.Transparent) {
                        Tab(selected = !birthdayList && !taskList, onClick = { taskList = false; birthdayList = false; filter = "Upcoming" }, text = { Text("Events") })
                        Tab(selected = birthdayList && !taskList, onClick = { taskList = false; birthdayList = true; filter = "Upcoming" }, text = { Text("Birthdays") })
                        Tab(selected = taskList, onClick = { taskList = true }, text = { Text("Tasks") })
                    } }
                    if (taskList) {
                        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !completedTasks, onClick = { completedTasks = false }, label = { Text("Active") })
                            FilterChip(selected = completedTasks, onClick = { completedTasks = true }, label = { Text("Completed") })
                        } }
                        if (!state.tasksAvailable) item { Text("Task setup is pending on the backend. Tap Sync after setup.") }
                        val tasks = TaskPriority.sorted(state.tasks.filter { it.completed == completedTasks &&
                            (query.isBlank() || it.title.contains(query, true) || it.notes.contains(query, true)) })
                        if (tasks.isEmpty()) item { Text(if (query.isNotBlank()) "No tasks match your search." else if (completedTasks) "No completed tasks." else "A clear head starts here. Tap + New task to capture a thought.", style = MaterialTheme.typography.bodyMedium) }
                        items(tasks, key = { "task:${it.id}" }) { task ->
                            TaskCard(task, state.busy, now, { onCompleteTask(task.id, it) },
                                { editingTaskId = task.id; onClearTaskError(); taskForm = true }, { deletingTaskId = task.id })
                        }
                    } else {
                    item { LazyRow(Modifier.testTag("event-filters"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(if (birthdayList) listOf("Upcoming", "Today", "All") else listOf("Upcoming", "Today", "Groups", "All")) { option ->
                            FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                        }
                    } }
                    if (visible.isEmpty()) item { EmptyEvents(query, filter, state.deadlines.none { if (birthdayList) it.kind == "birthday" else it.kind != "birthday" }, onSync) }
                    itemsIndexed(visible, key = { _, event -> event.id }) { index, event ->
                        val start = state.previews[event.id]?.nextStart ?: event.startMillis
                        val day = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
                        val previous = visible.getOrNull(index - 1)?.let { previous ->
                            Instant.ofEpochMilli(state.previews[previous.id]?.nextStart ?: previous.startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        val colors = listOf(Color(0xFF4A9B8B), Color(0xFF648DD1), Color(0xFFAC83C6), Color(0xFFCC9A45), Color(0xFFCC8091), Color(0xFF66A3AF))
                        val border = colors[Math.floorMod(day.toEpochDay(), colors.size.toLong()).toInt()]
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (day != previous) Text(date(start, pattern = "EEE, d MMM yyyy"),
                                Modifier.padding(top = if (index == 0) 0.dp else 6.dp, start = 4.dp),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            EventCard(event, state.previews[event.id], permissionReady, now, border, state.busy, event.id in state.deletableIds, state.deletePermissionsLoaded, { onEdit(event.id) }) { deletingId = event.id }
                        }
                    }
                    }
                    item { Text(if (state.lastSync == 0L) "Not synced yet" else "Synced ${date(state.lastSync)} · Available offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                1 -> {
                    item { SectionTitle("Alarms, on your terms.", "Check permissions, try the sound, and feel ready.") }
                    item { AlarmSetup(state, now, onTest, onNotifications, onExact, onFullScreen) }
                    item { InfoCard("Built for the moments that matter", "Alarms use your alarm volume, ring until dismissed, and stop automatically after two minutes. Do Not Disturb and phone settings still apply.") }
                    item { InfoCard("A note on all-day events", "Birthdays use their own local reminder time. Other all-day events need a reminder-time policy before they can ring.") }
                }
                else -> {
                    item { SectionTitle("Your space.", "Private plans and shared calendars, together.") }
                    item { Card {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Glyph(R.drawable.ic_person, Modifier.size(32.dp))
                            Text(state.session!!.email, style = MaterialTheme.typography.titleMedium)
                            Text("Connected to pocketbase.hacking-lab.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider()
                            Text("${state.deadlines.count { it.groupName.isBlank() }} private events · ${state.deadlines.count { it.groupName.isNotBlank() }} shared events")
                            Button(onClick = onSync, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Sync now") }
                            OutlinedButton(onClick = onLogout, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
                        }
                    } }
                    item { InfoCard("Offline, with confidence", "Cached events and scheduled alarms work without a connection. Sync after event edits or group membership changes. Signing out removes this account’s cached events and alarms from the phone.") }
                }
            }
            item { Spacer(Modifier.height(if (signedIn && tab == 0) 80.dp else 8.dp)) }
        }
    }
}

@Composable private fun SectionTitle(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SummaryTile(label: String, title: String, at: Long?, zone: String, now: Long,
    colors: List<Color>, accent: Color, icon: Int, modifier: Modifier = Modifier, dateOnly: Boolean = false, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().background(Brush.linearGradient(colors)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(icon, Modifier.size(16.dp), accent)
                Text(label, color = accent, style = MaterialTheme.typography.labelSmall)
            }
            Text(if (at == null) "—" else if (dateOnly) { if (date(at, zone, "yyyy-MM-dd") == date(now, zone, "yyyy-MM-dd")) "Today" else "Birthday" } else date(at, zone, "HH:mm"), color = Color.White,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            if (at != null) {
                Text(date(at, zone, "EEE, d MMM"), color = accent, style = MaterialTheme.typography.labelSmall)
                Text(zone, color = accent, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val minutes = ((at - now + 59_999) / 60_000).coerceAtLeast(1)
                Text(if (dateOnly) "All day" else if (minutes < 60) "In $minutes min" else "In ${minutes / 60}h ${minutes % 60}m", color = Color.White, style = MaterialTheme.typography.labelMedium)
            } else Text(if (onClick != null) "Tap for alarm settings" else "You’re all caught up", color = accent, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, color = colors.first(), content = content)
    else Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = colors.first(), content = content)
}

@Composable private fun BirthdayTile(next: Pair<Deadline, Long>?, now: Long, onClick: () -> Unit) {
    val accent = Color(0xFFF6D6F1)
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("next-birthday-tile"), shape = MaterialTheme.shapes.medium, color = Color(0xFF643663)) {
        Row(Modifier.background(Brush.linearGradient(listOf(Color(0xFF513052), Color(0xFF89517E)))).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Glyph(R.drawable.ic_cake, Modifier.size(28.dp), accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("NEXT BIRTHDAY", color = accent, style = MaterialTheme.typography.labelSmall)
                Text(next?.first?.title ?: "No upcoming birthdays", color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (next != null) Text(date(next.second, pattern = "EEE, d MMM yyyy"), color = accent, style = MaterialTheme.typography.labelSmall)
            }
            if (next != null) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val zone = ZoneId.systemDefault()
                val days = java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), Instant.ofEpochMilli(next.second).atZone(zone).toLocalDate())
                Text(if (days <= 0) "Today" else "In $days ${if (days == 1L) "day" else "days"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                val age = com.hackinglab.toki.data.BirthdaySpec.from(next.first).ageOn(next.second)
                if (age != null) Text("Turns $age", color = accent, style = MaterialTheme.typography.labelSmall)
            } else Glyph(R.drawable.ic_arrow, tint = accent)
        }
    }
}

@Composable private fun LoginCard(busy: Boolean, onLogin: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Welcome to TOKI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Sign in to bring your events together.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(email, { email = it }, label = { Text("Email address") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy)
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy)
            Button(onClick = { onLogin(email, password); password = "" }, enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) "Signing in…" else "Sign in") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_lock, Modifier.size(16.dp)); Text("Your password is never saved on this phone.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable private fun AlarmSetup(state: TokiState, now: Long, onTest: () -> Unit, onNotifications: () -> Unit, onExact: () -> Unit, onFullScreen: () -> Unit, onCreate: (com.hackinglab.toki.data.NewEvent) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Alarm readiness", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SetupRow("Notifications", "Show alarm controls and reminders", state.notificationsAllowed, onNotifications)
                HorizontalDivider()
                SetupRow("Exact alarms", "Ring at your scheduled time", state.exactAllowed, onExact)
                HorizontalDivider()
                SetupRow("Lock-screen display", "Show a full-screen Dismiss button", state.fullScreenAllowed, onFullScreen)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Glyph(R.drawable.ic_bell, Modifier.size(28.dp))
                Text("Hear it for yourself.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Set a test alarm, then lock your screen. No event will be added to your calendar.")
                Button(onClick = onTest, enabled = !state.busy && state.notificationsAllowed && state.exactAllowed && state.testAt <= now,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (state.testAt > now) "Alarm scheduled" else "Test alarm in 30 seconds") }
                if (state.testAt > now) {
                    Text("${(state.testAt - now + 999) / 1000}s", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text("Rings at ${date(state.testAt, pattern = "HH:mm:ss")}")
                }
                if (state.testDelay >= 0) Text("Last delivery: ${"%.1f".format(state.testDelay / 1000.0)}s after target", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun SetupRow(title: String, subtitle: String, ready: Boolean, onEnable: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Glyph(if (ready) R.drawable.ic_check else R.drawable.ic_bell)
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (ready) Text("Ready", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        else TextButton(onClick = onEnable) { Text("Enable") }
    }
}

@Composable private fun EventCard(event: Deadline, preview: EventPreview?, permissionsReady: Boolean, now: Long, dateColor: Color, busy: Boolean, canDelete: Boolean, permissionsLoaded: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }
    val time = preview?.nextStart ?: event.startMillis
    val reminderPassed = preview?.nextStart != null && EventTime.reminderAt(preview.nextStart, event.reminderMinutes) <= now
    val status = when { !event.enabled -> "Off"; preview?.problem != null -> "Needs attention"; preview?.nextStart == null -> "Finished"; reminderPassed -> if (event.kind == "birthday") "Today" else "Due soon"; !permissionsReady -> "Setup needed"; else -> "Alarm on" }
    val active = status == "Alarm on"
    Card(border = BorderStroke(1.5.dp, dateColor), modifier = Modifier.testTag("event-${event.id}"), shape = MaterialTheme.shapes.small, onClick = { expanded = !expanded }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.widthIn(min = 50.dp)) {
                    if (event.kind == "birthday") Glyph(R.drawable.ic_cake, Modifier.size(24.dp))
                    else Text(date(time, pattern = "HH:mm"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(date(time, pattern = if (Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).year == Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).year) "d MMM" else "d MMM yy"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(if (event.groupName.isBlank()) R.drawable.ic_lock else R.drawable.ic_group, Modifier.size(12.dp))
                        Text("${event.groupName.ifBlank { "Private" }} · ${if (event.kind == "birthday") "Yearly" else if (event.rrule.isBlank()) "One-time" else repeatLabel(event.rrule)}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Surface(color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                    Text(status, Modifier.widthIn(max = 82.dp).padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (expanded) {
                HorizontalDivider()
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (event.kind == "birthday") {
                    val birthday = com.hackinglab.toki.data.BirthdaySpec.from(event)
                    val age = birthday.ageOn(time)
                    if (age != null) Text("Turning $age", style = MaterialTheme.typography.bodyMedium)
                    Text("Reminder at ${"%02d:%02d".format(birthday.hour, birthday.minute)} in your phone’s local time zone.", style = MaterialTheme.typography.bodyMedium)
                    if (birthday.month == 2 && birthday.day == 29) Text("In non-leap years: ${if (birthday.leapDayPolicy == "mar1") "March 1" else "February 28"}.", style = MaterialTheme.typography.bodySmall)
                }
                Text("${date(time)} · ${ZoneId.systemDefault().id}", style = MaterialTheme.typography.bodyMedium)
                if (event.kind != "birthday" && event.timezone != ZoneId.systemDefault().id)
                    Text("Event time: ${date(time, event.timezone)} · ${event.timezone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when {
                    !event.enabled -> "This event or your personal reminder is switched off. Enable it in PocketBase, then sync."
                    preview?.problem != null -> preview.problem
                    preview?.nextStart == null -> "This event has no future deadline."
                    reminderPassed -> if (event.kind == "birthday") "The reminder time has passed. This birthday stays here all day." else "The reminder time has passed. This event stays here until its deadline."
                    event.kind == "birthday" -> "Repeats every year. Reminders follow your phone’s local time."
                    event.reminderMinutes == 0 -> "Alarm rings at the event’s start time."
                    else -> "Alarm rings ${event.reminderMinutes} minutes before the event."
                }, style = MaterialTheme.typography.bodyMedium)
                if (event.description.isNotBlank()) Text(event.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.rrule.isNotBlank()) Text("Repeat rule: ${event.rrule}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (canDelete) Row {
                    TextButton(onClick = onEdit, enabled = !busy) { Text("Edit event") }
                    TextButton(onClick = onDelete, enabled = !busy) { Text("Delete event", color = MaterialTheme.colorScheme.error) }
                } else if (!permissionsLoaded) Text("Sync to load event actions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun repeatLabel(rule: String): String {
    val parts = rule.removePrefix("RRULE:").split(';').mapNotNull { val p = it.split('=', limit = 2); if (p.size == 2) p[0] to p[1] else null }.toMap()
    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
    val unit = when (parts["FREQ"]) { "DAILY" -> "day"; "WEEKLY" -> "week"; "MONTHLY" -> "month"; "YEARLY" -> "year"; else -> return "Repeating" }
    return if (interval == 1) "Every $unit" else "Every $interval ${unit}s"
}

@Composable private fun EmptyEvents(query: String, filter: String, noData: Boolean, onSync: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Glyph(R.drawable.ic_calendar, Modifier.size(32.dp))
            Text(if (query.isNotBlank()) "No matching events" else if (filter == "Today") "A clear day ahead." else "Nothing here yet.", style = MaterialTheme.typography.titleLarge)
            Text(if (query.isNotBlank()) "Try another title or group name." else if (noData) "Add an event in PocketBase, then sync to bring it here." else "Try All to see completed or disabled events.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onSync) { Text("Sync events") }
        }
    }
}
@Composable private fun InfoCard(title: String, text: String) {
    OutlinedCard { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
