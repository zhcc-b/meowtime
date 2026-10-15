package com.example.mytime.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

internal fun nextDailyOccurrence(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
    fun onDate(date: java.time.LocalDate) = date.atTime(hour, minute).atZone(now.zone)
    val today = onDate(now.toLocalDate())
    return if (today.isAfter(now)) today else onDate(now.toLocalDate().plusDays(1))
}

/** Durable configuration and PendingIntents outlive the Activity and its ViewModel. */
internal object SystemAlarmScheduler {
    const val FIRE_DAILY = "com.example.mytime.FIRE_DAILY"
    const val FIRE_SNOOZE = "com.example.mytime.FIRE_SNOOZE"
    const val STATE_CHANGED = "com.example.mytime.ALERT_STATE_CHANGED"
    const val SNOOZE_MS = 10L * 60L * 1000L
    fun preferences(context: Context) = context.getSharedPreferences("system_alarms", Context.MODE_PRIVATE)
    private fun manager(context: Context) = context.getSystemService(AlarmManager::class.java)
    fun canSchedule(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || manager(context).canScheduleExactAlarms()

    fun configure(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val prefs = preferences(context)
        val changed = enabled != prefs.getBoolean("enabled", false) || hour != prefs.getInt("hour", 8) || minute != prefs.getInt("minute", 0)
        prefs.edit().putBoolean("enabled", enabled).putInt("hour", hour).putInt("minute", minute).apply()
        if (!enabled) {
            cancel(context, false)
            cancel(context, true)
        } else if (changed || prefs.getLong("dailyAt", 0) == 0L) {
            scheduleNextDaily(context)
        }
    }

    fun scheduleNextDaily(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val prefs = preferences(context)
        if (!prefs.getBoolean("enabled", false)) return
        scheduleOccurrence(context, nextDailyOccurrence(now, prefs.getInt("hour", 8), prefs.getInt("minute", 0)).toInstant().toEpochMilli(), false)
    }

    fun scheduleOccurrence(context: Context, at: Long, snooze: Boolean): Boolean {
        if (!canSchedule(context)) return false
        val intent = pending(context, snooze, at)
        return try {
            // Commit before scheduling: the receiver must be able to validate this occurrence
            // even when a different process instance receives it.
            preferences(context).edit().putLong(if (snooze) "snoozeAt" else "dailyAt", at).commit()
            manager(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            true
        } catch (error: SecurityException) {
            preferences(context).edit().remove(if (snooze) "snoozeAt" else "dailyAt").apply()
            DiagnosticLogger.log("ALARM", "Exact alarm unavailable: ${error.message}")
            false
        }
    }

    fun snooze(context: Context): Boolean {
        if (!preferences(context).getBoolean("enabled", false)) return false
        return scheduleOccurrence(context, System.currentTimeMillis() + SNOOZE_MS, true)
    }

    fun cancel(context: Context, snooze: Boolean) {
        manager(context).cancel(pending(context, snooze, 0))
        preferences(context).edit().remove(if (snooze) "snoozeAt" else "dailyAt").apply()
    }

    fun restore(context: Context, reboot: Boolean = false) {
        scheduleNextDaily(context)
        val snoozeAt = preferences(context).getLong("snoozeAt", 0)
        if (snoozeAt > 0 && preferences(context).getBoolean("enabled", false)) {
            scheduleOccurrence(context, if (reboot) maxOf(snoozeAt, System.currentTimeMillis() + 500) else snoozeAt, true)
        }
    }

    private fun pending(context: Context, snooze: Boolean, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context, if (snooze) 2 else 1,
        Intent(context, DailyAlarmReceiver::class.java).setAction(if (snooze) FIRE_SNOOZE else FIRE_DAILY).putExtra("at", at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun publishState(context: Context) {
        context.sendBroadcast(Intent(STATE_CHANGED).setPackage(context.packageName))
    }
}

class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SystemAlarmScheduler.FIRE_DAILY, SystemAlarmScheduler.FIRE_SNOOZE -> {
                val snooze = intent.action == SystemAlarmScheduler.FIRE_SNOOZE
                val prefs = SystemAlarmScheduler.preferences(context)
                val key = if (snooze) "snoozeAt" else "dailyAt"
                val at = intent.getLongExtra("at", 0)
                if (!prefs.getBoolean("enabled", false) || at <= 0 || prefs.getLong(key, 0) != at || System.currentTimeMillis() < at) return
                // Consume once before starting sound; a stale/duplicate delivery cannot ring twice.
                prefs.edit().remove(key).commit()
                if (!snooze) SystemAlarmScheduler.scheduleNextDaily(context)
                AlertPlaybackService.ring(context, AlertKind.DAILY)
            }
            Intent.ACTION_BOOT_COMPLETED -> SystemAlarmScheduler.restore(context, reboot = true)
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> SystemAlarmScheduler.restore(context)
        }
    }
}
