package com.hackinglab.toki

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackinglab.toki.ui.theme.TOKITheme

/** Shows over the lock screen without dismissing the device's keyguard. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TOKITheme {
                val title by AlarmService.ringing.collectAsState()
                LaunchedEffect(title) { if (title == null) finish() }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                        Text("TOKI ALARM", style = MaterialTheme.typography.labelLarge)
                        Text(title ?: "Reminder", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Time to get ready.")
                        Button(onClick = { stopService(Intent(this@AlarmActivity, AlarmService::class.java)); finish() },
                            modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Dismiss alarm") }
                        Text("Stops automatically after 2 minutes", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
