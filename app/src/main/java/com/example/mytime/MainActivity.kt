package com.example.mytime

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mytime.ui.ClockViewModel
import com.example.mytime.ui.ClockMode
import com.example.mytime.ui.ParticleWeather
import com.example.mytime.ui.SleepSoundMode
import com.example.mytime.ui.ThemePreset
import com.example.mytime.ui.theme.FlipClockScreen
import com.example.mytime.ui.theme.MytimeTheme

class MainActivity : ComponentActivity() {

    private enum class LocationDialogMode {
        PRE_PERMISSION,
        OPEN_SETTINGS
    }

    private val viewModel: ClockViewModel by viewModels()
    private var locationDialogMode by mutableStateOf<LocationDialogMode?>(null)
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        locationDialogMode = null
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted && isLocationPermissionPermanentlyDenied()) {
            locationDialogMode = LocationDialogMode.OPEN_SETTINGS
        }
        viewModel.fetchLocation(hasLocationPermission = granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 设置全屏沉浸式
        applyImmersiveMode()

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (hasLocationPermission()) {
            viewModel.fetchLocation(hasLocationPermission = true)
        } else {
            viewModel.fetchLocation(hasLocationPermission = false)
            if (!hasRequestedLocationPermissionBefore()) {
                locationDialogMode = LocationDialogMode.PRE_PERMISSION
            }
        }

        setContent {
            MytimeTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(uiState.isSettingsVisible) {
                    window.decorView.post { applyImmersiveMode() }
                }
                if (locationDialogMode != null) {
                    AlertDialog(
                        onDismissRequest = {
                            locationDialogMode = null
                            viewModel.fetchLocation(hasLocationPermission = false)
                        },
                        title = {
                            Text(
                                getString(
                                    when (locationDialogMode) {
                                        LocationDialogMode.OPEN_SETTINGS -> R.string.location_permission_title
                                        LocationDialogMode.PRE_PERMISSION -> R.string.location_permission_title
                                        else -> R.string.location_permission_title
                                    }
                                )
                            )
                        },
                        text = {
                            Text(
                                getString(
                                    when (locationDialogMode) {
                                        LocationDialogMode.OPEN_SETTINGS -> R.string.location_permission_settings_message
                                        LocationDialogMode.PRE_PERMISSION -> R.string.location_permission_message
                                        else -> R.string.location_permission_message
                                    }
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    when (locationDialogMode) {
                                        LocationDialogMode.OPEN_SETTINGS -> openAppSettings()
                                        LocationDialogMode.PRE_PERMISSION -> requestLocationPermissions()
                                        else -> Unit
                                    }
                                }
                            ) {
                                Text(
                                    getString(
                                        if (locationDialogMode == LocationDialogMode.OPEN_SETTINGS) {
                                            R.string.location_permission_open_settings
                                        } else {
                                            R.string.location_permission_allow
                                        }
                                    )
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    locationDialogMode = null
                                    viewModel.fetchLocation(hasLocationPermission = false)
                                }
                            ) {
                                Text(getString(R.string.location_permission_not_now))
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    FlipClockScreen(
                        state = uiState,
                        onPlayAudio = { viewModel.playAudio() },
                        onOpenSettings = { viewModel.openSettings() },
                        onCloseSettings = { viewModel.closeSettings() },
                        onSelectFont = { viewModel.setFont(it) },
                        onToggleParallax = { viewModel.toggleParallax(it) },
                        onToggleParticleWeatherAuto = { viewModel.setParticleWeatherAuto(it) },
                        onSelectParticleWeather = { weather: ParticleWeather -> viewModel.setParticleWeather(weather) },
                        onToggleCats = { viewModel.toggleCats(it) },
                        onToggle24HourFormat = { viewModel.toggle24HourFormat(it) },
                        onSetClockMode = { mode: ClockMode -> viewModel.setClockMode(mode) },
                        onToggleModeRunning = { viewModel.toggleModeRunning() },
                        onResetMode = { viewModel.resetActiveMode() },
                        onSetPomodoroFocus = { viewModel.setPomodoroFocusMinutes(it) },
                        onSetPomodoroBreak = { viewModel.setPomodoroBreakMinutes(it) },
                        onSetCountdown = { viewModel.setCountdownDurationMinutes(it) },
                        onToggleHourlyChime = { viewModel.toggleHourlyChime(it) },
                        onToggleDailyAlarm = { viewModel.toggleDailyAlarm(it) },
                        onSetDailyAlarmHour = { viewModel.setDailyAlarmHour(it) },
                        onSetDailyAlarmMinute = { viewModel.setDailyAlarmMinute(it) },
                        onSnoozeDailyAlarm = { viewModel.snoozeDailyAlarm() },
                        onDismissDailyAlarm = { viewModel.dismissDailyAlarm() },
                        onSetThemePreset = { preset: ThemePreset -> viewModel.setThemePreset(preset) },
                        onSelectSleepSound = { mode: SleepSoundMode -> viewModel.setSleepSoundMode(mode) },
                        onToggleWhiteNoise = { viewModel.toggleWhiteNoise(it) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { applyImmersiveMode() }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setAppActive(true)
    }

    override fun onStop() {
        viewModel.setAppActive(false)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.BLACK
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requestLocationPermissions() {
        markLocationPermissionRequested()
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequestedLocationPermissionBefore(): Boolean {
        return getSharedPreferences("permissions", MODE_PRIVATE)
            .getBoolean("location_requested_once", false)
    }

    private fun markLocationPermissionRequested() {
        getSharedPreferences("permissions", MODE_PRIVATE)
            .edit()
            .putBoolean("location_requested_once", true)
            .apply()
    }

    private fun isLocationPermissionPermanentlyDenied(): Boolean {
        val askedBefore = hasRequestedLocationPermissionBefore()
        val fineRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        return askedBefore && !fineRationale && !coarseRationale
    }

    private fun openAppSettings() {
        locationDialogMode = null
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}
