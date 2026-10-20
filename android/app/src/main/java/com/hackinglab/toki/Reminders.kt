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
import com.hackinglab.toki.data.OccurrencePlanner

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
        val reset = prefs.edit().putStringSet("ids", emptySet())
        old.forEach { reset.remove("occurrence:$it").remove("trigger:$it") }
        reset.commit()
        var count = 0
        if (ready()) records.filter { it.enabled && !it.allDay }.forEach {
            if (scheduleNext(it)) count++
        }
        val testAt = prefs.getLong("testAt", 0)
        if (testAt > System.currentTimeMillis() && ready()) {
            try { set(TEST, testAt) } catch (_: SecurityException) { }
        }
        return count
    }
    fun scheduleNext(event: Deadline): Boolean {
        alarms.cancel(pending(event.id))
        val ids = prefs.getStringSet("ids", emptySet())!!.toMutableSet().apply { remove(event.id) }
        val edit = prefs.edit().remove("occurrence:${event.id}").remove("trigger:${event.id}")
        var scheduled = false
        if (event.enabled && !event.allDay && ready()) {
            try {
                val next = OccurrencePlanner.next(event, System.currentTimeMillis())
                if (next != null) {
                    set(event.id, next.reminderMillis)
                    edit.putLong("occurrence:${event.id}", next.startMillis).putLong("trigger:${event.id}", next.reminderMillis)
                    ids += event.id
                    scheduled = true
                }
            } catch (_: Exception) { /* Invalid rule is surfaced per event in the UI. */ }
        }
        edit.putStringSet("ids", ids).commit()
        return scheduled
    }
    fun consumeOccurrence(id: String): Long? {
        val trigger = prefs.getLong("trigger:$id", 0)
        if (trigger == 0L || System.currentTimeMillis() < trigger) return null
        val occurrence = prefs.getLong("occurrence:$id", 0)
        prefs.edit().remove("trigger:$id").remove("occurrence:$id").commit()
        return occurrence.takeIf { System.currentTimeMillis() - trigger <= 600_000 }
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
                        val occurrence = app.reminders.consumeOccurrence(id)
                        if (event != null) {
                            if (event.enabled && !event.allDay && occurrence != null) {
                                app.reminders.show(id, event.title, "Starts in ${event.reminderMinutes} minutes")
                            }
                            // Advance only this event so simultaneous alarms retain their triggers.
                            app.reminders.scheduleNext(event)
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
