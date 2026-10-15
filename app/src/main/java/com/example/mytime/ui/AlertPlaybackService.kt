package com.example.mytime.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.mytime.MainActivity
import com.example.mytime.R
import kotlinx.coroutines.*

/** Android owns this scope from onCreate to onDestroy; the Activity never owns alarm sound. */
class AlertPlaybackService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var playback: Job? = null
    private val timeouts = mutableMapOf<AlertKind, Job>()
    private val owners = AlertSoundOwners(::startPlayback, { playback?.cancel() })

    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.installCrashHandler(applicationContext)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.alert_notification_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val kind = intent?.getStringExtra("kind")?.let { runCatching { AlertKind.valueOf(it) }.getOrNull() }
        if (intent?.action == RING && kind != null) {
            startForeground(NOTIFICATION_ID, notification())
            val now = System.currentTimeMillis()
            val prefs = SystemAlarmScheduler.preferences(this)
            val existing = prefs.getLong(untilKey(kind), 0)
            val until = intent.getLongExtra("until", 0)
            if (until > now) prefs.edit().putLong(untilKey(kind), maxOf(existing, until)).commit()
        }
        // Restore any other still-active owner after a service/process restart.
        for (active in AlertKind.entries) {
            val remaining = SystemAlarmScheduler.preferences(this).getLong(untilKey(active), 0) - System.currentTimeMillis()
            if (remaining > 0 && active !in timeouts) {
                startForeground(NOTIFICATION_ID, notification())
                if (wakeLock == null) {
                    wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "meowtime:alarm").apply { setReferenceCounted(false) }
                }
                val longestRemaining = AlertKind.entries.maxOf { prefsKind -> SystemAlarmScheduler.preferences(this).getLong(untilKey(prefsKind), 0) } - System.currentTimeMillis()
                wakeLock?.acquire(longestRemaining.coerceAtLeast(0) + 30_000)
                owners.acquire(active)
                timeouts[active] = scope.launch {
                    delay(remaining)
                    end(active)
                }
            }
        }
        if (intent?.action == STOP && kind != null) end(kind)
        if (intent?.action == STOP_ALL) AlertKind.entries.forEach(::end)
        SystemAlarmScheduler.publishState(this)
        if (timeouts.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        return START_REDELIVER_INTENT
    }

    private fun startPlayback() {
        val previous = playback
        playback = scope.launch {
            previous?.cancelAndJoin()
            try {
                AlarmTonePlayer().play()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DiagnosticLogger.log("AUDIO", "Alert service failed: ${error.message}")
                AlertKind.entries.forEach(::end)
            }
        }
    }

    private fun end(kind: AlertKind) {
        timeouts.remove(kind)?.cancel()
        owners.release(kind)
        SystemAlarmScheduler.preferences(this).edit().remove(untilKey(kind)).apply()
        SystemAlarmScheduler.publishState(this)
        if (timeouts.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, Intent(this, AlertPlaybackService::class.java).setAction(STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.alert_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_stat_alarm, getString(R.string.alert_notification_stop), stop).build())
            .build()
    }

    override fun onDestroy() {
        owners.clear()
        serviceJob.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    internal companion object {
        private const val RING = "ring"
        private const val STOP = "stop"
        private const val STOP_ALL = "stop_all"
        private const val CHANNEL = "clock_alerts"
        private const val NOTIFICATION_ID = 1001
        const val RING_MS = 9L * 60L * 1000L
        fun untilKey(kind: AlertKind) = "ringingUntil_${kind.name}"
        fun ring(context: Context, kind: AlertKind) {
            ContextCompat.startForegroundService(context, Intent(context, AlertPlaybackService::class.java).setAction(RING).putExtra("kind", kind.name).putExtra("until", System.currentTimeMillis() + RING_MS))
        }
        fun stop(context: Context, kind: AlertKind) {
            if (SystemAlarmScheduler.preferences(context).getLong(untilKey(kind), 0) <= 0) return
            context.startService(Intent(context, AlertPlaybackService::class.java).setAction(STOP).putExtra("kind", kind.name))
        }
    }
}
