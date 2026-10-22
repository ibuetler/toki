package com.hackinglab.toki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hackinglab.toki.data.*

@Composable
internal fun CalendarSettingsScreen(state: TokiState, onClose: () -> Unit, onReload: () -> Unit,
    onSource: (CalendarSource) -> Unit, onRule: (CalendarRule) -> Unit, onDelete: (String, Boolean) -> Unit) {
    var source by remember { mutableStateOf<CalendarSource?>(null) }
    var rule by remember { mutableStateOf<CalendarRule?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(state.settingsSavedVersion) { source = null; rule = null; deleting = null }
    val settings = state.calendarSettings ?: CalendarSettings()
    val busy = state.settingsBusy
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().systemBarsPadding().testTag("calendar-settings").padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Calendars", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose, enabled = !busy) { Text("Done") }
                } }
                item { Text("Your calendar links and exclusions are private to your TOKI account. Jupiter imports every four hours. Open TOKI or refresh afterward to update events and alarms.") }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.settingsError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                item { OutlinedButton(onClick = onReload, enabled = !busy) { Text("Reload settings") } }
                item { Text("Calendar connections", style = MaterialTheme.typography.titleLarge) }
                items(settings.sources, key = { "source:${it.id}" }) { entry ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(entry.enabled, { onSource(entry.copy(enabled = it)) }, enabled = !busy)
                        }
                        Text(if (entry.lastSuccess.isBlank()) "Not imported yet" else "Last import: ${entry.lastSuccess}", style = MaterialTheme.typography.bodySmall)
                        if (entry.lastError.isNotBlank()) Text(entry.lastError, color = MaterialTheme.colorScheme.error)
                        Row { TextButton(onClick = { source = entry }, enabled = !busy) { Text("Edit calendar") }
                            TextButton(onClick = { deleting = entry.id to true }, enabled = !busy) { Text("Remove calendar") } }
                    } }
                }
                item { Button(onClick = { source = CalendarSource() }, enabled = !busy && state.calendarSettings != null) { Text("Add calendar") } }
                item { HorizontalDivider() }
                item { Text("Exclusion rules", style = MaterialTheme.typography.titleLarge) }
                item { Text("Use * for any number of characters and ? for one character. Marina * matches titles starting with Marina. Matching ignores case and applies to the whole title.") }
                items(settings.rules, key = { "rule:${it.id}" }) { entry ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.pattern, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(entry.enabled, { onRule(entry.copy(enabled = it)) }, enabled = !busy)
                        }
                        Text((if (entry.matchType == "exact") "Exact title · " else "Pattern · ") + (settings.sources.find { it.id == entry.source }?.name ?: "All my calendars"), style = MaterialTheme.typography.bodySmall)
                        Row { TextButton(onClick = { rule = entry }, enabled = !busy) { Text("Edit rule") }
                            TextButton(onClick = { deleting = entry.id to false }, enabled = !busy) { Text("Delete rule") } }
                    } }
                }
                item { Button(onClick = { rule = CalendarRule() }, enabled = !busy && state.calendarSettings != null) { Text("Add exclusion") } }
                item { Text("Rule changes and removed/disabled connections take effect at the next successful server import. Hidden individual occurrences remain hidden independently.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    source?.let { initial ->
        key(initial.id) { CalendarSourceForm(initial, busy, state.settingsError, { source = null }, onSource) }
    }
    rule?.let { initial ->
        key(initial.id) { CalendarRuleForm(initial, settings.sources, state.deadlines, busy, state.settingsError, { rule = null }, onRule) }
    }
    deleting?.let { (id, isSource) -> AlertDialog(onDismissRequest = { if (!busy) deleting = null },
        title = { Text(if (isSource) "Remove calendar?" else "Delete exclusion?") },
        text = { Text(if (isSource) "At the next successful import, events supplied only by this calendar will be removed. Its scoped rules will also be deleted. Outlook is unchanged." else "Matching events may return at the next import. Individually hidden occurrences stay hidden.") },
        confirmButton = { TextButton(onClick = { onDelete(id, isSource) }, enabled = !busy) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { deleting = null }, enabled = !busy) { Text("Cancel") } }) }
}

@Composable private fun SettingsForm(title: String, busy: Boolean, error: String?, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            content()
        } }
    }
}

@Composable internal fun CalendarSourceForm(initial: CalendarSource, busy: Boolean, error: String?, onClose: () -> Unit, onSave: (CalendarSource) -> Unit) {
    // Published links are credentials: keep drafts in memory, not saved-instance state.
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var show by remember { mutableStateOf(false) }
    SettingsForm(if (initial.id.isBlank()) "Add calendar" else "Edit calendar", busy, error, onClose) {
        OutlinedTextField(name, { name = it }, label = { Text("Calendar name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(url, { url = it }, label = { Text("Published ICS URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation())
        TextButton(onClick = { show = !show }) { Text(if (show) "Hide URL" else "Show URL") }
        Text("Use a published HTTPS subscription link. Outlook links are supported; other hosts need administrator approval. Anyone with this link can read the published calendar.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(initial.copy(name = name.trim(), url = url.trim())) }, enabled = !busy && name.isNotBlank() && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save calendar") }
    }
}

@Composable internal fun CalendarRuleForm(initial: CalendarRule, sources: List<CalendarSource>, events: List<Deadline>, busy: Boolean, error: String?, onClose: () -> Unit, onSave: (CalendarRule) -> Unit) {
    var pattern by remember { mutableStateOf(initial.pattern) }
    var scope by remember { mutableStateOf(initial.source) }
    var wildcard by remember { mutableStateOf(initial.matchType != "exact") }
    var menu by remember { mutableStateOf(false) }
    val draft = initial.copy(pattern = pattern.trim(), source = scope, matchType = if (wildcard) "wildcard" else "exact")
    SettingsForm(if (initial.id.isBlank()) "Add exclusion" else "Edit rule", busy, error, onClose) {
        OutlinedTextField(pattern, { pattern = it }, label = { Text("Title pattern") }, placeholder = { Text("Marina *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Use * and ? wildcards"); Switch(wildcard, { wildcard = it }, enabled = !busy) }
        Box { OutlinedButton(onClick = { menu = true }, enabled = !busy) { Text(sources.find { it.id == scope }?.name ?: "All my calendars") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("All my calendars") }, onClick = { scope = ""; menu = false })
                sources.forEach { source -> DropdownMenuItem(text = { Text(source.name) }, onClick = { scope = source.id; menu = false }) }
            }
        }
        Text("Matches ${TitlePattern.preview(events, draft, sources)} currently imported events", style = MaterialTheme.typography.titleMedium)
        Text("Preview includes cached imports, including hidden entries. Events already excluded or not yet imported cannot be counted. Rules apply after deduplication and never change Outlook.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(draft) }, enabled = !busy && pattern.isNotBlank() && pattern.length <= 200, modifier = Modifier.fillMaxWidth()) { Text("Save rule") }
    }
}
