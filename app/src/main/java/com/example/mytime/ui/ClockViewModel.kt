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
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.BatteryManager
import android.os.Build
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject
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
    val dailyAlarmEnabled = booleanPreferencesKey("daily_alarm_enabled")
    val dailyAlarmHour = intPreferencesKey("daily_alarm_hour")
    val dailyAlarmMinute = intPreferencesKey("daily_alarm_minute")
    val themePreset = stringPreferencesKey("theme_preset")
    val sleepSoundMode = stringPreferencesKey("sleep_sound_mode")
    val whiteNoise = booleanPreferencesKey("white_noise")
}

class ClockViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val _uiState = MutableStateFlow(ClockState())
    val uiState: StateFlow<ClockState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val whiteNoisePlayer = WhiteNoisePlayer()
    private val alarmTonePlayer = AlarmTonePlayer()
    private var alarmPlaybackJob: Job? = null
    private var sleepPlaybackJob: Job? = null
    private var playingSleepMode: SleepSoundMode? = null
    private val appContext: Context
        get() = getApplication<Application>().applicationContext
    private val sensorManager = application.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private val locale = Locale.getDefault()
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val amPmFormatter = DateTimeFormatter.ofPattern("a", locale)
    private val random = Random.Default

    private var basePitch: Float? = null
    private var baseRoll: Float? = null
    private var deviceLatitude: Double? = null
    private var deviceLongitude: Double? = null
    private var smoothParallax = Offset.Zero
    private var lastSensorUpdateTimeMs = 0L
    private var locationJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var locationRetryJob: Job? = null
    private var locationRetryAttempt = 0
    private var weatherJob: Job? = null
    private var tickerJob: Job? = null
    private var edgeLightJob: Job? = null
    private var sleepSoundJob: Job? = null
    private var dailyAlarmJob: Job? = null
    private var timerAlertJob: Job? = null
    private var dailyAlarmSnoozeJob: Job? = null
    private var dailyAlarmSnoozeDeadlineElapsedMs: Long? = null
    private var sleepSoundEndsAtElapsedMs: Long = 0L
    private var sensorRegistered = false
    private var isAppActive = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            _uiState.update { state ->
                state.copy(
                    batteryLevel = if (level >= 0 && scale > 0) "${(level * 100 / scale)}%" else state.batteryLevel,
                    isCharging = charging
                )
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

    private fun startDiagnostics() {
        if (diagnosticsJob?.isActive == true) return
        diagnosticsJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val state = _uiState.value
                DiagnosticLogger.snapshot("mode=${state.clockMode}, running=${state.timerRunning}, weather=${state.particleWeather}, theme=${state.activeThemePreset}, cats=${state.isCatSystemEnabled}, parallax=${state.isParallaxEnabled}")
                delay(5 * 60_000L)
            }
        }
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
                    startSleepPlayback(soundState.sleepSoundMode)
                    startSleepSoundCountdown()
                } else {
                    stopSleepSound(clearPersistedState = false)
                }
            }
        }
    }

    private fun startClockTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            var lastMinute = -1
            val weatherTicker = AutoWeatherTickerState(_uiState.value.particleWeather).apply {
                rememberAutoWeather(activeWeather)
            }

            while (isActive) {
                val now = ZonedDateTime.now()
                val currentState = _uiState.value
                val timeTick = buildTimeTick(now, currentState)
                val resolvedPreset = currentState.selectedThemePreset.resolveActive(timeTick.hour24)
                val activeWeather = weatherTicker.advanceWeatherTick(
                    requestedWeather = currentState.particleWeather,
                    isAuto = currentState.isParticleWeatherAuto,
                    resolvedPreset = resolvedPreset,
                    timeTick = timeTick,
                    realWeather = currentState.realWeather
                )

                val elapsedNow = SystemClock.elapsedRealtime()
                val snoozeRemaining = dailyAlarmSnoozeDeadlineElapsedMs?.let {
                    ((it - elapsedNow + 999L) / 1000L).coerceAtLeast(0L).toInt()
                } ?: 0
                val nextOffset = Offset(random.nextFloat() * 20f - 10f, random.nextFloat() * 20f - 10f)
                val nextFont = AvailableClockFonts.random()
                val sideEffects = _uiState.updateAndGetEffect { state ->
                    var effects = ClockTickerSideEffects()
                    var workingState = advanceThemeTick(state, resolvedPreset, nextFont)

                    val modeTick = advanceModeTick(workingState, elapsedNow)
                    workingState = modeTick.state
                    effects = effects.withModeTick(modeTick)

                    val burnInOffset = if (timeTick.minute != lastMinute) nextOffset else workingState.burnInOffset
                    val alarmTick = advanceAlarmTick(
                        state = workingState,
                        timeTick = timeTick,
                        companionMessage = modeTick.message ?: workingState.companionMessage,
                        snoozeRemainingSeconds = snoozeRemaining
                    )
                    workingState = alarmTick.state
                    effects = effects.withAlarmTick(alarmTick)

                    workingState.copy(
                        hour = timeTick.displayHour,
                        minute = timeTick.minute.toString().padStart(2, '0'),
                        second = timeTick.second.toString().padStart(2, '0'),
                        currentHour24 = timeTick.hour24,
                        amPm = timeTick.amPm,
                        date = timeTick.date,
                        dayOfWeek = timeTick.dayOfWeek,
                        // The wallpaper belongs to the active theme. AUTO resolves to NIGHT
                        // during night hours, while manually chosen themes remain unchanged.
                        backgroundRes = if (workingState.activeThemePreset == ThemePreset.NIGHT) {
                            R.drawable.night_sky
                        } else {
                            R.drawable.jiguang
                        },
                        particleWeather = if (workingState.isParticleWeatherAuto) activeWeather else workingState.particleWeather,
                        burnInOffset = burnInOffset,
                        dailyAlarmSnoozeRemainingSeconds = alarmTick.snoozeRemainingSeconds,
                        companionMessage = alarmTick.companionMessage
                    ) to effects
                }

                runTickerSideEffects(sideEffects)
                if (timeTick.minute != lastMinute) {
                    lastMinute = timeTick.minute
                }
                delay(1000)
            }
        }
    }

    private data class TimeTick(
        val displayHour: String,
        val minute: Int,
        val second: Int,
        val hour24: Int,
        val amPm: String,
        val date: String,
        val dayOfWeek: String,
        val epochSecond: Long,
        val isNightQuietHours: Boolean
    )

    private data class AutoWeatherTickerState(
        var activeWeather: ParticleWeather,
        var nextChangeEpochSec: Long = 0L,
        val recentAutoWeathers: ArrayDeque<ParticleWeather> = ArrayDeque()
    )

    private data class AlarmTickResult(
        val state: ClockState,
        val snoozeRemainingSeconds: Int,
        val companionMessage: String,
        val shouldStartDailyAlarm: Boolean = false
    )

    private data class ClockTickerSideEffects(
        val shouldPlayReminder: Boolean = false,
        val shouldStartDailyAlarm: Boolean = false,
        val shouldStartTimerAlert: Boolean = false,
        val edgeLightMode: EdgeLightMode? = null,
        val edgeLightDurationMs: Long? = null
    ) {
        fun withModeTick(modeTick: ModeTickResult): ClockTickerSideEffects {
            return copy(
                shouldPlayReminder = shouldPlayReminder || (modeTick.triggerSound && !modeTick.shouldStartTimerAlert),
                shouldStartTimerAlert = shouldStartTimerAlert || modeTick.shouldStartTimerAlert,
                edgeLightMode = modeTick.edgeLightMode ?: edgeLightMode,
                edgeLightDurationMs = modeTick.edgeLightDurationMs ?: edgeLightDurationMs
            )
        }

        fun withAlarmTick(alarmTick: AlarmTickResult): ClockTickerSideEffects {
            return copy(
                shouldStartDailyAlarm = shouldStartDailyAlarm || alarmTick.shouldStartDailyAlarm
            )
        }
    }

    private fun buildTimeTick(now: ZonedDateTime, state: ClockState): TimeTick {
        val hour24 = now.hour
        val displayHour = if (state.is24HourFormat) {
            hour24.toString().padStart(2, '0')
        } else {
            val h12 = if (hour24 % 12 == 0) 12 else hour24 % 12
            h12.toString().padStart(2, '0')
        }
        return TimeTick(
            displayHour = displayHour,
            minute = now.minute,
            second = now.second,
            hour24 = hour24,
            amPm = if (state.is24HourFormat) "" else now.format(amPmFormatter),
            date = now.format(dateFormatter),
            dayOfWeek = now.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
            epochSecond = now.toEpochSecond(),
            isNightQuietHours = hour24 >= 23 || hour24 < 7
        )
    }

    private fun AutoWeatherTickerState.rememberAutoWeather(weather: ParticleWeather) {
        recentAutoWeathers.addLast(weather)
        while (recentAutoWeathers.size > 3) {
            recentAutoWeathers.removeFirst()
        }
    }

    private fun AutoWeatherTickerState.advanceWeatherTick(
        requestedWeather: ParticleWeather,
        isAuto: Boolean,
        resolvedPreset: ThemePreset,
        timeTick: TimeTick,
        realWeather: ParticleWeather? = null
    ): ParticleWeather {
        if (!isAuto) {
            activeWeather = requestedWeather
            nextChangeEpochSec = timeTick.epochSecond + WEATHER_ROTATION_INTERVAL_SEC
            return activeWeather
        }

        if (realWeather != null) {
            // A dedicated night backdrop handles the time-of-day mood, so retain the
            // actual weather effect instead of substituting it with wind or fog.
            activeWeather = realWeather
            return activeWeather
        }

        if (activeWeather != requestedWeather) {
            activeWeather = requestedWeather
        }
        val weatherCandidates = ClockStateReducers.weatherCandidatesForPreset(
            preset = resolvedPreset,
            hour24 = timeTick.hour24,
            allowBrightWeather = !timeTick.isNightQuietHours
        )
        if (timeTick.isNightQuietHours && ClockStateReducers.isBrightWeather(activeWeather)) {
            activeWeather = pickRandomWeather(
                excluding = activeWeather,
                candidates = weatherCandidates,
                preferCalm = true,
                recent = recentAutoWeathers
            )
            rememberAutoWeather(activeWeather)
            nextChangeEpochSec = timeTick.epochSecond + WEATHER_ROTATION_INTERVAL_SEC
        }
        if (nextChangeEpochSec == 0L || timeTick.epochSecond >= nextChangeEpochSec) {
            activeWeather = pickRandomWeather(
                excluding = activeWeather,
                candidates = weatherCandidates,
                preferCalm = timeTick.isNightQuietHours,
                recent = recentAutoWeathers
            )
            rememberAutoWeather(activeWeather)
            nextChangeEpochSec = timeTick.epochSecond + WEATHER_ROTATION_INTERVAL_SEC
        }
        return activeWeather
    }

    private fun advanceThemeTick(state: ClockState, resolvedPreset: ThemePreset, nextFont: ClockFont): ClockState {
        if (state.activeThemePreset == resolvedPreset) return state
        // In AUTO mode the preset switches automatically on a schedule;
        // take the opportunity to also randomly pick a fresh font.
        val isAutoMode = state.selectedThemePreset == ThemePreset.AUTO
        return applyThemePresetProfile(
            state = state.copy(activeThemePreset = resolvedPreset),
            preset = resolvedPreset,
            randomizeFont = false
        ).let { if (isAutoMode) it.copy(selectedFont = nextFont) else it }
    }

    private fun advanceModeTick(state: ClockState, nowElapsedMs: Long): ModeTickResult {
        return modeTickResult(ClockStateReducers.advanceElapsedMode(state, nowElapsedMs))
    }

    private fun advanceAlarmTick(
        state: ClockState,
        timeTick: TimeTick,
        companionMessage: String,
        snoozeRemainingSeconds: Int
    ): AlarmTickResult {
        val dailyMarker = "${timeTick.date}-${state.dailyAlarmHour}-${state.dailyAlarmMinute}"
        var nextMessage = companionMessage
        var shouldStartDailyAlarm = false

        if (
            ClockStateReducers.shouldTriggerDailyAlarm(
                state = state,
                hour24 = timeTick.hour24,
                minute = timeTick.minute,
                second = timeTick.second,
                dailyMarker = dailyMarker,
                lastDailyAlarmMarker = state.lastDailyAlarmMarker
            )
        ) {
            shouldStartDailyAlarm = true
            nextMessage = appContext.getString(R.string.reminder_alarm_now)
        }

        return AlarmTickResult(
            state = if (shouldStartDailyAlarm) state.copy(lastDailyAlarmMarker = dailyMarker) else state,
            snoozeRemainingSeconds = snoozeRemainingSeconds,
            companionMessage = nextMessage,
            shouldStartDailyAlarm = shouldStartDailyAlarm
        )
    }

    private fun runTickerSideEffects(sideEffects: ClockTickerSideEffects) {
        if (sideEffects.shouldPlayReminder) {
            playAudio()
        }
        if (sideEffects.shouldStartDailyAlarm) {
            startDailyAlarm()
        }
        if (sideEffects.shouldStartTimerAlert) {
            startTimerAlert()
        }
        if (sideEffects.edgeLightMode != null) {
            showEdgeLight(sideEffects.edgeLightMode, sideEffects.edgeLightDurationMs)
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
                val baseWeight = ClockStateReducers.weatherWeight(weather, preferCalm = preferCalm)
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

    private data class ModeTickResult(
        val state: ClockState,
        val message: String? = null,
        val triggerSound: Boolean = false,
        val shouldStartTimerAlert: Boolean = false,
        val edgeLightMode: EdgeLightMode? = null,
        val edgeLightDurationMs: Long? = null
    )

    private fun modeTickResult(result: ClockStateReducers.ModeTickResult): ModeTickResult {
        return ModeTickResult(
            state = result.state,
            message = result.message.toCompanionMessage(),
            triggerSound = result.triggerSound,
            shouldStartTimerAlert = result.message == ClockStateReducers.ModeMessage.COUNTDOWN_DONE,
            edgeLightMode = result.edgeLightMode,
            edgeLightDurationMs = result.edgeLightDurationMs
        )
    }

    private fun ClockStateReducers.ModeMessage?.toCompanionMessage(): String? {
        return when (this) {
            ClockStateReducers.ModeMessage.FOCUS_DONE -> appContext.getString(R.string.companion_focus_done)
            ClockStateReducers.ModeMessage.BREAK_DONE -> appContext.getString(R.string.companion_break_done)
            ClockStateReducers.ModeMessage.COUNTDOWN_DONE -> appContext.getString(R.string.companion_countdown_done)
            ClockStateReducers.ModeMessage.BREAK_NUDGE -> appContext.getString(R.string.companion_break_nudge)
            null -> null
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

    private fun startAlarmPlayback() {
        if (alarmPlaybackJob?.isActive == true) return
        val previous = alarmPlaybackJob
        alarmPlaybackJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            alarmTonePlayer.play()
        }
    }

    private fun stopAlarmPlayback() {
        alarmPlaybackJob?.cancel()
    }

    private fun startSleepPlayback(mode: SleepSoundMode) {
        if (sleepPlaybackJob?.isActive == true && playingSleepMode == mode) return
        val previous = sleepPlaybackJob
        previous?.cancel()
        playingSleepMode = mode
        sleepPlaybackJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            whiteNoisePlayer.play(mode)
        }
    }

    private fun stopSleepPlayback() {
        sleepPlaybackJob?.cancel()
        playingSleepMode = null
    }

    private fun startDailyAlarm() {
        dailyAlarmJob?.cancel()
        startAlarmPlayback()
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
        stopAlarmPlayback()
    }

    private fun startTimerAlert() {
        timerAlertJob?.cancel()
        startAlarmPlayback()
        showEdgeLight(EdgeLightMode.TIMER_ALERT, DAILY_ALARM_RING_MS)
        _uiState.update {
            it.copy(
                isTimerAlertRinging = true,
                companionMessage = appContext.getString(R.string.timer_alert_now)
            )
        }
        timerAlertJob = viewModelScope.launch {
            delay(DAILY_ALARM_RING_MS)
            stopTimerAlertSound()
            clearEdgeLight()
            _uiState.update { it.copy(isTimerAlertRinging = false) }
        }
    }

    private fun stopTimerAlertSound() {
        timerAlertJob?.cancel()
        timerAlertJob = null
        stopAlarmPlayback()
    }

    fun isNetworkLocationFallbackAllowed(): Boolean =
        appContext.getSharedPreferences("weather_location", Context.MODE_PRIVATE)
            .getBoolean("allowNetworkLocation", false)

    fun allowNetworkLocationFallback() {
        appContext.getSharedPreferences("weather_location", Context.MODE_PRIVATE)
            .edit().putBoolean("allowNetworkLocation", true).apply()
        retryLocation()
    }

    private fun fetchNetworkCity(): Pair<Location, String>? {
        if (!appContext.getSharedPreferences("weather_location", Context.MODE_PRIVATE)
                .getBoolean("allowNetworkLocation", false)) return null
        val connection = URL("https://ipapi.co/json/").openConnection() as HttpURLConnection
        return try {
            connection.setRequestProperty("User-Agent", "MeowClock/1.1")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            val status = connection.responseCode
            if (status !in 200..299) {
                DiagnosticLogger.log("LOCATION", "IP city service HTTP $status")
                return null
            }
            val response = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val latitude = response.optDouble("latitude", Double.NaN)
            val longitude = response.optDouble("longitude", Double.NaN)
            if (response.optBoolean("error") || !LocationRecoveryPolicy.validCoordinates(latitude, longitude)) return null
            val city = response.optString("city").ifBlank { response.optString("region") }
            if (city.isBlank()) return null
            val location = Location("ip").apply {
                this.latitude = latitude
                this.longitude = longitude
                accuracy = 25_000f
                time = System.currentTimeMillis()
            }
            DiagnosticLogger.log("LOCATION", "city-level IP fallback succeeded")
            location to city.uppercase(locale)
        } catch (error: Exception) {
            DiagnosticLogger.log("LOCATION", "city-level IP fallback failed", error)
            null
        } finally {
            connection.disconnect()
        }
    }

    fun retryLocation() {
        if (locationJob?.isActive == true) return
        locationRetryAttempt = 0
        fetchLocation(hasLocationPermission = hasDeviceLocationPermission())
    }

    private fun hasDeviceLocationPermission(): Boolean =
        appContext.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun restoreWeatherLocation() {
        if (deviceLatitude != null) return
        val prefs = appContext.getSharedPreferences("weather_location", Context.MODE_PRIVATE)
        if (!LocationRecoveryPolicy.isCacheFresh(prefs.getLong("savedAt", 0L), System.currentTimeMillis())) return
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong("latitude", java.lang.Double.doubleToLongBits(Double.NaN)))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong("longitude", java.lang.Double.doubleToLongBits(Double.NaN)))
        if (!LocationRecoveryPolicy.validCoordinates(lat, lon)) return
        deviceLatitude = lat
        deviceLongitude = lon
        val city = prefs.getString("city", null)?.removeSuffix("（大致位置）")?.removeSuffix(" (approx.)")
        _uiState.update { it.copy(location = city ?: appContext.getString(R.string.location_cached)) }
        DiagnosticLogger.log("LOCATION", "restored recent successful location")
    }

    fun fetchLocation(hasLocationPermission: Boolean) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch(Dispatchers.IO) {
            restoreWeatherLocation()
            val hadLocation = deviceLatitude != null
            DiagnosticLogger.log("LOCATION", "refresh requested; permission=$hasLocationPermission; cached=$hadLocation")
            _uiState.update { it.copy(isLocationRefreshing = true,
                location = if (hadLocation) it.location else appContext.getString(R.string.location_loading)) }
            try {
                // Cached coordinates keep weather usable while a fresh fix is attempted.
                if (hadLocation && _uiState.value.temperatureCelsius == null) fetchAndApplyWeather()
                var location = try {
                    withTimeoutOrNull(20_000L) {
                        if (hasLocationPermission) fetchDeviceLocation() else null
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    DiagnosticLogger.log("LOCATION", "request failed", error)
                    null
                }
                currentCoroutineContext().ensureActive()
                val networkCity = if (location == null) fetchNetworkCity() else null
                currentCoroutineContext().ensureActive()
                if (networkCity != null) location = networkCity.first
                if (location != null) {
                    deviceLatitude = location.latitude
                    deviceLongitude = location.longitude
                    // Weather needs coordinates, not a working Android geocoder.
                    _uiState.update { it.copy(location = appContext.getString(R.string.location_resolved)) }
                    fetchAndApplyWeather()
                    currentCoroutineContext().ensureActive()
                    val city = networkCity?.second ?: withTimeoutOrNull(6_000L) { reverseGeocodeCity(location) }
                    currentCoroutineContext().ensureActive()
                    val label = city?.uppercase(locale) ?: appContext.getString(R.string.location_resolved)
                    _uiState.update { it.copy(location = label) }
                    appContext.getSharedPreferences("weather_location", Context.MODE_PRIVATE).edit()
                        .putLong("latitude", java.lang.Double.doubleToLongBits(location.latitude))
                        .putLong("longitude", java.lang.Double.doubleToLongBits(location.longitude))
                        .putLong("savedAt", System.currentTimeMillis()).putString("city", label).apply()
                    DiagnosticLogger.log("LOCATION", "resolved; provider=${location.provider}; cityAvailable=${city != null}")
                } else {
                    // A failed refresh must never erase the last working coordinates/weather.
                    if (!hadLocation) _uiState.update { it.copy(location = appContext.getString(R.string.location_unavailable)) }
                    DiagnosticLogger.log("LOCATION", "no fix; retaining previous weather; automatic retry enabled")
                }
                if (deviceLatitude != null && isAppActive) startWeatherRefresh()
            } finally {
                // Only this active attempt owns the loading indicator.
                if (currentCoroutineContext().isActive) _uiState.update { it.copy(isLocationRefreshing = false) }
            }
        }
    }

    private fun startLocationRecovery() {
        if (locationRetryJob?.isActive == true) return
        locationRetryJob = viewModelScope.launch {
            while (isActive) {
                delay(LocationRecoveryPolicy.retryDelayMillis(locationRetryAttempt))
                if (locationJob?.isActive == true) continue
                if (deviceLatitude == null || _uiState.value.weatherRefreshFailed || _uiState.value.temperatureCelsius == null) {
                    locationRetryAttempt++
                    DiagnosticLogger.log("LOCATION", "automatic retry attempt=$locationRetryAttempt")
                    if (deviceLatitude != null) {
                        locationJob = viewModelScope.launch(Dispatchers.IO) { fetchAndApplyWeather() }
                    } else {
                        fetchLocation(hasDeviceLocationPermission())
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchDeviceLocation(): Location? {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = manager.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
        DiagnosticLogger.log("LOCATION", "enabled providers=${providers.joinToString()}; geocoder=${Geocoder.isPresent()}")
        val cached = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { location ->
            val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            age in 0..java.util.concurrent.TimeUnit.HOURS.toNanos(6)
        }.minByOrNull { it.accuracy }
        if (cached != null) return cached
        // Try all available Android providers, not just the single provider picked by Criteria.
        val systemFix = withTimeoutOrNull(10_000L) { requestFreshLocation(manager) }
        if (systemFix != null) return systemFix
        DiagnosticLogger.log("LOCATION", "system providers produced no fix; trying Google balanced location")
        return try {
            requestGoogleFusedLocation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DiagnosticLogger.log("LOCATION", "Google location unavailable", error)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestGoogleFusedLocation(): Location? {
        return withTimeoutOrNull(GOOGLE_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val cancellationToken = CancellationTokenSource()
                continuation.invokeOnCancellation { cancellationToken.cancel() }
                fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationToken.token)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        }
    }

    private fun startWeatherRefresh() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(WEATHER_REFRESH_INTERVAL_MS)
                fetchAndApplyWeather()
            }
        }
    }

    private fun stopWeatherRefresh() {
        weatherJob?.cancel()
        weatherJob = null
    }

    private suspend fun fetchAndApplyWeather() {
        val latitude = deviceLatitude ?: return
        val longitude = deviceLongitude ?: return
        val weather = fetchWeatherFromApi(latitude, longitude)
        currentCoroutineContext().ensureActive()
        if (weather == null) {
            _uiState.update { it.copy(weatherRefreshFailed = true) }
            DiagnosticLogger.log("WEATHER", "request failed; will retry automatically")
            return
        }
        _uiState.update {
            it.copy(
                weatherRefreshFailed = false,
                realWeather = weather.particleWeather,
                temperatureCelsius = weather.temperatureCelsius,
                apparentTemperatureCelsius = weather.apparentTemperatureCelsius,
                hourlyForecast = weather.hourlyForecast
            )
        }
        DiagnosticLogger.log(
            "WEATHER",
            "updated; effect=${weather.particleWeather}, temp=${weather.temperatureCelsius}, apparent=${weather.apparentTemperatureCelsius}"
        )
    }

    private data class LiveWeather(
        val particleWeather: ParticleWeather,
        val temperatureCelsius: Double?,
        val apparentTemperatureCelsius: Double?,
        val windSpeedKmh: Double?,
        val windGustKmh: Double?,
        val hourlyForecast: List<HourlyForecast>
    )

    private fun fetchWeatherFromApi(latitude: Double, longitude: Double): LiveWeather? {
        val connection = runCatching {
            URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude" +
                    "&longitude=$longitude&current=weather_code,temperature_2m,apparent_temperature" +
                    ",wind_speed_10m,wind_gusts_10m" +
                    "&hourly=temperature_2m,weather_code&forecast_days=1&timezone=auto" +
                    "&temperature_unit=celsius"
            ).openConnection() as HttpURLConnection
        }.getOrNull() ?: return null

        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.bufferedReader().use { reader ->
                val response = JSONObject(reader.readText())
                val current = response.getJSONObject("current")
                val windSpeedKmh = current.optDouble("wind_speed_10m", Double.NaN).takeIf { it.isFinite() }
                val windGustKmh = current.optDouble("wind_gusts_10m", Double.NaN).takeIf { it.isFinite() }
                val mappedWeather = wmoCodeToParticleWeather(current.getInt("weather_code"))
                LiveWeather(
                    particleWeather = mappedWeather.withWindOverride(windSpeedKmh, windGustKmh),
                    temperatureCelsius = current.optDouble("temperature_2m", Double.NaN).takeIf { it.isFinite() },
                    apparentTemperatureCelsius = current.optDouble("apparent_temperature", Double.NaN)
                        .takeIf { it.isFinite() },
                    windSpeedKmh = windSpeedKmh,
                    windGustKmh = windGustKmh,
                    hourlyForecast = parseHourlyForecast(response)
                )
            }
        } catch (error: Exception) {
            DiagnosticLogger.log("WEATHER", "API failed", error)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseHourlyForecast(response: JSONObject): List<HourlyForecast> {
        val hourly = response.optJSONObject("hourly") ?: return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temperatures = hourly.optJSONArray("temperature_2m") ?: return emptyList()
        val codes = hourly.optJSONArray("weather_code") ?: return emptyList()
        val size = minOf(times.length(), temperatures.length(), codes.length())
        val nowKey = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0).toString()
        val firstUpcoming = (0 until size).firstOrNull { times.getString(it) >= nowKey } ?: return emptyList()
        return listOf(firstUpcoming, firstUpcoming + 4)
            .filter { it < size }
            .map { index ->
                HourlyForecast(
                    time = times.getString(index).substringAfter('T'),
                    weather = wmoCodeToParticleWeather(codes.getInt(index)),
                    temperatureCelsius = temperatures.getDouble(index)
                )
            }
    }

    private fun wmoCodeToParticleWeather(code: Int): ParticleWeather = when (code) {
        0, 1 -> ParticleWeather.SUNNY
        2, 3 -> ParticleWeather.CLOUDY
        45, 48 -> ParticleWeather.FOG
        51, 53, 61, 80 -> ParticleWeather.DRIZZLE
        55, 56, 57, 63, 65, 66, 67, 81, 82, 95 -> ParticleWeather.RAIN
        71, 73, 77, 85 -> ParticleWeather.SNOW
        75, 86 -> ParticleWeather.BLIZZARD
        96, 99 -> ParticleWeather.HAIL
        else -> ParticleWeather.CLOUDY
    }

    private fun ParticleWeather.withWindOverride(windSpeedKmh: Double?, windGustKmh: Double?): ParticleWeather {
        val isWindy = (windSpeedKmh ?: 0.0) >= WIND_EFFECT_SPEED_KMH ||
            (windGustKmh ?: 0.0) >= WIND_EFFECT_GUST_KMH
        return if (isWindy && this in setOf(ParticleWeather.SUNNY, ParticleWeather.CLOUDY, ParticleWeather.FOG)) {
            ParticleWeather.WIND
        } else {
            this
        }
    }

    @SuppressLint("MissingPermission", "Deprecated")
    private suspend fun requestFreshLocation(locationManager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val providers = locationManager.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
            if (providers.isEmpty()) {
                DiagnosticLogger.log("LOCATION", "no active system location providers")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) {
                        locationManager.removeUpdates(this)
                        continuation.resume(location)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            continuation.invokeOnCancellation { runCatching { locationManager.removeUpdates(listener) } }
            // Register on the same looper as callbacks to avoid leaving a late listener behind.
            android.os.Handler(Looper.getMainLooper()).post {
                if (!continuation.isActive) return@post
                var registered = 0
                for (provider in providers) {
                    try {
                        locationManager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
                        registered++
                    } catch (error: Exception) {
                        DiagnosticLogger.log("LOCATION", "provider registration failed: $provider", error)
                    }
                }
                if (!continuation.isActive) locationManager.removeUpdates(listener)
                else if (registered == 0) continuation.resume(null)
            }
        }

    private suspend fun reverseGeocodeCity(location: Location): String? =
        suspendCancellableCoroutine { continuation ->
            // A legacy Geocoder Binder call may never return. Use one bounded worker,
            // with no queue, so repeated refreshes cannot leak threads or queued requests.
            val geocoder = Geocoder(appContext, locale)
            val latitude = location.latitude
            val longitude = location.longitude
            try {
                geocodeExecutor.execute {
                    val city = try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(
                            latitude, longitude, 1
                        )
                        addresses?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                    } catch (error: Exception) {
                        DiagnosticLogger.log("LOCATION", "city lookup failed", error)
                        null
                    }
                    if (continuation.isActive) continuation.resume(city)
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                DiagnosticLogger.log("LOCATION", "city lookup busy; skipped")
                continuation.resume(null)
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
        val now = SystemClock.elapsedRealtime()
        val tick = _uiState.updateAndGetEffect { previous ->
            val result = advanceModeTick(previous, now)
            val state = result.state
            if (state.clockMode == ClockMode.CLOCK || (previous.timerRunning && !state.timerRunning)) {
                state
            } else {
                state.copy(
                    timerRunning = !state.timerRunning,
                    companionMessage = if (!state.timerRunning) {
                        appContext.getString(R.string.companion_timer_started)
                    } else {
                        appContext.getString(R.string.companion_timer_paused)
                    },
                    timerUpdatedAtElapsedMs = if (!state.timerRunning) now else null
                )
            } to result
        }
        runTickerSideEffects(ClockTickerSideEffects().withModeTick(tick))
        _uiState.value.let { updated ->
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
                val weatherCandidates = ClockStateReducers.weatherCandidatesForPreset(
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
            ClockStateReducers.snoozeDailyAlarmState(
                state = it,
                snoozeSeconds = snoozeSeconds,
                message = appContext.getString(R.string.alarm_snoozed_message)
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
            ClockStateReducers.dismissDailyAlarmState(
                state = it,
                message = appContext.getString(R.string.alarm_dismissed_message)
            )
        }
    }

    fun repeatCountdownTimer() {
        val now = SystemClock.elapsedRealtime()
        stopTimerAlertSound()
        clearEdgeLight()
        _uiState.update {
            it.copy(
                clockMode = ClockMode.COUNTDOWN,
                timerRunning = true,
                timerUpdatedAtElapsedMs = now,
                isTimerAlertRinging = false,
                countdownRemainingSeconds = it.countdownDurationMinutes * 60,
                companionMessage = appContext.getString(R.string.timer_repeat_message)
            )
        }
    }

    fun dismissTimerAlert() {
        stopTimerAlertSound()
        clearEdgeLight()
        _uiState.update {
            it.copy(
                isTimerAlertRinging = false,
                companionMessage = appContext.getString(R.string.timer_stopped_message)
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
            startSleepPlayback(_uiState.value.sleepSoundMode)
            startSleepSoundCountdown()
        } else {
            stopSleepSound(clearPersistedState = true)
        }
        persistSetting { this[PreferenceKeys.whiteNoise] = enabled }
    }

    fun setSleepSoundMode(mode: SleepSoundMode) {
        _uiState.update { it.copy(sleepSoundMode = mode) }
        if (_uiState.value.whiteNoiseEnabled) {
            startSleepPlayback(mode)
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
        stopSleepPlayback()
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
            startDiagnostics()
            startLocationRecovery()
            registerRotationSensorIfNeeded()
            if (deviceLatitude != null && deviceLongitude != null) startWeatherRefresh()
        } else {
            stopClockTicker()
            locationRetryJob?.cancel()
            locationRetryJob = null
            diagnosticsJob?.cancel()
            diagnosticsJob = null
            unregisterRotationSensor()
            stopWeatherRefresh()
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
        // Parallax does not need display-refresh-rate sensor updates.  Throttling this
        // prevents a continuous full-screen Compose redraw while the phone is resting.
        if (nowMs - lastSensorUpdateTimeMs < PARALLAX_UPDATE_INTERVAL_MS) {
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

    private companion object {
        val geocodeExecutor = java.util.concurrent.ThreadPoolExecutor(
            0, 1, 30L, java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.SynchronousQueue<Runnable>(),
            java.util.concurrent.ThreadFactory { task -> Thread(task, "MeowCityLookup").apply { isDaemon = true } }
        )
        const val PARALLAX_UPDATE_INTERVAL_MS = 100L
        const val GOOGLE_LOCATION_TIMEOUT_MS = 8_000L
        const val WIND_EFFECT_SPEED_KMH = 25.0
        const val WIND_EFFECT_GUST_KMH = 40.0
    }

    override fun onCleared() {
        edgeLightJob?.cancel()
        sleepSoundJob?.cancel()
        dailyAlarmJob?.cancel()
        timerAlertJob?.cancel()
        dailyAlarmSnoozeJob?.cancel()
        stopClockTicker()
        unregisterRotationSensor()
        runCatching { appContext.unregisterReceiver(batteryReceiver) }
        locationJob?.cancel()
        locationRetryJob?.cancel()
        diagnosticsJob?.cancel()
        stopWeatherRefresh()
        mediaPlayer?.release()
        loudnessEnhancer?.release()
        stopSleepPlayback()
        stopAlarmPlayback()
        mediaPlayer = null
        loudnessEnhancer = null
        super.onCleared()
    }
}

private const val SLEEP_SOUND_DURATION_MS = 60L * 60L * 1000L
private const val DAILY_ALARM_RING_MS = 9L * 60L * 1000L
private const val DAILY_ALARM_SNOOZE_MS = 10L * 60L * 1000L
private const val WEATHER_ROTATION_INTERVAL_SEC = 8L * 60L * 60L
private const val WEATHER_REFRESH_INTERVAL_MS = 30L * 60L * 1000L
private const val TIMER_ALERT_EDGE_MS = 5_000L
private const val BREAK_NUDGE_INTERVAL_SEC = 30 * 60
private const val BREAK_NUDGE_EDGE_MS = 10_000L

private fun Offset.sanitize(): Offset = Offset(
    x = x.takeIf { it.isFinite() } ?: 0f,
    y = y.takeIf { it.isFinite() } ?: 0f
)

private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod
