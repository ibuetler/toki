package com.hackinglab.toki

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.hackinglab.toki.data.Deadline
import com.hackinglab.toki.data.EventTime
import com.hackinglab.toki.ui.theme.TOKITheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[TokiViewModel::class.java]
        setContent {
            TOKITheme {
                val state by model.state.collectAsState()
                val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.permissionsChanged() }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) model.permissionsChanged()
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                TokiScreen(state, model, onNotifications = {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }, onFullScreen = {
                    if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                }, onExact = {
                    if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                })
            }
        }
    }
}

@Composable
private fun TokiScreen(state: TokiState, model: TokiViewModel, onNotifications: () -> Unit, onExact: () -> Unit, onFullScreen: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // Never saved to disk or instance state.
    Scaffold { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("TOKI", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                        Text("Make time for what matters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.session != null) TextButton(onClick = model::logout, enabled = !state.busy) { Text("Sign out") }
                }
            }
            if (!state.ready) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.session == null && state.ready) item {
                Card {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Your deadlines, together", style = MaterialTheme.typography.titleLarge)
                        Text("Sign in to see your private events and the groups you belong to.")
                        Text("pocketbase.hacking-lab.com", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !state.busy)
                        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.busy)
                        Button(onClick = { model.login(email, password); password = "" },
                            enabled = !state.busy && email.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.busy) "Signing in…" else "Sign in")
                        }
                    }
                }
            }
            if (state.session != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("YOUR TIME, AT A GLANCE", style = MaterialTheme.typography.labelMedium)
                        Text("${state.deadlines.size} events · ${state.scheduled} reminders set", style = MaterialTheme.typography.titleLarge)
                        Text(state.session.email)
                        Text(if (state.lastSync == 0L) "Not synced yet" else "Last synced ${formatTime(state.lastSync, ZoneId.systemDefault().id)}", style = MaterialTheme.typography.bodySmall)
                        Text("Cached events work offline. Sync to receive edits and membership changes.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = model::sync, enabled = !state.busy) { Text(if (state.busy) "Syncing…" else "Sync now") }
                    }
                }
            }
            item {
                OutlinedCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Ready to remind you", style = MaterialTheme.typography.titleMedium)
                        Text("Alarms ring until dismissed (up to 2 minutes). Allow lock-screen alarms to show the Dismiss screen while locked.")
                        if (!state.notificationsAllowed) OutlinedButton(onClick = onNotifications, enabled = !state.busy) { Text("Allow notifications") }
                        if (!state.exactAllowed) OutlinedButton(onClick = onExact, enabled = !state.busy) { Text("Allow exact alarms") }
                        if (!state.fullScreenAllowed) OutlinedButton(onClick = onFullScreen, enabled = !state.busy) { Text("Allow lock-screen alarms") }
                        if (state.notificationsAllowed && state.exactAllowed) Text("Permissions ready", color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(onClick = model::testReminder, enabled = !state.busy && state.notificationsAllowed && state.exactAllowed) { Text("Test alarm in 30 seconds") }
                        Text("Sound uses alarm volume and respects Do Not Disturb. Lock-screen display needs the extra permission above.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.message?.let { message -> item {
                Text(message, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium).padding(16.dp))
            } }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.session != null) {
                item { Text("Your events", style = MaterialTheme.typography.titleLarge) }
                if (state.deadlines.isEmpty()) item { Text("No events cached yet. Add deadlines in PocketBase, then tap Sync now.") }
                items(state.deadlines, key = { it.id }) { event -> DeadlineCard(event) }
            }
            item { Text("First milestone · Timed one-off reminders. Recurring and all-day reminders are coming next.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun formatTime(millis: Long, timezone: String): String {
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
    return DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm").withZone(zone).format(Instant.ofEpochMilli(millis))
}

@Composable
private fun DeadlineCard(event: Deadline) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (event.groupName.isBlank()) "PRIVATE" else "GROUP · ${event.groupName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(event.title, style = MaterialTheme.typography.titleLarge)
            Text("${formatTime(event.startMillis, event.timezone)} · ${event.timezone}")
            if (event.description.isNotBlank()) Text(event.description)
            Text(when {
                !EventTime.supportsReminder(event.rrule, event.allDay) -> "${if (event.rrule.isNotBlank()) "Recurring" else "All-day"} · reminder scheduling not yet available"
                !event.enabled -> "Reminder disabled"
                EventTime.reminderAt(event.startMillis, event.reminderMinutes) <= System.currentTimeMillis() -> "Reminder time has passed"
                else -> "Remind ${event.reminderMinutes} minutes before · subject to permissions above"
            }, style = MaterialTheme.typography.bodySmall)
        }
    }
}
