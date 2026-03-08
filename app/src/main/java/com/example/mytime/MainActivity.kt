package com.example.mytime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mytime.ui.ClockViewModel
import com.example.mytime.ui.theme.FlipClockScreen
import com.example.mytime.ui.theme.MytimeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ClockViewModel by viewModels()
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.fetchLocation(hasLocationPermission = granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 设置全屏沉浸式
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestLocationPermission()

        setContent {
            MytimeTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                        onToggleBurnIn = { viewModel.toggleBurnIn(it) },
                        onToggleSound = { viewModel.toggleSound(it) },
                        onSelectFont = { viewModel.setFont(it) },
                        onToggleParallax = { viewModel.toggleParallax(it) },
                        onToggleParticles = { viewModel.toggleParticles(it) },
                        onToggleDynamicWallpaper = { viewModel.toggleDynamicWallpaper(it) },
                        onToggle24HourFormat = { viewModel.toggle24HourFormat(it) }
                    )
                }
            }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            viewModel.fetchLocation(hasLocationPermission = true)
        }
    }
}
