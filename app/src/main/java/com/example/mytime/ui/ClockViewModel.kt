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
    val burnIn = booleanPreferencesKey("burn_in")
    val soundButton = booleanPreferencesKey("sound_button")
    val parallax = booleanPreferencesKey("parallax")
    val particles = booleanPreferencesKey("particles")
    val cats = booleanPreferencesKey("cats")
    val dynamicWallpaper = booleanPreferencesKey("dynamic_wallpaper")
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
    val breakReminder = booleanPreferencesKey("break_reminder")
    val themePreset = stringPreferencesKey("theme_preset")
    val themeEdgeLight = booleanPreferencesKey("theme_edge_light")
    val whiteNoise = booleanPreferencesKey("white_noise")
}

class ClockViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val _uiState = MutableStateFlow(ClockState())
    val uiState: StateFlow<ClockState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val whiteNoisePlayer = WhiteNoisePlayer()
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
                    state.copy(
                        isBurnInProtectionEnabled = preferences[PreferenceKeys.burnIn] ?: state.isBurnInProtectionEnabled,
                        isSoundButtonVisible = preferences[PreferenceKeys.soundButton] ?: state.isSoundButtonVisible,
                        isParallaxEnabled = preferences[PreferenceKeys.parallax] ?: state.isParallaxEnabled,
                        isParticleSystemEnabled = preferences[PreferenceKeys.particles] ?: state.isParticleSystemEnabled,
                        isParticleWeatherAuto = manualWeather == null,
                        particleWeather = manualWeather ?: state.particleWeather,
                        isCatSystemEnabled = preferences[PreferenceKeys.cats] ?: state.isCatSystemEnabled,
                        isDynamicWallpaperEnabled = preferences[PreferenceKeys.dynamicWallpaper] ?: state.isDynamicWallpaperEnabled,
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
                        breakReminderEnabled = preferences[PreferenceKeys.breakReminder] ?: state.breakReminderEnabled,
                        selectedThemePreset = preset,
                        isThemeEdgeLightEnabled = preferences[PreferenceKeys.themeEdgeLight] ?: state.isThemeEdgeLightEnabled,
                        whiteNoiseEnabled = preferences[PreferenceKeys.whiteNoise] ?: state.whiteNoiseEnabled
                    )
                }
                if (_uiState.value.whiteNoiseEnabled) {
                    whiteNoisePlayer.start()
                } else {
                    whiteNoisePlayer.stop()
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
                    if (isNightQuietHours && activeWeather.isBrightWeather()) {
                        activeWeather = pickRandomWeather(
                            excluding = activeWeather,
                            allowBrightWeather = false
                        )
                        nextWeatherChangeEpochSec = nowEpochSec + (8 * 60 * 60)
                    }
                    if (nextWeatherChangeEpochSec == 0L || nowEpochSec >= nextWeatherChangeEpochSec) {
                        activeWeather = pickRandomWeather(
                            excluding = activeWeather,
                            allowBrightWeather = !isNightQuietHours
                        )
                        nextWeatherChangeEpochSec = nowEpochSec + (8 * 60 * 60)
                    }
                } else {
                    activeWeather = _uiState.value.particleWeather
                    nextWeatherChangeEpochSec = nowEpochSec + (8 * 60 * 60)
                }

                var shouldPlayReminder = false
                var edgeLightMode: EdgeLightMode? = null
                var edgeLightDurationMs: Long? = null
                _uiState.update { state ->
                    var workingState = state
                    if (workingState.activeThemePreset != resolvedPreset) {
                        workingState = applyThemePresetProfile(
                            state = workingState.copy(activeThemePreset = resolvedPreset),
                            preset = resolvedPreset
                        )
                    }

                    val modeTick = advanceModeState(workingState)
                    workingState = modeTick.state
                    if (modeTick.triggerSound) {
                        shouldPlayReminder = true
                    }
                    edgeLightMode = modeTick.edgeLightMode
                    edgeLightDurationMs = modeTick.edgeLightDurationMs

                    val burnInOffset = when {
                        !workingState.isBurnInProtectionEnabled -> Offset.Zero
                        currentMinute != lastMinute -> Offset(
                            x = random.nextFloat() * 20f - 10f,
                            y = random.nextFloat() * 20f - 10f
                        )
                        else -> workingState.burnInOffset
                    }
                    val displayHour = if (workingState.is24HourFormat) {
                        hour24.toString().padStart(2, '0')
                    } else {
                        val h12 = if (hour24 % 12 == 0) 12 else hour24 % 12
                        h12.toString().padStart(2, '0')
                    }
                    val amPm = if (workingState.is24HourFormat) "" else now.format(amPmFormatter)
                    val onlineWallpaper = if (workingState.isDynamicWallpaperEnabled) {
                        getOnlineBackgroundForTime(now, hour24)
                    } else {
                        null
                    }

                    val hourlyMarker = "${now.toLocalDate()}-$hour24"
                    val dailyMarker = "${now.toLocalDate()}-${workingState.dailyAlarmHour}-${workingState.dailyAlarmMinute}"
                    var companionMessage = modeTick.message ?: workingState.companionMessage

                    if (workingState.hourlyChimeEnabled && currentMinute == 0 && second == 0 && hourlyMarker != lastHourlyChimeMarker) {
                        lastHourlyChimeMarker = hourlyMarker
                        shouldPlayReminder = true
                        companionMessage = appContext.getString(R.string.reminder_hourly_chime, displayHour)
                    }
                    if (
                        workingState.dailyAlarmEnabled &&
                        hour24 == workingState.dailyAlarmHour &&
                        currentMinute == workingState.dailyAlarmMinute &&
                        second == 0 &&
                        dailyMarker != lastDailyAlarmMarker
                    ) {
                        lastDailyAlarmMarker = dailyMarker
                        shouldPlayReminder = true
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
                        backgroundRes = if (workingState.isDynamicWallpaperEnabled) null else R.drawable.jiguang,
                        backgroundUrl = onlineWallpaper,
                        particleWeather = if (workingState.isParticleWeatherAuto) activeWeather else workingState.particleWeather,
                        burnInOffset = burnInOffset,
                        companionMessage = companionMessage
                    )
                }
                if (shouldPlayReminder) {
                    playReminderCue()
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

    private fun pickRandomWeather(excluding: ParticleWeather, allowBrightWeather: Boolean = true): ParticleWeather {
        val candidates = if (allowBrightWeather) {
            ParticleWeather.entries
        } else {
            ParticleWeather.entries.filterNot { it.isBrightWeather() }
        }
        val options = candidates.filter { it != excluding }.ifEmpty { candidates }
        return options[random.nextInt(options.size)]
    }

    private fun ParticleWeather.isBrightWeather(): Boolean {
        return this == ParticleWeather.SUNNY || this == ParticleWeather.CLOUDY
    }

    private fun getOnlineBackgroundForTime(now: ZonedDateTime, hour: Int): String {
        val urls = when (hour) {
            in 6..11 -> listOf(
                "https://picsum.photos/seed/meowtime-morning-1/1920/1080",
                "https://picsum.photos/seed/meowtime-morning-2/1920/1080",
                "https://picsum.photos/seed/meowtime-morning-3/1920/1080"
            )
            in 12..17 -> listOf(
                "https://picsum.photos/seed/meowtime-afternoon-1/1920/1080",
                "https://picsum.photos/seed/meowtime-afternoon-2/1920/1080",
                "https://picsum.photos/seed/meowtime-afternoon-3/1920/1080"
            )
            else -> listOf(
                "https://picsum.photos/seed/meowtime-night-1/1920/1080",
                "https://picsum.photos/seed/meowtime-night-2/1920/1080",
                "https://picsum.photos/seed/meowtime-night-3/1920/1080"
            )
        }
        val index = ((now.year + now.dayOfYear + (hour / 6)) % urls.size).coerceAtLeast(0)
        return urls[index]
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
                            edgeLightDurationMs = 5_000L
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
                            edgeLightDurationMs = 5_000L
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
                        edgeLightDurationMs = 5_000L
                    )
                } else {
                    ModeTickResult(state.copy(countdownRemainingSeconds = nextRemaining))
                }
            }
            ClockMode.STOPWATCH -> {
                val nextElapsed = state.stopwatchElapsedSeconds + 1
                val needsBreakNudge = state.breakReminderEnabled && nextElapsed > 0 && nextElapsed % (45 * 60) == 0
                ModeTickResult(
                    state.copy(stopwatchElapsedSeconds = nextElapsed),
                    message = if (needsBreakNudge) appContext.getString(R.string.companion_break_nudge) else null,
                    triggerSound = false,
                    edgeLightMode = if (needsBreakNudge) EdgeLightMode.BREAK_REMINDER else null,
                    edgeLightDurationMs = if (needsBreakNudge) 10_000L else null
                )
            }
        }
    }

    private fun applyThemePresetProfile(state: ClockState, preset: ThemePreset): ClockState {
        val profile = preset.profile()
        val presetFont = AvailableClockFonts.find { it.name == profile.fontName } ?: state.selectedFont
        return state.copy(
            selectedFont = presetFont,
            particleWeather = profile.weather,
            isParticleWeatherAuto = false,
            isParticleSystemEnabled = profile.particlesEnabled,
            isCatSystemEnabled = profile.catsEnabled,
            isDynamicWallpaperEnabled = profile.dynamicWallpaperEnabled,
            isSoundButtonVisible = profile.soundButtonVisible,
            activeThemePreset = preset
        )
    }

    private fun playReminderCue() {
        playAudio()
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

    fun toggleBurnIn(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isBurnInProtectionEnabled = enabled,
                burnInOffset = if (enabled) it.burnInOffset else Offset.Zero
            )
        }
        persistSetting { this[PreferenceKeys.burnIn] = enabled }
    }

    fun toggleSound(visible: Boolean) {
        _uiState.update { it.copy(isSoundButtonVisible = visible) }
        persistSetting { this[PreferenceKeys.soundButton] = visible }
    }

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
            showEdgeLight(EdgeLightMode.NONE)
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
                ClockMode.STOPWATCH -> showEdgeLight(if (updated.timerRunning) EdgeLightMode.STOPWATCH_ACTIVE else EdgeLightMode.NONE)
                ClockMode.CLOCK -> Unit
                else -> if (!updated.timerRunning) showEdgeLight(EdgeLightMode.NONE)
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
        showEdgeLight(EdgeLightMode.NONE)
    }

    fun adjustPomodoroFocus(delta: Int) {
        _uiState.update { state ->
            val next = (state.pomodoroFocusMinutes + delta).coerceIn(5, 90)
            state.copy(
                pomodoroFocusMinutes = next,
                pomodoroRemainingSeconds = if (!state.timerRunning && state.pomodoroPhase == PomodoroPhase.FOCUS) next * 60 else state.pomodoroRemainingSeconds
            )
        }
        persistSetting { this[PreferenceKeys.pomodoroFocusMinutes] = _uiState.value.pomodoroFocusMinutes }
    }

    fun adjustPomodoroBreak(delta: Int) {
        _uiState.update { state ->
            val next = (state.pomodoroBreakMinutes + delta).coerceIn(1, 30)
            state.copy(
                pomodoroBreakMinutes = next,
                pomodoroRemainingSeconds = if (!state.timerRunning && state.pomodoroPhase == PomodoroPhase.BREAK) next * 60 else state.pomodoroRemainingSeconds
            )
        }
        persistSetting { this[PreferenceKeys.pomodoroBreakMinutes] = _uiState.value.pomodoroBreakMinutes }
    }

    fun adjustCountdownDuration(delta: Int) {
        _uiState.update { state ->
            val next = (state.countdownDurationMinutes + delta).coerceIn(1, 180)
            state.copy(
                countdownDurationMinutes = next,
                countdownRemainingSeconds = if (!state.timerRunning) next * 60 else state.countdownRemainingSeconds
            )
        }
        persistSetting { this[PreferenceKeys.countdownDurationMinutes] = _uiState.value.countdownDurationMinutes }
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

    fun toggleParticles(enabled: Boolean) {
        _uiState.update { it.copy(isParticleSystemEnabled = enabled) }
        persistSetting { this[PreferenceKeys.particles] = enabled }
    }

    fun setParticleWeatherAuto(auto: Boolean) {
        _uiState.update { state ->
            if (auto) {
                state.copy(
                    isParticleWeatherAuto = true,
                    particleWeather = pickRandomWeather(excluding = state.particleWeather)
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

    fun toggleDynamicWallpaper(enabled: Boolean) {
        _uiState.update { it.copy(isDynamicWallpaperEnabled = enabled) }
        persistSetting { this[PreferenceKeys.dynamicWallpaper] = enabled }
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
        persistSetting { this[PreferenceKeys.dailyAlarmEnabled] = enabled }
    }

    fun adjustDailyAlarmHour(delta: Int) {
        _uiState.update { state ->
            state.copy(dailyAlarmHour = (state.dailyAlarmHour + delta).floorMod(24))
        }
        persistSetting { this[PreferenceKeys.dailyAlarmHour] = _uiState.value.dailyAlarmHour }
    }

    fun adjustDailyAlarmMinute(delta: Int) {
        _uiState.update { state ->
            state.copy(dailyAlarmMinute = (state.dailyAlarmMinute + delta).floorMod(60))
        }
        persistSetting { this[PreferenceKeys.dailyAlarmMinute] = _uiState.value.dailyAlarmMinute }
    }

    fun toggleBreakReminder(enabled: Boolean) {
        _uiState.update { it.copy(breakReminderEnabled = enabled) }
        persistSetting { this[PreferenceKeys.breakReminder] = enabled }
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

    fun toggleThemeEdgeLight(enabled: Boolean) {
        _uiState.update { it.copy(isThemeEdgeLightEnabled = enabled) }
        persistSetting { this[PreferenceKeys.themeEdgeLight] = enabled }
    }

    private fun showEdgeLight(mode: EdgeLightMode, durationMs: Long? = null) {
        edgeLightJob?.cancel()
        _uiState.update { it.copy(edgeLightMode = mode) }
        if (mode != EdgeLightMode.NONE && durationMs != null) {
            edgeLightJob = viewModelScope.launch {
                delay(durationMs)
                _uiState.update { state ->
                    if (state.clockMode == ClockMode.STOPWATCH && state.timerRunning) {
                        state.copy(edgeLightMode = EdgeLightMode.STOPWATCH_ACTIVE)
                    } else {
                        state.copy(edgeLightMode = EdgeLightMode.NONE)
                    }
                }
            }
        } else {
            edgeLightJob = null
        }
    }

    fun toggleWhiteNoise(enabled: Boolean) {
        _uiState.update { it.copy(whiteNoiseEnabled = enabled) }
        if (enabled) whiteNoisePlayer.start() else whiteNoisePlayer.stop()
        persistSetting { this[PreferenceKeys.whiteNoise] = enabled }
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
        stopClockTicker()
        unregisterRotationSensor()
        runCatching { appContext.unregisterReceiver(batteryReceiver) }
        locationJob?.cancel()
        mediaPlayer?.release()
        loudnessEnhancer?.release()
        whiteNoisePlayer.stop()
        mediaPlayer = null
        loudnessEnhancer = null
        super.onCleared()
    }
}

private fun Offset.sanitize(): Offset = Offset(
    x = x.takeIf { it.isFinite() } ?: 0f,
    y = y.takeIf { it.isFinite() } ?: 0f
)

private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod
