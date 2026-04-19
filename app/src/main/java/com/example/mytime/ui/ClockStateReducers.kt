package com.example.mytime.ui

internal object ClockStateReducers {
    enum class ModeMessage {
        FOCUS_DONE,
        BREAK_DONE,
        COUNTDOWN_DONE,
        BREAK_NUDGE
    }

    data class ModeTickResult(
        val state: ClockState,
        val message: ModeMessage? = null,
        val triggerSound: Boolean = false,
        val edgeLightMode: EdgeLightMode? = null,
        val edgeLightDurationMs: Long? = null
    )

    fun advanceModeState(
        state: ClockState,
        timerAlertEdgeMs: Long = DEFAULT_TIMER_ALERT_EDGE_MS,
        breakNudgeIntervalSec: Int = DEFAULT_BREAK_NUDGE_INTERVAL_SEC,
        breakNudgeEdgeMs: Long = DEFAULT_BREAK_NUDGE_EDGE_MS
    ): ModeTickResult {
        if (!state.timerRunning) return ModeTickResult(state)
        return when (state.clockMode) {
            ClockMode.CLOCK -> ModeTickResult(state)
            ClockMode.POMODORO -> advancePomodoro(state, timerAlertEdgeMs)
            ClockMode.COUNTDOWN -> advanceCountdown(state, timerAlertEdgeMs)
            ClockMode.STOPWATCH -> advanceStopwatch(state, breakNudgeIntervalSec, breakNudgeEdgeMs)
        }
    }

    fun shouldTriggerDailyAlarm(
        state: ClockState,
        hour24: Int,
        minute: Int,
        second: Int,
        dailyMarker: String,
        lastDailyAlarmMarker: String
    ): Boolean {
        return state.dailyAlarmEnabled &&
            !state.isDailyAlarmRinging &&
            hour24 == state.dailyAlarmHour &&
            minute == state.dailyAlarmMinute &&
            second == 0 &&
            dailyMarker != lastDailyAlarmMarker
    }

    fun snoozeDailyAlarmState(
        state: ClockState,
        snoozeSeconds: Int,
        message: String = state.companionMessage
    ): ClockState {
        return state.copy(
            isDailyAlarmRinging = false,
            dailyAlarmSnoozeRemainingSeconds = snoozeSeconds,
            companionMessage = message
        )
    }

    fun dismissDailyAlarmState(
        state: ClockState,
        message: String = state.companionMessage
    ): ClockState {
        return state.copy(
            isDailyAlarmRinging = false,
            dailyAlarmSnoozeRemainingSeconds = 0,
            companionMessage = message
        )
    }

    fun effectiveEdgeLightMode(state: ClockState): EdgeLightMode? {
        state.edgeLightMode?.let { return it }
        return when (state.activeThemePreset) {
            ThemePreset.FOCUS -> EdgeLightMode.AMBIENT_FOCUS
            ThemePreset.PLAYFUL -> EdgeLightMode.AMBIENT_PLAYFUL
            ThemePreset.SERENE -> EdgeLightMode.AMBIENT_SERENE
            ThemePreset.NIGHT -> EdgeLightMode.AMBIENT_NIGHT
            else -> null
        }
    }

    fun weatherCandidatesForPreset(
        preset: ThemePreset,
        hour24: Int,
        allowBrightWeather: Boolean
    ): List<ParticleWeather> {
        val base = when (preset) {
            ThemePreset.FOCUS -> listOf(
                ParticleWeather.WIND,
                ParticleWeather.CLOUDY,
                ParticleWeather.FOG,
                ParticleWeather.DRIZZLE,
                ParticleWeather.RAIN,
                ParticleWeather.HAIL
            )
            ThemePreset.PLAYFUL -> listOf(
                ParticleWeather.SUNNY,
                ParticleWeather.CLOUDY,
                ParticleWeather.WIND,
                ParticleWeather.DRIZZLE,
                ParticleWeather.RAIN,
                ParticleWeather.HAIL
            )
            ThemePreset.SERENE -> listOf(
                ParticleWeather.CLOUDY,
                ParticleWeather.FOG,
                ParticleWeather.DRIZZLE,
                ParticleWeather.RAIN,
                ParticleWeather.SNOW,
                ParticleWeather.WIND
            )
            ThemePreset.NIGHT -> listOf(
                ParticleWeather.RAIN,
                ParticleWeather.DRIZZLE,
                ParticleWeather.HAIL
            )
            ThemePreset.AUTO -> weatherCandidatesForPreset(
                preset = ThemePreset.AUTO.resolveActive(hour24),
                hour24 = hour24,
                allowBrightWeather = allowBrightWeather
            )
        }
        return if (allowBrightWeather) base else base.filter(::isNightSafeWeather)
    }

    fun isBrightWeather(weather: ParticleWeather): Boolean {
        return weather == ParticleWeather.SUNNY || weather == ParticleWeather.CLOUDY
    }

