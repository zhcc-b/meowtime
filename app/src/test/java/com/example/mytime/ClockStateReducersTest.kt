package com.example.mytime

import com.example.mytime.ui.ClockMode
import com.example.mytime.ui.ClockState
import com.example.mytime.ui.ClockStateReducers
import com.example.mytime.ui.EdgeLightMode
import com.example.mytime.ui.ParticleWeather
import com.example.mytime.ui.PomodoroPhase
import com.example.mytime.ui.ThemePreset
import com.example.mytime.ui.effectiveEdgeLightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockStateReducersTest {
    @Test
    fun countdownEndingStopsTimerAndRaisesAlert() {
        val result = ClockStateReducers.advanceModeState(
            ClockState(
                clockMode = ClockMode.COUNTDOWN,
                timerRunning = true,
                countdownRemainingSeconds = 1
            )
        )

        assertEquals(0, result.state.countdownRemainingSeconds)
        assertFalse(result.state.timerRunning)
        assertTrue(result.triggerSound)
        assertEquals(EdgeLightMode.TIMER_ALERT, result.edgeLightMode)
        assertEquals(ClockStateReducers.ModeMessage.COUNTDOWN_DONE, result.message)
    }

    @Test
    fun pomodoroFocusEndingSwitchesToBreak() {
        val result = ClockStateReducers.advanceModeState(
            ClockState(
                clockMode = ClockMode.POMODORO,
                timerRunning = true,
                pomodoroPhase = PomodoroPhase.FOCUS,
                pomodoroRemainingSeconds = 1,
                pomodoroBreakMinutes = 5,
                focusedSecondsToday = 42,
                completedPomodoros = 2
            )
        )

        assertEquals(PomodoroPhase.BREAK, result.state.pomodoroPhase)
        assertEquals(5 * 60, result.state.pomodoroRemainingSeconds)
        assertEquals(43, result.state.focusedSecondsToday)
        assertEquals(3, result.state.completedPomodoros)
        assertTrue(result.triggerSound)
        assertEquals(EdgeLightMode.TIMER_ALERT, result.edgeLightMode)
        assertEquals(ClockStateReducers.ModeMessage.FOCUS_DONE, result.message)
    }

    @Test
    fun pomodoroBreakEndingSwitchesBackToFocus() {
        val result = ClockStateReducers.advanceModeState(
            ClockState(
                clockMode = ClockMode.POMODORO,
                timerRunning = true,
                pomodoroPhase = PomodoroPhase.BREAK,
                pomodoroRemainingSeconds = 1,
                pomodoroFocusMinutes = 25,
                completedBreaks = 4
            )
        )

        assertEquals(PomodoroPhase.FOCUS, result.state.pomodoroPhase)
        assertEquals(25 * 60, result.state.pomodoroRemainingSeconds)
        assertEquals(5, result.state.completedBreaks)
        assertTrue(result.triggerSound)
        assertEquals(EdgeLightMode.TIMER_ALERT, result.edgeLightMode)
        assertEquals(ClockStateReducers.ModeMessage.BREAK_DONE, result.message)
    }

    @Test
    fun stopwatchEveryThirtyMinutesRaisesBreakReminderOnly() {
        val result = ClockStateReducers.advanceModeState(
            ClockState(
                clockMode = ClockMode.STOPWATCH,
                timerRunning = true,
                stopwatchElapsedSeconds = 30 * 60 - 1
            )
        )

        assertEquals(30 * 60, result.state.stopwatchElapsedSeconds)
        assertFalse(result.triggerSound)
        assertEquals(EdgeLightMode.BREAK_REMINDER, result.edgeLightMode)
        assertEquals(ClockStateReducers.ModeMessage.BREAK_NUDGE, result.message)
    }

    @Test
    fun dailyAlarmTriggersOnlyAtExactEnabledTimeOncePerMarker() {
        val state = ClockState(
            dailyAlarmEnabled = true,
            dailyAlarmHour = 7,
            dailyAlarmMinute = 30
        )
        val marker = "Apr 19, 2026-7-30"

        assertTrue(
            ClockStateReducers.shouldTriggerDailyAlarm(
                state = state,
                hour24 = 7,
                minute = 30,
                second = 0,
                dailyMarker = marker,
                lastDailyAlarmMarker = ""
            )
        )
        assertFalse(
            ClockStateReducers.shouldTriggerDailyAlarm(
                state = state,
                hour24 = 7,
                minute = 30,
                second = 0,
                dailyMarker = marker,
                lastDailyAlarmMarker = marker
            )
        )
    }

    @Test
    fun snoozeAndDismissClearRingingStatePredictably() {
        val ringing = ClockState(
            isDailyAlarmRinging = true,
            dailyAlarmSnoozeRemainingSeconds = 0,
            companionMessage = "ringing"
        )

        val snoozed = ClockStateReducers.snoozeDailyAlarmState(ringing, snoozeSeconds = 10 * 60, message = "snoozed")
        assertFalse(snoozed.isDailyAlarmRinging)
        assertEquals(10 * 60, snoozed.dailyAlarmSnoozeRemainingSeconds)
        assertEquals("snoozed", snoozed.companionMessage)

        val dismissed = ClockStateReducers.dismissDailyAlarmState(snoozed, message = "dismissed")
        assertFalse(dismissed.isDailyAlarmRinging)
        assertEquals(0, dismissed.dailyAlarmSnoozeRemainingSeconds)
        assertEquals("dismissed", dismissed.companionMessage)
    }

    @Test
    fun automaticWeatherAvoidsBrightCandidatesDuringNightHours() {
        val candidates = ClockStateReducers.weatherCandidatesForPreset(
            preset = ThemePreset.PLAYFUL,
            hour24 = 2,
            allowBrightWeather = false
        )

        assertTrue(candidates.isNotEmpty())
        assertFalse(candidates.contains(ParticleWeather.SUNNY))
        assertFalse(candidates.contains(ParticleWeather.CLOUDY))
        assertFalse(candidates.contains(ParticleWeather.HAIL))
        assertTrue(candidates.all(ClockStateReducers::isNightSafeWeather))
        assertTrue(ClockStateReducers.isBrightWeather(ParticleWeather.SUNNY))
    }

    @Test
    fun explicitEdgeLightModeOverridesAmbientThemeMode() {
        val explicitAlert = ClockState(
            activeThemePreset = ThemePreset.NIGHT,
            edgeLightMode = EdgeLightMode.TIMER_ALERT
        )
        val ambientNight = ClockState(activeThemePreset = ThemePreset.NIGHT)
        val autoTheme = ClockState(activeThemePreset = ThemePreset.AUTO)

        assertEquals(EdgeLightMode.TIMER_ALERT, explicitAlert.effectiveEdgeLightMode())
        assertEquals(EdgeLightMode.AMBIENT_NIGHT, ambientNight.effectiveEdgeLightMode())
        assertNull(autoTheme.effectiveEdgeLightMode())
    }
}
