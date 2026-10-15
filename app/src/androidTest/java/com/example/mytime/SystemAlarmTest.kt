package com.example.mytime

import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mytime.ui.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemAlarmTest {
    @Test fun systemDeliveryRingsWithoutActivityAndConsumesOccurrenceOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SystemAlarmScheduler.preferences(context)
        val original = prefs.all.toMap()
        assumeTrue(SystemAlarmScheduler.canSchedule(context))
        assumeTrue(AlertKind.entries.none { prefs.getLong(AlertPlaybackService.untilKey(it), 0) > System.currentTimeMillis() })
        try {
            SystemAlarmScheduler.configure(context, true, 8, 0)
            val at = System.currentTimeMillis() + 2000
            assertTrue(SystemAlarmScheduler.scheduleOccurrence(context, at, false))
            await { prefs.getLong(AlertPlaybackService.untilKey(AlertKind.DAILY), 0) > System.currentTimeMillis() }
            val until = prefs.getLong(AlertPlaybackService.untilKey(AlertKind.DAILY), 0)
            assertTrue(prefs.getLong("dailyAt", 0) > at)
            DailyAlarmReceiver().onReceive(context, Intent(SystemAlarmScheduler.FIRE_DAILY).putExtra("at", at))
            assertEquals(until, prefs.getLong(AlertPlaybackService.untilKey(AlertKind.DAILY), 0))
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 1001 })
            AlertPlaybackService.ring(context, AlertKind.TIMER)
            await { prefs.getLong(AlertPlaybackService.untilKey(AlertKind.TIMER), 0) > System.currentTimeMillis() }
            AlertPlaybackService.stop(context, AlertKind.DAILY)
            await { prefs.getLong(AlertPlaybackService.untilKey(AlertKind.DAILY), 0) == 0L }
            assertTrue(prefs.getLong(AlertPlaybackService.untilKey(AlertKind.TIMER), 0) > System.currentTimeMillis())
        } finally {
            AlertKind.entries.forEach { AlertPlaybackService.stop(context, it) }
            await { AlertKind.entries.all { prefs.getLong(AlertPlaybackService.untilKey(it), 0) == 0L } }
            SystemAlarmScheduler.cancel(context, false)
            SystemAlarmScheduler.cancel(context, true)
            val editor = prefs.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.commit()
            SystemAlarmScheduler.restore(context)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
        assertTrue("Expected system alarm state within 10 seconds", condition())
    }
}
