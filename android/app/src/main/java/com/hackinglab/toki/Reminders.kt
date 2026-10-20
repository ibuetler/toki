package com.hackinglab.toki

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hackinglab.toki.data.Deadline
import com.hackinglab.toki.data.EventTime

class Reminders(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    companion object { const val CHANNEL = "toki_alarm_playback_v2"; const val TEST = "__local_test__" }
    init {
        val channel = NotificationChannel(CHANNEL, "Audible reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Sounds for TOKI deadlines and test reminders"
            // AlarmService owns looping audio. Avoid a second notification sound.
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    fun fullScreenAllowed() = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    fun ready() = exactAllowed() && notificationsAllowed()
    private fun pending(id: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setData(Uri.Builder().scheme("toki").authority("reminder").appendPath(id).build()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun set(id: String, time: Long) {
        // These are visible, user-requested alarms. Allow-while-idle alarms can
        // throttle closely spaced reminders; alarm-clock delivery avoids that path.
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), pending(id))
    }
    fun pendingTestAt(): Long = prefs.getLong("testAt", 0)
    fun lastTestDelay(): Long = prefs.getLong("testDelay", -1)
    fun cancelAll() {
        context.stopService(Intent(context, AlarmService::class.java))
        prefs.getStringSet("ids", emptySet())!!.forEach { alarms.cancel(pending(it)) }
        alarms.cancel(pending(TEST))
        prefs.edit().clear().commit()
        NotificationManagerCompat.from(context).cancelAll()
    }
    fun rebuild(records: List<Deadline>): Int {
        val old = prefs.getStringSet("ids", emptySet())!!.toSet()
        old.forEach { alarms.cancel(pending(it)) }
        val scheduled = mutableSetOf<String>()
        if (ready()) {
            records.filter { it.enabled && EventTime.supportsReminder(it.rrule, it.allDay) }.forEach {
                val at = EventTime.reminderAt(it.startMillis, it.reminderMinutes)
                if (at > System.currentTimeMillis()) {
                    try { set(it.id, at); scheduled += it.id } catch (_: SecurityException) { /* Permission changed. */ }
                }
            }
        }
        prefs.edit().putStringSet("ids", scheduled).commit()
        val testAt = prefs.getLong("testAt", 0)
        if (testAt > System.currentTimeMillis() && ready()) {
            try { set(TEST, testAt) } catch (_: SecurityException) { }
        }
        return scheduled.size
    }
    fun test(): Long {
        check(ready()) { "Allow notifications and exact alarms first." }
        val at = System.currentTimeMillis() + 30_000
        check(prefs.edit().putLong("testAt", at).remove("testDelay").commit())
        try { set(TEST, at) } catch (error: Exception) {
            prefs.edit().remove("testAt").commit()
            throw error
        }
        android.util.Log.i("TokiAlarmTiming", "Test scheduled: targetMillis=$at")
        return at
    }
    fun consumeTest(): Boolean {
        val at = prefs.getLong("testAt", 0)
        if (at == 0L || System.currentTimeMillis() < at || System.currentTimeMillis() - at > 600_000) return false
        val delay = System.currentTimeMillis() - at
        prefs.edit().remove("testAt").putLong("testDelay", delay).commit()
        android.util.Log.i("TokiAlarmTiming", "Test delivered: latenessMillis=$delay")
        return true
    }
    fun show(id: String, title: String, message: String) {
        if (!notificationsAllowed()) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java)
                .putExtra("id", id).putExtra("title", title).putExtra("message", message))
        } catch (_: IllegalStateException) {
            // If the OS rejects a foreground launch, keep a visible fallback.
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(title).setContentText("Open TOKI: alarm playback was blocked by Android.").build()
            try { NotificationManagerCompat.from(context).notify(id, 1, notification) } catch (_: SecurityException) { }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment ?: return
        val result = goAsync()
        Thread {
            try {
                val app = TokiGraph.get(context)
                synchronized(app.lock) {
                    if (id == Reminders.TEST) {
                        if (app.reminders.consumeTest()) app.reminders.show(id, "TOKI test reminder", "Your phone can play TOKI reminders while the app is closed.")
                    } else if (app.sessions.load() != null) {
                        val event = app.database.deadlines().find(id)
                        if (event != null && event.enabled && EventTime.supportsReminder(event.rrule, event.allDay)) {
                            val delta = System.currentTimeMillis() - EventTime.reminderAt(event.startMillis, event.reminderMinutes)
                            if (delta in 0..600_000) app.reminders.show(id, event.title, "Starts in ${event.reminderMinutes} minutes")
                        }
                    }
                }
            } finally { result.finish() }
        }.start()
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val result = goAsync()
        Thread { try { TokiGraph.get(context).reschedule() } finally { result.finish() } }.start()
    }
}
