package com.example.mytime.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.audiofx.LoudnessEnhancer
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.ui.geometry.Offset
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytime.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.random.Random

private val Context.dataStore by preferencesDataStore(name = "clock_settings")

private object PreferenceKeys {
    val parallax = booleanPreferencesKey("parallax")
    val cats = booleanPreferencesKey("cats")
    val is24Hour = booleanPreferencesKey("is_24_hour")
    val selectedFont = stringPreferencesKey("selected_font")
    val particleWeatherMode = stringPreferencesKey("particle_weather_mode")
    val clockMode = stringPreferencesKey("clock_mode")
    val pomodoroFocusMinutes = intPreferencesKey("pomodoro_focus_minutes")
    val pomodoroBreakMinutes = intPreferencesKey("pomodoro_break_minutes")
    val countdownDurationMinutes = intPreferencesKey("countdown_duration_minutes")
    val hourlyChime = booleanPreferencesKey("hourly_chime")
    val dailyAlarmEnabled = booleanPreferencesKey("daily_alarm_enabled")
    val dailyAlarmHour = intPreferencesKey("daily_alarm_hour")
    val dailyAlarmMinute = intPreferencesKey("daily_alarm_minute")
    val themePreset = stringPreferencesKey("theme_preset")
    val sleepSoundMode = stringPreferencesKey("sleep_sound_mode")
    val whiteNoise = booleanPreferencesKey("white_noise")
}

class ClockViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private enum class ReminderCue {
        DEFAULT,
        HOURLY_CHIME
    }

    private val _uiState = MutableStateFlow(ClockState())
    val uiState: StateFlow<ClockState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val whiteNoisePlayer = WhiteNoisePlayer()
    private val alarmTonePlayer = AlarmTonePlayer()
    private val appContext: Context
        get() = getApplication<Application>().applicationContext
    private val sensorManager = application.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val locale = Locale.getDefault()
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val amPmFormatter = DateTimeFormatter.ofPattern("a", locale)
    private val random = Random.Default

    private var basePitch: Float? = null
    private var baseRoll: Float? = null
    private var smoothParallax = Offset.Zero
    private var lastSensorUpdateTimeMs = 0L
    private var locationJob: Job? = null
    private var tickerJob: Job? = null
    private var edgeLightJob: Job? = null
    private var sleepSoundJob: Job? = null
    private var dailyAlarmJob: Job? = null
    private var dailyAlarmSnoozeJob: Job? = null
    private var dailyAlarmSnoozeDeadlineElapsedMs: Long? = null
    private var sleepSoundEndsAtElapsedMs: Long = 0L
    private var sensorRegistered = false
    private var isAppActive = false
    private var lastHourlyChimeMarker = ""
    private var lastDailyAlarmMarker = ""

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                _uiState.update { it.copy(batteryLevel = "${(level * 100 / scale)}%") }
            }
        }
    }

    init {
        _uiState.update {
            it.copy(
                is24HourFormat = DateFormat.is24HourFormat(appContext),
                location = appContext.getString(R.string.location_loading)
            )
        }
        appContext.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        observePersistedSettings()
    }

    private fun observePersistedSettings() {
        viewModelScope.launch {
            appContext.dataStore.data.collect { preferences ->
                _uiState.update { state ->
                    val selectedFontName = preferences[PreferenceKeys.selectedFont]
                    val selectedFont = AvailableClockFonts.find { it.name == selectedFontName } ?: state.selectedFont
                    val weatherMode = preferences[PreferenceKeys.particleWeatherMode] ?: "AUTO"
                    val manualWeather = runCatching { ParticleWeather.valueOf(weatherMode) }.getOrNull()
                    val mode = runCatching { ClockMode.valueOf(preferences[PreferenceKeys.clockMode] ?: ClockMode.CLOCK.name) }
                        .getOrDefault(ClockMode.CLOCK)
                    val preset = runCatching { ThemePreset.valueOf(preferences[PreferenceKeys.themePreset] ?: ThemePreset.AUTO.name) }
                        .getOrDefault(ThemePreset.AUTO)
                    val persistedPomodoroFocus = preferences[PreferenceKeys.pomodoroFocusMinutes] ?: state.pomodoroFocusMinutes
                    val persistedPomodoroBreak = preferences[PreferenceKeys.pomodoroBreakMinutes] ?: state.pomodoroBreakMinutes
                    val persistedCountdown = preferences[PreferenceKeys.countdownDurationMinutes] ?: state.countdownDurationMinutes
                    val sleepMode = runCatching { SleepSoundMode.valueOf(preferences[PreferenceKeys.sleepSoundMode] ?: SleepSoundMode.RAIN.name) }
                        .getOrDefault(SleepSoundMode.RAIN)
                    state.copy(
                        isParallaxEnabled = preferences[PreferenceKeys.parallax] ?: state.isParallaxEnabled,
                        isParticleWeatherAuto = manualWeather == null,
                        particleWeather = manualWeather ?: state.particleWeather,
                        isCatSystemEnabled = preferences[PreferenceKeys.cats] ?: state.isCatSystemEnabled,
                        is24HourFormat = preferences[PreferenceKeys.is24Hour] ?: state.is24HourFormat,
                        selectedFont = selectedFont,
                        clockMode = mode,
                        pomodoroFocusMinutes = persistedPomodoroFocus,
                        pomodoroBreakMinutes = persistedPomodoroBreak,
                        pomodoroRemainingSeconds = if (!state.timerRunning && state.clockMode == ClockMode.POMODORO && persistedPomodoroFocus != state.pomodoroFocusMinutes) {
                            persistedPomodoroFocus * 60
                        } else {
                            state.pomodoroRemainingSeconds
                        },
                        countdownDurationMinutes = persistedCountdown,
                        countdownRemainingSeconds = if (!state.timerRunning && state.clockMode == ClockMode.COUNTDOWN && persistedCountdown != state.countdownDurationMinutes) {
                            persistedCountdown * 60
                        } else {
                            state.countdownRemainingSeconds
                        },
                        hourlyChimeEnabled = preferences[PreferenceKeys.hourlyChime] ?: state.hourlyChimeEnabled,
                        dailyAlarmEnabled = preferences[PreferenceKeys.dailyAlarmEnabled] ?: state.dailyAlarmEnabled,
                        dailyAlarmHour = preferences[PreferenceKeys.dailyAlarmHour] ?: state.dailyAlarmHour,
                        dailyAlarmMinute = preferences[PreferenceKeys.dailyAlarmMinute] ?: state.dailyAlarmMinute,
                        selectedThemePreset = preset,
                        sleepSoundMode = sleepMode,
                        whiteNoiseEnabled = preferences[PreferenceKeys.whiteNoise] ?: state.whiteNoiseEnabled
                    )
                }
                val soundState = _uiState.value
                if (soundState.whiteNoiseEnabled) {
                    if (sleepSoundEndsAtElapsedMs == 0L) {
                        sleepSoundEndsAtElapsedMs = SystemClock.elapsedRealtime() + SLEEP_SOUND_DURATION_MS
                    }
                    whiteNoisePlayer.start(soundState.sleepSoundMode)
                    startSleepSoundCountdown()
                } else {
                    stopSleepSound(clearPersistedState = false)
                }
            }
        }
    }

    private fun startClockTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch(Dispatchers.Default) {
            var lastMinute = -1
            var nextWeatherChangeEpochSec = 0L
            var activeWeather = _uiState.value.particleWeather
            val recentAutoWeathers = ArrayDeque<ParticleWeather>()

            fun rememberAutoWeather(weather: ParticleWeather) {
                recentAutoWeathers.addLast(weather)
                while (recentAutoWeathers.size > 3) {
                    recentAutoWeathers.removeFirst()
                }
            }

            rememberAutoWeather(activeWeather)
            while (isActive) {
                val now = ZonedDateTime.now()
                val currentMinute = now.minute
                val hour24 = now.hour
                val second = now.second
                val nowEpochSec = now.toEpochSecond()
                val weatherAuto = _uiState.value.isParticleWeatherAuto
                val isNightQuietHours = hour24 >= 23 || hour24 < 7
                val resolvedPreset = _uiState.value.selectedThemePreset.resolveActive(hour24)
                if (weatherAuto) {
                    if (activeWeather != _uiState.value.particleWeather) {
                        activeWeather = _uiState.value.particleWeather
                    }
                    val weatherCandidates = weatherCandidatesForPreset(
                        preset = resolvedPreset,
                        hour24 = hour24,
                        allowBrightWeather = !isNightQuietHours
                    )
                    if (isNightQuietHours && activeWeather.isBrightWeather()) {
                        activeWeather = pickRandomWeather(
                            excluding = activeWeather,
                            candidates = weatherCandidates,
                            preferCalm = true,
                            recent = recentAutoWeathers
                        )
                        rememberAutoWeather(activeWeather)
                        nextWeatherChangeEpochSec = nowEpochSec + WEATHER_ROTATION_INTERVAL_SEC
                    }
                    if (nextWeatherChangeEpochSec == 0L || nowEpochSec >= nextWeatherChangeEpochSec) {
                        activeWeather = pickRandomWeather(
                            excluding = activeWeather,
                            candidates = weatherCandidates,
                            preferCalm = isNightQuietHours,
                            recent = recentAutoWeathers
                        )
                        rememberAutoWeather(activeWeather)
                        nextWeatherChangeEpochSec = nowEpochSec + WEATHER_ROTATION_INTERVAL_SEC
                    }
                } else {
                    activeWeather = _uiState.value.particleWeather
                    nextWeatherChangeEpochSec = nowEpochSec + WEATHER_ROTATION_INTERVAL_SEC
                }

                var shouldPlayReminder = false
                var shouldStartDailyAlarm = false
                var reminderCue = ReminderCue.DEFAULT
                var edgeLightMode: EdgeLightMode? = null
                var edgeLightDurationMs: Long? = null
                _uiState.update { state ->
                    var workingState = state
                    if (workingState.activeThemePreset != resolvedPreset) {
                        // In AUTO mode the preset switches automatically on a schedule;
                        // take the opportunity to also randomly pick a fresh font.
                        val isAutoMode = workingState.selectedThemePreset == ThemePreset.AUTO
                        workingState = applyThemePresetProfile(
                            state = workingState.copy(activeThemePreset = resolvedPreset),
                            preset = resolvedPreset,
                            randomizeFont = isAutoMode
                        )
                    }

                    val modeTick = advanceModeState(workingState)
                    workingState = modeTick.state
                    if (modeTick.triggerSound) {
                        shouldPlayReminder = true
                    }
                    edgeLightMode = modeTick.edgeLightMode
                    edgeLightDurationMs = modeTick.edgeLightDurationMs

                    val burnInOffset = if (currentMinute != lastMinute) {
                        Offset(
                            x = random.nextFloat() * 20f - 10f,
                            y = random.nextFloat() * 20f - 10f
                        )
                    } else {
                        workingState.burnInOffset
                    }
                    val displayHour = if (workingState.is24HourFormat) {
                        hour24.toString().padStart(2, '0')
                    } else {
                        val h12 = if (hour24 % 12 == 0) 12 else hour24 % 12
                        h12.toString().padStart(2, '0')
                    }
                    val amPm = if (workingState.is24HourFormat) "" else now.format(amPmFormatter)
                    val hourlyMarker = "${now.toLocalDate()}-$hour24"
                    val dailyMarker = "${now.toLocalDate()}-${workingState.dailyAlarmHour}-${workingState.dailyAlarmMinute}"
                    var companionMessage = modeTick.message ?: workingState.companionMessage
                    val snoozeRemainingSeconds = dailyAlarmSnoozeDeadlineElapsedMs
                        ?.let { deadline ->
                            (((deadline - SystemClock.elapsedRealtime()) + 999L) / 1000L)
                                .coerceAtLeast(0L)
                                .toInt()
                        }
                        ?: 0

                    if (workingState.hourlyChimeEnabled && currentMinute == 0 && second == 0 && hourlyMarker != lastHourlyChimeMarker) {
                        lastHourlyChimeMarker = hourlyMarker
                        shouldPlayReminder = true
                        reminderCue = ReminderCue.HOURLY_CHIME
                        companionMessage = appContext.getString(R.string.reminder_hourly_chime, displayHour)
                    }
                    if (
                        workingState.dailyAlarmEnabled &&
                        !workingState.isDailyAlarmRinging &&
                        hour24 == workingState.dailyAlarmHour &&
                        currentMinute == workingState.dailyAlarmMinute &&
                        second == 0 &&
                        dailyMarker != lastDailyAlarmMarker
                    ) {
                        lastDailyAlarmMarker = dailyMarker
                        shouldStartDailyAlarm = true
                        companionMessage = appContext.getString(R.string.reminder_alarm_now)
                    }

                    workingState.copy(
                        hour = displayHour,
                        minute = currentMinute.toString().padStart(2, '0'),
                        second = second.toString().padStart(2, '0'),
                        currentHour24 = hour24,
                        amPm = amPm,
                        date = now.format(dateFormatter),
                        dayOfWeek = now.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                        // Avoid bright/sunny background imagery during quiet night hours.
                        backgroundRes = if (isNightQuietHours) null else R.drawable.jiguang,
                        particleWeather = if (workingState.isParticleWeatherAuto) activeWeather else workingState.particleWeather,
                        burnInOffset = burnInOffset,
                        dailyAlarmSnoozeRemainingSeconds = snoozeRemainingSeconds,
                        companionMessage = companionMessage
                    )
                }
                if (shouldPlayReminder) {
                    playReminderCue(reminderCue)
                }
                if (shouldStartDailyAlarm) {
                    startDailyAlarm()
                }
                if (edgeLightMode != null) {
                    showEdgeLight(edgeLightMode!!, edgeLightDurationMs)
                }
                if (currentMinute != lastMinute) {
                    lastMinute = currentMinute
                }
                delay(1000)
            }
        }
    }

    private fun stopClockTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun pickRandomWeather(
        excluding: ParticleWeather,
        candidates: List<ParticleWeather>,
        preferCalm: Boolean = false,
        recent: Collection<ParticleWeather> = emptyList()
    ): ParticleWeather {
        val allowed = candidates.ifEmpty { ParticleWeather.entries.toList() }
        val options = allowed.filter { it != excluding }.ifEmpty { allowed }
        if (options.isEmpty()) return excluding

        val weightedOptions = buildList {
            options.forEach { weather ->
                val baseWeight = weather.weight(preferCalm = preferCalm)
                // Lower chance of repeating very recent weather types.
                val adjustedWeight = if (recent.contains(weather)) {
                    (baseWeight - 1).coerceAtLeast(1)
                } else {
                    baseWeight + 1
                }
                val weight = adjustedWeight.coerceAtLeast(1)
                repeat(weight) { add(weather) }
            }
        }
        return weightedOptions[random.nextInt(weightedOptions.size)]
    }

    private fun ParticleWeather.isBrightWeather(): Boolean {
        return this == ParticleWeather.SUNNY || this == ParticleWeather.CLOUDY
    }

    private fun weatherCandidatesForPreset(
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
        return if (allowBrightWeather) {
            base
        } else {
            base.filter { it.isNightSafeWeather() }
        }
    }

    private fun ParticleWeather.isNightSafeWeather(): Boolean {
        return when (this) {
            ParticleWeather.SUNNY, ParticleWeather.CLOUDY, ParticleWeather.HAIL, ParticleWeather.BLIZZARD -> false
            ParticleWeather.FOG, ParticleWeather.DRIZZLE, ParticleWeather.RAIN, ParticleWeather.SNOW, ParticleWeather.WIND -> true
        }
    }

    private fun ParticleWeather.weight(preferCalm: Boolean): Int {
        if (!preferCalm) {
            return when (this) {
                ParticleWeather.DRIZZLE -> 4
                ParticleWeather.FOG -> 3
                ParticleWeather.CLOUDY, ParticleWeather.RAIN, ParticleWeather.WIND, ParticleWeather.SNOW -> 2
                ParticleWeather.SUNNY, ParticleWeather.HAIL, ParticleWeather.BLIZZARD -> 1
            }
        }
        return when (this) {
            ParticleWeather.DRIZZLE, ParticleWeather.FOG -> 5
            ParticleWeather.RAIN, ParticleWeather.SNOW -> 3
            ParticleWeather.WIND -> 2
            ParticleWeather.CLOUDY -> 1
            ParticleWeather.SUNNY, ParticleWeather.HAIL, ParticleWeather.BLIZZARD -> 1
        }
    }

    private data class ModeTickResult(
        val state: ClockState,
        val message: String? = null,
        val triggerSound: Boolean = false,
        val edgeLightMode: EdgeLightMode? = null,
        val edgeLightDurationMs: Long? = null
    )

    private fun advanceModeState(state: ClockState): ModeTickResult {
        if (!state.timerRunning) return ModeTickResult(state)
        return when (state.clockMode) {
            ClockMode.CLOCK -> ModeTickResult(state)
            ClockMode.POMODORO -> {
                val nextRemaining = state.pomodoroRemainingSeconds - 1
                val focusedSeconds = state.focusedSecondsToday + if (state.pomodoroPhase == PomodoroPhase.FOCUS) 1 else 0
                if (nextRemaining <= 0) {
                    if (state.pomodoroPhase == PomodoroPhase.FOCUS) {
                        ModeTickResult(
                            state.copy(
                                pomodoroPhase = PomodoroPhase.BREAK,
                                pomodoroRemainingSeconds = state.pomodoroBreakMinutes * 60,
                                focusedSecondsToday = focusedSeconds,
                                completedPomodoros = state.completedPomodoros + 1
                            ),
                            message = appContext.getString(R.string.companion_focus_done),
                            triggerSound = true,
                            edgeLightMode = EdgeLightMode.TIMER_ALERT,
                            edgeLightDurationMs = TIMER_ALERT_EDGE_MS
                        )
                    } else {
                        ModeTickResult(
                            state.copy(
                                pomodoroPhase = PomodoroPhase.FOCUS,
                                pomodoroRemainingSeconds = state.pomodoroFocusMinutes * 60,
                                completedBreaks = state.completedBreaks + 1
                            ),
                            message = appContext.getString(R.string.companion_break_done),
                            triggerSound = true,
                            edgeLightMode = EdgeLightMode.TIMER_ALERT,
                            edgeLightDurationMs = TIMER_ALERT_EDGE_MS
                        )
                    }
                } else {
                    ModeTickResult(
                        state.copy(
                            pomodoroRemainingSeconds = nextRemaining,
                            focusedSecondsToday = focusedSeconds
                        )
                    )
                }
            }
            ClockMode.COUNTDOWN -> {
                val nextRemaining = state.countdownRemainingSeconds - 1
                if (nextRemaining <= 0) {
                    ModeTickResult(
                        state.copy(
                            countdownRemainingSeconds = 0,
                            timerRunning = false
                        ),
                        message = appContext.getString(R.string.companion_countdown_done),
                        triggerSound = true,
                        edgeLightMode = EdgeLightMode.TIMER_ALERT,
                        edgeLightDurationMs = TIMER_ALERT_EDGE_MS
                    )
                } else {
                    ModeTickResult(state.copy(countdownRemainingSeconds = nextRemaining))
                }
            }
            ClockMode.STOPWATCH -> {
                val nextElapsed = state.stopwatchElapsedSeconds + 1
                val needsBreakNudge = nextElapsed > 0 && nextElapsed % BREAK_NUDGE_INTERVAL_SEC == 0
                ModeTickResult(
                    state.copy(stopwatchElapsedSeconds = nextElapsed),
                    message = if (needsBreakNudge) appContext.getString(R.string.companion_break_nudge) else null,
                    triggerSound = false,
                    edgeLightMode = if (needsBreakNudge) EdgeLightMode.BREAK_REMINDER else null,
                    edgeLightDurationMs = if (needsBreakNudge) BREAK_NUDGE_EDGE_MS else null
                )
            }
        }
    }

    private fun applyThemePresetProfile(
        state: ClockState,
        preset: ThemePreset,
        randomizeFont: Boolean = false
    ): ClockState {
        val profile = preset.profile()
        val presetFont = if (randomizeFont) {
            AvailableClockFonts.random()
        } else {
            AvailableClockFonts.find { it.name == profile.fontName } ?: state.selectedFont
        }
        return state.copy(
            selectedFont = presetFont,
            particleWeather = if (state.isParticleWeatherAuto) state.particleWeather else profile.weather,
            isParticleWeatherAuto = state.isParticleWeatherAuto,
            isCatSystemEnabled = profile.catsEnabled,
            isSoundButtonVisible = profile.soundButtonVisible,
            activeThemePreset = preset
        )
    }

    private fun playReminderCue(cue: ReminderCue = ReminderCue.DEFAULT) {
        when (cue) {
            ReminderCue.DEFAULT -> playAudio()
            ReminderCue.HOURLY_CHIME -> playSystemChime()
        }
    }

    private fun playSystemChime() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        runCatching {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
        }
        tone.release()
    }

    private fun startDailyAlarm() {
        dailyAlarmJob?.cancel()
        alarmTonePlayer.start()
        showEdgeLight(EdgeLightMode.TIMER_ALERT, DAILY_ALARM_RING_MS)
        _uiState.update {
            it.copy(
                isDailyAlarmRinging = true,
                companionMessage = appContext.getString(R.string.reminder_alarm_now)
            )
        }
        dailyAlarmJob = viewModelScope.launch {
            delay(DAILY_ALARM_RING_MS)
            stopDailyAlarmSound()
            clearEdgeLight()
            _uiState.update { it.copy(isDailyAlarmRinging = false) }
        }
    }

    private fun stopDailyAlarmSound() {
        dailyAlarmJob?.cancel()
        dailyAlarmJob = null
        alarmTonePlayer.stop()
    }

    fun fetchLocation(hasLocationPermission: Boolean) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch(Dispatchers.IO) {
            val city = if (hasLocationPermission) fetchCityFromDeviceLocation() else null
            _uiState.update {
                it.copy(location = city?.uppercase(locale) ?: appContext.getString(R.string.location_unavailable))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCityFromDeviceLocation(): String? {
        return runCatching {
            val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                    bestLocation = location
                }
            }
            bestLocation ?: requestFreshLocation(locationManager)
        }.getOrNull()?.let { location ->
            reverseGeocodeCity(location)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(locationManager: LocationManager): Location? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                locationManager.getCurrentLocation(
                    LocationManager.FUSED_PROVIDER,
                    cancellationSignal,
                    appContext.mainExecutor
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            suspendCancellableCoroutine { continuation ->
                val criteria = android.location.Criteria().apply {
                    accuracy = android.location.Criteria.ACCURACY_COARSE
                }
                val provider = locationManager.getBestProvider(criteria, true)
                    ?: locationManager.getProviders(true).firstOrNull()
                if (provider == null) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }
                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    }

    private suspend fun reverseGeocodeCity(location: Location): String? {
        val geocoder = Geocoder(appContext, locale)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val city = addresses.firstOrNull()?.locality
                                ?: addresses.firstOrNull()?.subAdminArea
                                ?: addresses.firstOrNull()?.adminArea
                            if (continuation.isActive) {
                                continuation.resume(city)
                            }
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                )
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            addresses?.firstOrNull()?.locality
                ?: addresses?.firstOrNull()?.subAdminArea
                ?: addresses?.firstOrNull()?.adminArea
        }
    }

    fun playAudio() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(appContext, R.raw.audio_file)
            mediaPlayer?.setVolume(1f, 1f)
            runCatching {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(mediaPlayer!!.audioSessionId).apply {
                    setTargetGain(1200)
                    enabled = true
                }
            }
        }
        mediaPlayer?.apply {
            if (isPlaying) {
                seekTo(0)
            }
            setVolume(1f, 1f)
            start()
        }
    }

    fun openSettings() = _uiState.update { it.copy(isSettingsVisible = true) }
    fun closeSettings() = _uiState.update { it.copy(isSettingsVisible = false) }

    fun setFont(font: ClockFont) {
        _uiState.update { it.copy(selectedFont = font) }
        persistSetting { this[PreferenceKeys.selectedFont] = font.name }
    }

    fun setClockMode(mode: ClockMode) {
        _uiState.update { state ->
            when (mode) {
                ClockMode.CLOCK -> state.copy(clockMode = mode, timerRunning = false, companionMessage = appContext.getString(R.string.mode_clock))
                ClockMode.POMODORO -> state.copy(
                    clockMode = mode,
                    timerRunning = false,
                    pomodoroPhase = PomodoroPhase.FOCUS,
                    pomodoroRemainingSeconds = state.pomodoroFocusMinutes * 60,
                    companionMessage = appContext.getString(R.string.companion_focus_ready)
                )
                ClockMode.COUNTDOWN -> state.copy(
                    clockMode = mode,
                    timerRunning = false,
                    countdownRemainingSeconds = state.countdownDurationMinutes * 60,
                    companionMessage = appContext.getString(R.string.companion_countdown_ready)
                )
                ClockMode.STOPWATCH -> state.copy(
                    clockMode = mode,
                    timerRunning = false,
                    stopwatchElapsedSeconds = 0,
                    companionMessage = appContext.getString(R.string.companion_stopwatch_ready)
                )
            }
        }
        if (mode != ClockMode.STOPWATCH) {
            clearEdgeLight()
        }
        persistSetting { this[PreferenceKeys.clockMode] = mode.name }
    }

    fun toggleModeRunning() {
        var nextState: ClockState? = null
        _uiState.update { state ->
            if (state.clockMode == ClockMode.CLOCK) {
                state
            } else {
                state.copy(
                    timerRunning = !state.timerRunning,
                    companionMessage = if (!state.timerRunning) {
                        appContext.getString(R.string.companion_timer_started)
                    } else {
                        appContext.getString(R.string.companion_timer_paused)
                    }
                ).also { nextState = it }
            }
        }
        nextState?.let { updated ->
            when (updated.clockMode) {
                ClockMode.STOPWATCH -> if (updated.timerRunning) showEdgeLight(EdgeLightMode.STOPWATCH_ACTIVE) else clearEdgeLight()
                ClockMode.CLOCK -> Unit
                else -> if (!updated.timerRunning) clearEdgeLight()
            }
        }
    }

    fun resetActiveMode() {
        _uiState.update { state ->
            when (state.clockMode) {
                ClockMode.CLOCK -> state
                ClockMode.POMODORO -> state.copy(
                    timerRunning = false,
                    pomodoroPhase = PomodoroPhase.FOCUS,
                    pomodoroRemainingSeconds = state.pomodoroFocusMinutes * 60,
                    companionMessage = appContext.getString(R.string.companion_focus_ready)
                )
                ClockMode.COUNTDOWN -> state.copy(
                    timerRunning = false,
                    countdownRemainingSeconds = state.countdownDurationMinutes * 60,
                    companionMessage = appContext.getString(R.string.companion_countdown_ready)
                )
                ClockMode.STOPWATCH -> state.copy(
                    timerRunning = false,
                    stopwatchElapsedSeconds = 0,
                    companionMessage = appContext.getString(R.string.companion_stopwatch_ready)
                )
            }
        }
        clearEdgeLight()
    }

    fun setPomodoroFocusMinutes(minutes: Int) {
        val next = minutes.coerceIn(5, 90)
        if (_uiState.value.pomodoroFocusMinutes == next) return
        _uiState.update { state ->
            state.copy(
                pomodoroFocusMinutes = next,
                pomodoroRemainingSeconds = if (!state.timerRunning && state.pomodoroPhase == PomodoroPhase.FOCUS) {
                    next * 60
                } else {
                    state.pomodoroRemainingSeconds
                }
            )
        }
        persistSetting { this[PreferenceKeys.pomodoroFocusMinutes] = next }
    }

    fun setPomodoroBreakMinutes(minutes: Int) {
        val next = minutes.coerceIn(1, 30)
        if (_uiState.value.pomodoroBreakMinutes == next) return
        _uiState.update { state ->
            state.copy(
                pomodoroBreakMinutes = next,
                pomodoroRemainingSeconds = if (!state.timerRunning && state.pomodoroPhase == PomodoroPhase.BREAK) {
                    next * 60
                } else {
                    state.pomodoroRemainingSeconds
                }
            )
        }
        persistSetting { this[PreferenceKeys.pomodoroBreakMinutes] = next }
    }

    fun setCountdownDurationMinutes(minutes: Int) {
        val next = minutes.coerceIn(1, 180)
        if (_uiState.value.countdownDurationMinutes == next) return
        _uiState.update { state ->
            state.copy(
                countdownDurationMinutes = next,
                countdownRemainingSeconds = if (!state.timerRunning) next * 60 else state.countdownRemainingSeconds
            )
        }
        persistSetting { this[PreferenceKeys.countdownDurationMinutes] = next }
    }

    fun toggleParallax(enabled: Boolean) {
        if (!enabled) {
            basePitch = null
            baseRoll = null
            smoothParallax = Offset.Zero
        }
        _uiState.update { it.copy(isParallaxEnabled = enabled, parallaxOffset = if (enabled) it.parallaxOffset else Offset.Zero) }
        persistSetting { this[PreferenceKeys.parallax] = enabled }
    }

    fun setParticleWeatherAuto(auto: Boolean) {
        _uiState.update { state ->
            if (auto) {
                val hour24 = state.currentHour24
                val resolvedPreset = state.selectedThemePreset.resolveActive(hour24)
                val isNightQuietHours = hour24 >= 23 || hour24 < 7
                val weatherCandidates = weatherCandidatesForPreset(
                    preset = resolvedPreset,
                    hour24 = hour24,
                    allowBrightWeather = !isNightQuietHours
                )
                state.copy(
                    isParticleWeatherAuto = true,
                    particleWeather = pickRandomWeather(
                        excluding = state.particleWeather,
                        candidates = weatherCandidates,
                        preferCalm = isNightQuietHours
                    )
                )
            } else {
                state.copy(isParticleWeatherAuto = false)
            }
        }
        persistSetting {
            this[PreferenceKeys.particleWeatherMode] = if (auto) "AUTO" else _uiState.value.particleWeather.name
        }
    }

    fun setParticleWeather(weather: ParticleWeather) {
        _uiState.update { it.copy(isParticleWeatherAuto = false, particleWeather = weather) }
        persistSetting {
            this[PreferenceKeys.particleWeatherMode] = weather.name
        }
    }

    fun toggleCats(enabled: Boolean) {
        _uiState.update { it.copy(isCatSystemEnabled = enabled) }
        persistSetting { this[PreferenceKeys.cats] = enabled }
    }

    fun toggle24HourFormat(enabled: Boolean) {
        _uiState.update { it.copy(is24HourFormat = enabled) }
        persistSetting { this[PreferenceKeys.is24Hour] = enabled }
    }

    fun toggleHourlyChime(enabled: Boolean) {
        _uiState.update { it.copy(hourlyChimeEnabled = enabled) }
        persistSetting { this[PreferenceKeys.hourlyChime] = enabled }
    }

    fun toggleDailyAlarm(enabled: Boolean) {
        _uiState.update { it.copy(dailyAlarmEnabled = enabled) }
        if (!enabled) {
            dismissDailyAlarm()
        }
        persistSetting { this[PreferenceKeys.dailyAlarmEnabled] = enabled }
    }

    fun setDailyAlarmHour(hour: Int) {
        val normalizedHour = hour.floorMod(24)
        if (_uiState.value.dailyAlarmHour == normalizedHour) return
        _uiState.update { it.copy(dailyAlarmHour = normalizedHour) }
        persistSetting { this[PreferenceKeys.dailyAlarmHour] = normalizedHour }
    }

    fun setDailyAlarmMinute(minute: Int) {
        val normalizedMinute = minute.floorMod(60)
        if (_uiState.value.dailyAlarmMinute == normalizedMinute) return
        _uiState.update { it.copy(dailyAlarmMinute = normalizedMinute) }
        persistSetting { this[PreferenceKeys.dailyAlarmMinute] = normalizedMinute }
    }

    fun snoozeDailyAlarm() {
        stopDailyAlarmSound()
        clearEdgeLight()
        val snoozeSeconds = (DAILY_ALARM_SNOOZE_MS / 1000L).toInt()
        dailyAlarmSnoozeDeadlineElapsedMs = SystemClock.elapsedRealtime() + DAILY_ALARM_SNOOZE_MS
        _uiState.update {
            it.copy(
                isDailyAlarmRinging = false,
                dailyAlarmSnoozeRemainingSeconds = snoozeSeconds,
                companionMessage = appContext.getString(R.string.alarm_snoozed_message)
            )
        }
        dailyAlarmSnoozeJob?.cancel()
        dailyAlarmSnoozeJob = viewModelScope.launch {
            delay(DAILY_ALARM_SNOOZE_MS)
            dailyAlarmSnoozeDeadlineElapsedMs = null
            _uiState.update { it.copy(dailyAlarmSnoozeRemainingSeconds = 0) }
            if (_uiState.value.dailyAlarmEnabled) {
                startDailyAlarm()
            }
        }
    }

    fun dismissDailyAlarm() {
        clearDailyAlarmSnooze()
        stopDailyAlarmSound()
        clearEdgeLight()
        _uiState.update {
            it.copy(
                isDailyAlarmRinging = false,
                dailyAlarmSnoozeRemainingSeconds = 0,
                companionMessage = appContext.getString(R.string.alarm_dismissed_message)
            )
        }
    }

    private fun clearDailyAlarmSnooze() {
        dailyAlarmSnoozeJob?.cancel()
        dailyAlarmSnoozeJob = null
        dailyAlarmSnoozeDeadlineElapsedMs = null
    }

    fun setThemePreset(preset: ThemePreset) {
        _uiState.update { state ->
            applyThemePresetProfile(
                state = state.copy(selectedThemePreset = preset, activeThemePreset = preset.resolveActive(state.currentHour24)),
                preset = preset.resolveActive(state.currentHour24)
            )
        }
        persistSetting { this[PreferenceKeys.themePreset] = preset.name }
    }

    private fun showEdgeLight(mode: EdgeLightMode, durationMs: Long? = null) {
        edgeLightJob?.cancel()
        _uiState.update { it.copy(edgeLightMode = mode) }
        if (durationMs != null) {
            edgeLightJob = viewModelScope.launch {
                delay(durationMs)
                _uiState.update { state -> state.withClearedEdgeLight() }
            }
        } else {
            edgeLightJob = null
        }
    }

    private fun clearEdgeLight() {
        edgeLightJob?.cancel()
        edgeLightJob = null
        _uiState.update { state -> state.withClearedEdgeLight() }
    }

    private fun ClockState.withClearedEdgeLight(): ClockState {
        return if (clockMode == ClockMode.STOPWATCH && timerRunning) {
            copy(edgeLightMode = EdgeLightMode.STOPWATCH_ACTIVE)
        } else {
            copy(edgeLightMode = null)
        }
    }

    fun toggleWhiteNoise(enabled: Boolean) {
        if (enabled) {
            sleepSoundEndsAtElapsedMs = SystemClock.elapsedRealtime() + SLEEP_SOUND_DURATION_MS
            _uiState.update {
                it.copy(
                    whiteNoiseEnabled = true,
                    sleepSoundRemainingSeconds = (SLEEP_SOUND_DURATION_MS / 1000L).toInt()
                )
            }
            whiteNoisePlayer.start(_uiState.value.sleepSoundMode)
            startSleepSoundCountdown()
        } else {
            stopSleepSound(clearPersistedState = true)
        }
        persistSetting { this[PreferenceKeys.whiteNoise] = enabled }
    }

    fun setSleepSoundMode(mode: SleepSoundMode) {
        _uiState.update { it.copy(sleepSoundMode = mode) }
        if (_uiState.value.whiteNoiseEnabled) {
            whiteNoisePlayer.start(mode)
        }
        persistSetting { this[PreferenceKeys.sleepSoundMode] = mode.name }
    }

    private fun startSleepSoundCountdown() {
        if (sleepSoundJob?.isActive == true) return
        sleepSoundJob = viewModelScope.launch {
            while (isActive && _uiState.value.whiteNoiseEnabled) {
                val remaining = ((sleepSoundEndsAtElapsedMs - SystemClock.elapsedRealtime()) / 1000L)
                    .toInt()
                    .coerceAtLeast(0)
                _uiState.update { it.copy(sleepSoundRemainingSeconds = remaining) }
                if (remaining <= 0) {
                    stopSleepSound(clearPersistedState = true)
                    persistSetting { this[PreferenceKeys.whiteNoise] = false }
                    break
                }
                delay(1_000L)
            }
        }
    }

    private fun stopSleepSound(clearPersistedState: Boolean) {
        sleepSoundJob?.cancel()
        sleepSoundJob = null
        sleepSoundEndsAtElapsedMs = 0L
        whiteNoisePlayer.stop()
        _uiState.update { it.copy(whiteNoiseEnabled = false, sleepSoundRemainingSeconds = 0) }
        if (clearPersistedState) {
            persistSetting { this[PreferenceKeys.whiteNoise] = false }
        }
    }

    fun setAppActive(active: Boolean) {
        if (isAppActive == active) return
        isAppActive = active
        if (active) {
            startClockTicker()
            registerRotationSensorIfNeeded()
        } else {
            stopClockTicker()
            unregisterRotationSensor()
        }
    }

    private fun registerRotationSensorIfNeeded() {
        if (sensorRegistered) return
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            sensorRegistered = true
        }
    }

    private fun unregisterRotationSensor() {
        if (!sensorRegistered) return
        sensorManager.unregisterListener(this)
        sensorRegistered = false
        basePitch = null
        baseRoll = null
        smoothParallax = Offset.Zero
    }

    private fun persistSetting(block: MutablePreferences.() -> Unit) {
        viewModelScope.launch {
            appContext.dataStore.edit { preferences ->
                block(preferences)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR || !_uiState.value.isParallaxEnabled) {
            return
        }

        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastSensorUpdateTimeMs < 16L) {
            return
        }
        lastSensorUpdateTimeMs = nowMs

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        if (orientation.any { !it.isFinite() }) {
            return
        }

        val currentPitch = orientation[1]
        val currentRoll = orientation[2]
        if (!currentPitch.isFinite() || !currentRoll.isFinite()) {
            return
        }
        if (basePitch == null || baseRoll == null) {
            basePitch = currentPitch
            baseRoll = currentRoll
        }

        val targetOffset = Offset(
            x = (currentRoll - (baseRoll ?: currentRoll)) * 45f,
            y = (currentPitch - (basePitch ?: currentPitch)) * 45f
        ).sanitize()
        val smoothing = 0.18f
        smoothParallax = Offset(
            x = smoothParallax.x + (targetOffset.x - smoothParallax.x) * smoothing,
            y = smoothParallax.y + (targetOffset.y - smoothParallax.y) * smoothing
        ).sanitize()

        if (!smoothParallax.x.isFinite() || !smoothParallax.y.isFinite()) {
            smoothParallax = Offset.Zero
            return
        }

        val current = _uiState.value.parallaxOffset
        if (abs(smoothParallax.x - current.x) < 0.15f && abs(smoothParallax.y - current.y) < 0.15f) {
            return
        }
        _uiState.update { it.copy(parallaxOffset = smoothParallax.sanitize()) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        edgeLightJob?.cancel()
        sleepSoundJob?.cancel()
        dailyAlarmJob?.cancel()
        dailyAlarmSnoozeJob?.cancel()
        stopClockTicker()
        unregisterRotationSensor()
        runCatching { appContext.unregisterReceiver(batteryReceiver) }
        locationJob?.cancel()
        mediaPlayer?.release()
        loudnessEnhancer?.release()
        whiteNoisePlayer.stop()
        alarmTonePlayer.stop()
        mediaPlayer = null
        loudnessEnhancer = null
        super.onCleared()
    }
}

private const val SLEEP_SOUND_DURATION_MS = 60L * 60L * 1000L
private const val DAILY_ALARM_RING_MS = 9L * 60L * 1000L
private const val DAILY_ALARM_SNOOZE_MS = 10L * 60L * 1000L
private const val WEATHER_ROTATION_INTERVAL_SEC = 8L * 60L * 60L
private const val TIMER_ALERT_EDGE_MS = 5_000L
private const val BREAK_NUDGE_INTERVAL_SEC = 30 * 60
private const val BREAK_NUDGE_EDGE_MS = 10_000L

private fun Offset.sanitize(): Offset = Offset(
    x = x.takeIf { it.isFinite() } ?: 0f,
    y = y.takeIf { it.isFinite() } ?: 0f
)

private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod
