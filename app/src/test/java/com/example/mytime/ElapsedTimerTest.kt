package com.example.mytime

import com.example.mytime.ui.*
import org.junit.Assert.*
import org.junit.Test

class ElapsedTimerTest {
    private fun running(mode: ClockMode) = ClockState(clockMode = mode, timerRunning = true, timerUpdatedAtElapsedMs = 1000L)

    @Test fun stopwatchIncludesBackgroundAndPreservesFractionalSeconds() {
        val first = ClockStateReducers.advanceElapsedMode(running(ClockMode.STOPWATCH), 16750).state
        assertEquals(15, first.stopwatchElapsedSeconds)
        assertEquals(16000L, first.timerUpdatedAtElapsedMs)
        assertEquals(16, ClockStateReducers.advanceElapsedMode(first, 17000).state.stopwatchElapsedSeconds)
    }
    @Test fun countdownCatchesUpOnceAfterDeadline() {
        val result = ClockStateReducers.advanceElapsedMode(running(ClockMode.COUNTDOWN).copy(countdownRemainingSeconds = 5), 31000)
        assertEquals(0, result.state.countdownRemainingSeconds)
        assertFalse(result.state.timerRunning)
        assertTrue(result.triggerSound)
        assertFalse(ClockStateReducers.advanceElapsedMode(result.state, 32000).triggerSound)
    }
    @Test fun pausedTimeIsExcludedAfterResume() {
        val paused = ClockStateReducers.advanceElapsedMode(running(ClockMode.STOPWATCH).copy(timerRunning = false), 31000).state
        assertEquals(0, paused.stopwatchElapsedSeconds)
        val resumed = paused.copy(timerRunning = true, timerUpdatedAtElapsedMs = 31000)
        assertEquals(2, ClockStateReducers.advanceElapsedMode(resumed, 33000).state.stopwatchElapsedSeconds)
    }
    @Test fun pomodoroCrossesSeveralPhasesWithoutLosingFocusTime() {
        val result = ClockStateReducers.advanceElapsedMode(running(ClockMode.POMODORO), (65 * 60 + 1) * 1000L)
        assertEquals(PomodoroPhase.FOCUS, result.state.pomodoroPhase)
        assertEquals(20 * 60, result.state.pomodoroRemainingSeconds)
        assertEquals(55 * 60, result.state.focusedSecondsToday)
        assertEquals(2, result.state.completedPomodoros)
        assertEquals(2, result.state.completedBreaks)
        assertTrue(result.triggerSound)
    }
    @Test fun elapsedJumpMatchesRepeatedTicks() {
        var stepped = running(ClockMode.POMODORO).copy(pomodoroRemainingSeconds = 90)
        val initial = stepped
        repeat(4001) { stepped = ClockStateReducers.advanceModeState(stepped).state }
        val jumped = ClockStateReducers.advanceModeBySeconds(initial, 4001).state
        assertEquals(stepped, jumped)
    }
}
