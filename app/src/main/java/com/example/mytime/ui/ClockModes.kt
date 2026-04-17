package com.example.mytime.ui

import com.example.mytime.ui.ParticleWeather

enum class ClockMode {
    CLOCK,
    POMODORO,
    COUNTDOWN,
    STOPWATCH
}

enum class PomodoroPhase {
    FOCUS,
    BREAK
}

enum class SleepSoundMode {
    RAIN,
    WHITE_NOISE
}

enum class ThemePreset {
    AUTO,
    FOCUS,
    PLAYFUL,
    SERENE,
    NIGHT
}

enum class EdgeLightMode {
    NONE,
    BREAK_REMINDER,
    TIMER_ALERT,
    STOPWATCH_ACTIVE,
    AMBIENT_FOCUS,
    AMBIENT_PLAYFUL,
    AMBIENT_SERENE,
    AMBIENT_NIGHT
}

data class ThemePresetProfile(
    val fontName: String,
    val weather: ParticleWeather,
    val particlesEnabled: Boolean,
    val catsEnabled: Boolean,
    val dynamicWallpaperEnabled: Boolean,
    val soundButtonVisible: Boolean,
    val quietLayout: Boolean,
    val dimStrength: Float
)

fun ThemePreset.resolveActive(hour24: Int): ThemePreset {
    return if (this != ThemePreset.AUTO) {
        this
    } else {
        when (hour24) {
            in 7..11 -> ThemePreset.FOCUS
            in 12..18 -> ThemePreset.PLAYFUL
            in 19..22 -> ThemePreset.SERENE
            else -> ThemePreset.NIGHT
        }
    }
}

fun ThemePreset.profile(): ThemePresetProfile {
    return when (this) {
        ThemePreset.AUTO -> ThemePreset.SERENE.profile()
        ThemePreset.FOCUS -> ThemePresetProfile(
            fontName = "Digital",
            weather = ParticleWeather.WIND,
            particlesEnabled = true,
            catsEnabled = true,
            dynamicWallpaperEnabled = true,
            soundButtonVisible = false,
            quietLayout = false,
            dimStrength = 0.90f
        )
        ThemePreset.PLAYFUL -> ThemePresetProfile(
            fontName = "Pixel",
            weather = ParticleWeather.SUNNY,
            particlesEnabled = true,
            catsEnabled = true,
            dynamicWallpaperEnabled = true,
            soundButtonVisible = true,
            quietLayout = false,
            dimStrength = 0.92f
        )
        ThemePreset.SERENE -> ThemePresetProfile(
            fontName = "Modern",
            weather = ParticleWeather.FOG,
            particlesEnabled = true,
            catsEnabled = true,
            dynamicWallpaperEnabled = true,
            soundButtonVisible = true,
            quietLayout = false,
            dimStrength = 0.84f
        )
        ThemePreset.NIGHT -> ThemePresetProfile(
            fontName = "Thick",
            weather = ParticleWeather.DRIZZLE,
            particlesEnabled = true,
            catsEnabled = true,
            dynamicWallpaperEnabled = true,
            soundButtonVisible = false,
            quietLayout = true,
            dimStrength = 0.62f
        )
    }
}