    fun isNightSafeWeather(weather: ParticleWeather): Boolean {
        return when (weather) {
            ParticleWeather.SUNNY, ParticleWeather.CLOUDY, ParticleWeather.HAIL, ParticleWeather.BLIZZARD -> false
            ParticleWeather.FOG, ParticleWeather.DRIZZLE, ParticleWeather.RAIN, ParticleWeather.SNOW, ParticleWeather.WIND -> true
        }
    }

    fun weatherWeight(weather: ParticleWeather, preferCalm: Boolean): Int {
        if (!preferCalm) {
            return when (weather) {
                ParticleWeather.DRIZZLE -> 4
                ParticleWeather.FOG -> 3
                ParticleWeather.CLOUDY, ParticleWeather.RAIN, ParticleWeather.WIND, ParticleWeather.SNOW -> 2
                ParticleWeather.SUNNY, ParticleWeather.HAIL, ParticleWeather.BLIZZARD -> 1
            }
        }
        return when (weather) {
            ParticleWeather.DRIZZLE, ParticleWeather.FOG -> 5
            ParticleWeather.RAIN, ParticleWeather.SNOW -> 3
            ParticleWeather.WIND -> 2
            ParticleWeather.CLOUDY -> 1
            ParticleWeather.SUNNY, ParticleWeather.HAIL, ParticleWeather.BLIZZARD -> 1
        }
    }

    private fun advancePomodoro(state: ClockState, timerAlertEdgeMs: Long): ModeTickResult {
        val nextRemaining = state.pomodoroRemainingSeconds - 1
        val focusedSeconds = state.focusedSecondsToday + if (state.pomodoroPhase == PomodoroPhase.FOCUS) 1 else 0
        if (nextRemaining > 0) {
            return ModeTickResult(
                state.copy(
                    pomodoroRemainingSeconds = nextRemaining,
                    focusedSecondsToday = focusedSeconds
                )
            )
        }
        return if (state.pomodoroPhase == PomodoroPhase.FOCUS) {
            ModeTickResult(
                state = state.copy(
                    pomodoroPhase = PomodoroPhase.BREAK,
                    pomodoroRemainingSeconds = state.pomodoroBreakMinutes * 60,
                    focusedSecondsToday = focusedSeconds,
                    completedPomodoros = state.completedPomodoros + 1
                ),
                message = ModeMessage.FOCUS_DONE,
                triggerSound = true,
                edgeLightMode = EdgeLightMode.TIMER_ALERT,
                edgeLightDurationMs = timerAlertEdgeMs
            )
        } else {
            ModeTickResult(
                state = state.copy(
                    pomodoroPhase = PomodoroPhase.FOCUS,
                    pomodoroRemainingSeconds = state.pomodoroFocusMinutes * 60,
                    completedBreaks = state.completedBreaks + 1
                ),
                message = ModeMessage.BREAK_DONE,
                triggerSound = true,
                edgeLightMode = EdgeLightMode.TIMER_ALERT,
                edgeLightDurationMs = timerAlertEdgeMs
            )
        }
    }

    private fun advanceCountdown(state: ClockState, timerAlertEdgeMs: Long): ModeTickResult {
        val nextRemaining = state.countdownRemainingSeconds - 1
        return if (nextRemaining <= 0) {
            ModeTickResult(
                state = state.copy(
                    countdownRemainingSeconds = 0,
                    timerRunning = false
                ),
                message = ModeMessage.COUNTDOWN_DONE,
                triggerSound = true,
                edgeLightMode = EdgeLightMode.TIMER_ALERT,
                edgeLightDurationMs = timerAlertEdgeMs
            )
        } else {
            ModeTickResult(state.copy(countdownRemainingSeconds = nextRemaining))
        }
    }

    private fun advanceStopwatch(
        state: ClockState,
        breakNudgeIntervalSec: Int,
        breakNudgeEdgeMs: Long
    ): ModeTickResult {
        val nextElapsed = state.stopwatchElapsedSeconds + 1
        val needsBreakNudge = nextElapsed > 0 && nextElapsed % breakNudgeIntervalSec == 0
        return ModeTickResult(
            state = state.copy(stopwatchElapsedSeconds = nextElapsed),
            message = if (needsBreakNudge) ModeMessage.BREAK_NUDGE else null,
            triggerSound = false,
            edgeLightMode = if (needsBreakNudge) EdgeLightMode.BREAK_REMINDER else null,
            edgeLightDurationMs = if (needsBreakNudge) breakNudgeEdgeMs else null
        )
    }
}

internal fun ClockState.effectiveEdgeLightMode(): EdgeLightMode? {
    return ClockStateReducers.effectiveEdgeLightMode(this)
}

internal const val DEFAULT_TIMER_ALERT_EDGE_MS = 5_000L
internal const val DEFAULT_BREAK_NUDGE_INTERVAL_SEC = 30 * 60
internal const val DEFAULT_BREAK_NUDGE_EDGE_MS = 10_000L
