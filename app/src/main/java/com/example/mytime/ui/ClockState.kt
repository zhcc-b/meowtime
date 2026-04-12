package com.example.mytime.ui

import androidx.compose.ui.geometry.Offset
import com.example.mytime.R

/**
 * 封装时钟所需的所有状态
 */
data class ClockState(
    val hour: String = "00",
    val minute: String = "00",
    val second: String = "00",
    val currentHour24: Int = 0,
    val amPm: String = "",
    val date: String = "--",
    val dayOfWeek: String = "--",
    val batteryLevel: String = "--",
    val location: String = "--",
    val backgroundRes: Int? = null,
    val backgroundUrl: String? = null,
    
    // 设置项
    val isBurnInProtectionEnabled: Boolean = true,
    val isSoundButtonVisible: Boolean = true,
    val isSettingsVisible: Boolean = false,
    
    // 酷炫效果开关
    val isParallaxEnabled: Boolean = true,
    val isParticleSystemEnabled: Boolean = false,
    val isParticleWeatherAuto: Boolean = true,
    val particleWeather: ParticleWeather = ParticleWeather.SNOW,
    val isCatSystemEnabled: Boolean = true,
    val isDynamicWallpaperEnabled: Boolean = true,
    val is24HourFormat: Boolean = true,
    
    // 字体设置
    val selectedFont: ClockFont = AvailableClockFonts[0],
    val allFonts: List<ClockFont> = AvailableClockFonts,

    // 模式与专注
    val clockMode: ClockMode = ClockMode.CLOCK,
    val timerRunning: Boolean = false,
    val pomodoroPhase: PomodoroPhase = PomodoroPhase.FOCUS,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val pomodoroRemainingSeconds: Int = 25 * 60,
    val countdownDurationMinutes: Int = 10,
    val countdownRemainingSeconds: Int = 10 * 60,
    val stopwatchElapsedSeconds: Int = 0,
    val focusedSecondsToday: Int = 0,
    val completedPomodoros: Int = 0,
    val completedBreaks: Int = 0,

    // 提醒与陪伴
    val hourlyChimeEnabled: Boolean = false,
    val dailyAlarmEnabled: Boolean = false,
    val dailyAlarmHour: Int = 8,
    val dailyAlarmMinute: Int = 0,
    val breakReminderEnabled: Boolean = true,
    val companionMessage: String = "",

    // 主题与环境
    val selectedThemePreset: ThemePreset = ThemePreset.AUTO,
    val activeThemePreset: ThemePreset = ThemePreset.SERENE,
    val whiteNoiseEnabled: Boolean = false,

    // 视差位移 (由陀螺仪控制)
    val parallaxOffset: Offset = Offset.Zero,
    
    // 防烧屏位移 (由时间循环控制，定期微动)
    val burnInOffset: Offset = Offset.Zero
)
