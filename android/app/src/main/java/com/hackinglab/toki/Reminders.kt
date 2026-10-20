package com.hackinglab.toki

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
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
    companion object { const val CHANNEL = "toki_reminders_v1"; const val TEST = "__local_test__" }
    init {
        val channel = NotificationChannel(CHANNEL, "Audible reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Sounds for TOKI deadlines and test reminders"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    fun ready() = exactAllowed() && notificationsAllowed()
    private fun pending(id: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setData(Uri.Builder().scheme("toki").authority("reminder").appendPath(id).build()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun set(id: String, time: Long) { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(id)) }
    fun cancelAll() {
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
    fun test() {
        check(ready()) { "Allow notifications and exact alarms first." }
        val at = System.currentTimeMillis() + 30_000
        set(TEST, at)
        prefs.edit().putLong("testAt", at).commit()
    }
    fun consumeTest(): Boolean {
        val at = prefs.getLong("testAt", 0)
        if (at == 0L || System.currentTimeMillis() < at || System.currentTimeMillis() - at > 600_000) return false
        prefs.edit().remove("testAt").commit()
        return true
    }
    fun show(id: String, title: String, message: String) {
        if (!notificationsAllowed()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder).setContentTitle(title).setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(id, 1, notification) }
        catch (_: SecurityException) { /* Permission can be revoked after the check. */ }
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
