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
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.hackinglab.toki.ui.theme.TOKITheme

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
                TokiScreen(state, onSaveTask = model::saveTask, onCompleteTask = model::completeTask, onDeleteTask = model::deleteTask, onClearTaskError = model::clearTaskError, onEdit = model::editEvent, onCloseEdit = model::closeEdit, onUpdate = model::updateEvent, onDelete = model::deleteEvent, onCreate = model::createEvent, onLogin = model::login, onSync = model::sync, onLogout = model::logout, onTest = model::testReminder, onNotifications = {
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
