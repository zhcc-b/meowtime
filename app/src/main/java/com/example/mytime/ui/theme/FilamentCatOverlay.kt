package com.example.mytime.ui.theme

import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mytime.ui.ParticleWeather
import com.example.mytime.ui.ThemePreset

@Composable
fun FilamentCatOverlay(
    enabled: Boolean,
    weather: ParticleWeather,
    theme: ThemePreset,
    forbiddenRect: RectF,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CatOverlayView(context).apply {
                setCatsEnabled(enabled)
                setWeather(weather)
                setTheme(theme)
                setForbiddenRect(forbiddenRect)
            }
        },
        update = { view ->
            view.setCatsEnabled(enabled)
            view.setWeather(weather)
            view.setTheme(theme)
            view.setForbiddenRect(forbiddenRect)
        }
    )
}
