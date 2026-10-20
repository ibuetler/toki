package com.hackinglab.toki

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Real alarm playback, independent of the phone's notification audio stream. */
class AlarmService : Service() {
    companion object {
        const val DISMISS = "com.hackinglab.toki.DISMISS_ALARM"
        const val NOTIFICATION_ID = 2001
        private val mutable = MutableStateFlow<String?>(null)
        val ringing = mutable.asStateFlow()
    }
    private var player: MediaPlayer? = null
    private var isTest = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == DISMISS || intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        isTest = intent.getStringExtra("id") == Reminders.TEST
        val title = intent.getStringExtra("title") ?: "TOKI reminder"
        val message = intent.getStringExtra("message") ?: "An event is coming up."
        val reminders = Reminders(this)
        if (!reminders.notificationsAllowed()) { stopSelf(); return START_NOT_STICKY }
        val screen = PendingIntent.getActivity(this, 1,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = PendingIntent.getService(this, 2, Intent(this, AlarmService::class.java).setAction(DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder).setContentTitle(title).setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(screen)
            .addAction(0, "Dismiss", dismiss)
            .apply { if (reminders.fullScreenAllowed()) setFullScreenIntent(screen, true) }
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        mutable.value = title
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TOKI:alarm").apply { acquire(130_000) }
        } else {
            wakeLock?.let { if (it.isHeld) it.release(); it.acquire(130_000) }
        }
        if (player == null) startPlayer()
        // One sound/dismiss target for overlapping alarms; latest title is shown.
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 120_000)
        return START_NOT_STICKY
    }
    private fun startPlayer() {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmService, sound)
                isLooping = true
                setOnPreparedListener {
                    it.start()
                    if (isTest) android.util.Log.i("TokiAlarmTiming", "Test audio started: nowMillis=${System.currentTimeMillis()}")
                }
                setOnErrorListener { _, _, _ -> stopSelf(); true }
                prepareAsync()
            }
        } catch (_: Exception) { stopSelf() }
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        mutable.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
