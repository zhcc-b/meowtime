package com.example.mytime.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// A visual cold-to-warm scale with light tones that stay readable on the dark header.
// Interpolation avoids an abrupt color switch at a temperature boundary.
private val temperatureStops = listOf(
    -10.0 to Color(0xFF9ABFFF),
    0.0 to Color(0xFF91D9FF),
    12.0 to Color(0xFF91E2DE),
    22.0 to Color(0xFFB6E6B6),
    28.0 to Color(0xFFFFE0A0),
    34.0 to Color(0xFFFFB48D),
    40.0 to Color(0xFFFF9B9B)
)

internal fun temperatureColor(celsius: Double?): Color {
    if (celsius == null || !celsius.isFinite()) return LiquidGlassText
    if (celsius <= temperatureStops.first().first) return temperatureStops.first().second
    for (index in 1 until temperatureStops.size) {
        val (upperTemperature, upperColor) = temperatureStops[index]
        if (celsius <= upperTemperature) {
            val (lowerTemperature, lowerColor) = temperatureStops[index - 1]
            val fraction = ((celsius - lowerTemperature) / (upperTemperature - lowerTemperature)).toFloat()
            return lerp(lowerColor, upperColor, fraction)
        }
    }
    return temperatureStops.last().second
}
