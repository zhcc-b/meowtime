package com.example.mytime.ui.theme

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.mytime.ui.ClockState
import com.example.mytime.ui.EdgeLightMode
import com.example.mytime.ui.ThemePreset

internal fun ClockState.effectiveEdgeLightMode(): EdgeLightMode? {
    edgeLightMode?.let { return it }
    return when (activeThemePreset) {
        ThemePreset.FOCUS -> EdgeLightMode.AMBIENT_FOCUS
        ThemePreset.PLAYFUL -> EdgeLightMode.AMBIENT_PLAYFUL
        ThemePreset.SERENE -> EdgeLightMode.AMBIENT_SERENE
        ThemePreset.NIGHT -> EdgeLightMode.AMBIENT_NIGHT
        else -> null
    }
}

@Composable
internal fun EdgeLightOverlay(
    mode: EdgeLightMode?,
    modifier: Modifier = Modifier
) {
    if (mode == null) return

    val transition = rememberInfiniteTransition(label = "edge_light")
    val colorShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    EdgeLightMode.BREAK_REMINDER -> 3600
                    EdgeLightMode.TIMER_ALERT -> 2400
                    EdgeLightMode.STOPWATCH_ACTIVE -> 5400
                    EdgeLightMode.AMBIENT_FOCUS -> 7200
                    EdgeLightMode.AMBIENT_PLAYFUL -> 5600
                    EdgeLightMode.AMBIENT_SERENE -> 10000
                    EdgeLightMode.AMBIENT_NIGHT -> 16000
                },
                easing = LinearEasing
            )
        ),
        label = "edge_color_shift"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    EdgeLightMode.BREAK_REMINDER -> 1000
                    EdgeLightMode.TIMER_ALERT -> 760
                    EdgeLightMode.STOPWATCH_ACTIVE -> 1600
                    EdgeLightMode.AMBIENT_FOCUS -> 2600
                    EdgeLightMode.AMBIENT_PLAYFUL -> 1800
                    EdgeLightMode.AMBIENT_SERENE -> 3200
                    EdgeLightMode.AMBIENT_NIGHT -> 4800
                },
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge_pulse"
    )
    val alertFlash by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 430, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge_alert_flash"
    )

    val overlayAlpha = when (mode) {
        EdgeLightMode.BREAK_REMINDER -> 0.94f
        EdgeLightMode.TIMER_ALERT -> 1f
        EdgeLightMode.STOPWATCH_ACTIVE -> 0.86f
        EdgeLightMode.AMBIENT_FOCUS -> 0.42f
        EdgeLightMode.AMBIENT_PLAYFUL -> 0.62f
        EdgeLightMode.AMBIENT_SERENE -> 0.38f
        EdgeLightMode.AMBIENT_NIGHT -> 0.28f
    }
    val palette = when (mode) {
        EdgeLightMode.BREAK_REMINDER -> listOf(
            Color(0xFF7CFF74),
            Color(0xFF5DEBFF),
            Color(0xFF5D76FF),
            Color(0xFFC46BFF),
            Color(0xFFFF5FB5),
            Color(0xFFFF9C43),
            Color(0xFF7CFF74)
        )
        EdgeLightMode.TIMER_ALERT -> listOf(
            Color(0xFF82FF73),
            Color(0xFF62ECFF),
            Color(0xFF4E77FF),
            Color(0xFFC56EFF),
            Color(0xFFFF6DB9),
            Color(0xFFFFA34E),
            Color(0xFF82FF73)
        )
        EdgeLightMode.STOPWATCH_ACTIVE -> listOf(
            Color(0xFF8EF67A),
            Color(0xFF76E7FF),
            Color(0xFF6491FF),
            Color(0xFFD08AFF),
            Color(0xFFFF8DCA),
            Color(0xFFFFB86E),
            Color(0xFF8EF67A)
        )
        // FOCUS: crisp steel-cyan → electric-blue → icy-white → cobalt — precision & alertness
        EdgeLightMode.AMBIENT_FOCUS -> listOf(
            Color(0xFF00C8FF),
            Color(0xFF00A3FF),
            Color(0xFF2979FF),
            Color(0xFF00E5FF),
            Color(0xFFB3EEFF),
            Color(0xFF2979FF),
            Color(0xFF00C8FF)
        )
        // PLAYFUL: full-spectrum pop — warm coral → gold → lime → cyan → violet → pink loop
        EdgeLightMode.AMBIENT_PLAYFUL -> listOf(
            Color(0xFFFF6B6B),
            Color(0xFFFFD93D),
            Color(0xFF6BCB77),
            Color(0xFF4ECDC4),
            Color(0xFF45B7D1),
            Color(0xFF9B59B6),
            Color(0xFFFF6B6B)
        )
        // SERENE: soft lavender → rose-water → mint → sky — calm & balanced
        EdgeLightMode.AMBIENT_SERENE -> listOf(
            Color(0xFFB8A5E8),
            Color(0xFFE8A5C4),
            Color(0xFFA5D8C8),
            Color(0xFFB8D8F0),
            Color(0xFFD4B8E8),
            Color(0xFFE8C4B8),
            Color(0xFFB8A5E8)
        )
        // NIGHT: deep indigo → midnight violet → dim teal — dim & atmospheric
        EdgeLightMode.AMBIENT_NIGHT -> listOf(
            Color(0xFF1A1A4E),
            Color(0xFF2D1B69),
            Color(0xFF11998E),
            Color(0xFF1A1A4E),
            Color(0xFF38006B),
            Color(0xFF0D3B5E),
            Color(0xFF1A1A4E)
        )
    }

    // Interpolates across the cyclic palette at evenly-spaced display positions offset by
    // colorShift.  Because we sample rather than sort shifted stops, the sweep gradient
    // has no seam at any point on the perimeter — the only requirement is that
    // colors.first() == colors.last() (all palettes above satisfy this).
    fun shiftedStops(colors: List<Color>, alphaMultiplier: Float): Array<Pair<Float, Color>> {
        val alpha = (alphaMultiplier * overlayAlpha * pulse).coerceIn(0f, 1f)
        val n = (colors.size - 1).coerceAtLeast(1)
        val outCount = 48  // enough stops for smooth sweep without hurting perf
        return Array(outCount + 1) { i ->
            val displayPos = i.toFloat() / outCount          // 0.0 … 1.0
            val palettePos  = (displayPos + colorShift) % 1f // cyclic sample offset
            val exactIdx    = palettePos * n
            val lo = exactIdx.toInt().coerceIn(0, n - 1)
            val hi = (lo + 1).coerceAtMost(n)
            val frac = exactIdx - lo
            val c0 = colors[lo]; val c1 = colors[hi]
            val r = c0.red   + (c1.red   - c0.red)   * frac
            val g = c0.green + (c1.green - c0.green) * frac
            val b = c0.blue  + (c1.blue  - c0.blue)  * frac
            displayPos to Color(r, g, b, alpha)
        }
    }

    Canvas(modifier = modifier) {
        fun safeAlpha(value: Float): Float = value.coerceIn(0f, 1f)
        val inset = 6.dp.toPx()
        val outerStroke = when (mode) {
            EdgeLightMode.TIMER_ALERT -> 11.dp.toPx()
            EdgeLightMode.BREAK_REMINDER -> 10.dp.toPx()
            EdgeLightMode.STOPWATCH_ACTIVE -> 9.dp.toPx()
            EdgeLightMode.AMBIENT_FOCUS -> 7.dp.toPx()
            EdgeLightMode.AMBIENT_PLAYFUL -> 8.dp.toPx()
            EdgeLightMode.AMBIENT_SERENE -> 6.dp.toPx()
            EdgeLightMode.AMBIENT_NIGHT -> 4.dp.toPx()
        }
        val glowStroke = when (mode) {
            EdgeLightMode.AMBIENT_NIGHT -> outerStroke * 2.8f
            EdgeLightMode.AMBIENT_SERENE -> outerStroke * 2.4f
            EdgeLightMode.AMBIENT_FOCUS -> outerStroke * 2.2f
            else -> outerStroke * 2.6f
        }
        val coreStroke = outerStroke * 0.26f
        val haloStroke = outerStroke * 1.4f
        val bloomStroke = when (mode) {
            EdgeLightMode.TIMER_ALERT, EdgeLightMode.BREAK_REMINDER -> outerStroke * 7.2f
            EdgeLightMode.STOPWATCH_ACTIVE -> outerStroke * 6.2f
            else -> outerStroke * 8.4f
        }
        val cornerRadiusPx = 42.dp.toPx()
        val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        val rectTopLeft = Offset(inset, inset)
        val rectSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        if (mode == EdgeLightMode.TIMER_ALERT) {
            val warm = Color(0xFFFF4A3D)
            val hot = Color(0xFFFFD36A)
            val flash = alertFlash.coerceIn(0f, 1f)
            val alertColor = Color(
                red = warm.red + (hot.red - warm.red) * flash,
                green = warm.green + (hot.green - warm.green) * flash,
                blue = warm.blue + (hot.blue - warm.blue) * flash,
                alpha = 1f
            )
            val flashAlpha = 0.42f + flash * 0.58f

            drawRect(
                color = alertColor.copy(alpha = safeAlpha(0.055f + flash * 0.055f))
            )
            listOf(
                outerStroke * 8.0f to 0.12f,
                outerStroke * 5.2f to 0.22f,
                outerStroke * 3.0f to 0.34f,
                outerStroke * 1.5f to 0.52f
            ).forEach { (width, alpha) ->
                drawRoundRect(
                    color = alertColor.copy(alpha = safeAlpha(alpha * flashAlpha)),
                    topLeft = rectTopLeft,
                    size = rectSize,
                    cornerRadius = corner,
                    style = Stroke(width = width)
                )
            }
            drawRoundRect(
                color = alertColor.copy(alpha = safeAlpha(0.92f * flashAlpha)),
                topLeft = rectTopLeft,
                size = rectSize,
                cornerRadius = corner,
                style = Stroke(width = outerStroke * (1.08f + flash * 0.18f))
            )
            drawRoundRect(
                color = Color.White.copy(alpha = safeAlpha(0.42f + flash * 0.42f)),
                topLeft = rectTopLeft,
                size = rectSize,
                cornerRadius = corner,
                style = Stroke(width = coreStroke * (1.1f + flash * 0.3f))
            )
            return@Canvas
        }
        val glowBrush = Brush.sweepGradient(
            colorStops = shiftedStops(palette, when (mode) {
                EdgeLightMode.AMBIENT_NIGHT -> 0.20f
                EdgeLightMode.AMBIENT_SERENE -> 0.30f
                EdgeLightMode.AMBIENT_FOCUS -> 0.32f
                else -> 0.36f
            }),
            center = center
        )
        val edgeBrush = Brush.sweepGradient(
            colorStops = shiftedStops(palette, when (mode) {
                EdgeLightMode.AMBIENT_SERENE -> 0.68f
                EdgeLightMode.AMBIENT_NIGHT -> 0.50f
                EdgeLightMode.AMBIENT_FOCUS -> 0.80f
                else -> 0.96f
            }),
            center = center
        )
        val bloomAlpha = when (mode) {
            EdgeLightMode.AMBIENT_NIGHT -> 0.032f
            EdgeLightMode.AMBIENT_SERENE -> 0.044f
            EdgeLightMode.AMBIENT_FOCUS -> 0.050f
            EdgeLightMode.AMBIENT_PLAYFUL -> 0.064f
            else -> 0.082f
        }
        listOf(
            bloomStroke to bloomAlpha * 0.34f,
            bloomStroke * 0.72f to bloomAlpha * 0.58f,
            bloomStroke * 0.48f to bloomAlpha * 0.86f,
            bloomStroke * 0.30f to bloomAlpha
        ).forEach { (width, alpha) ->
            drawRoundRect(
                brush = Brush.sweepGradient(
                    colorStops = shiftedStops(palette, alpha),
                    center = center
                ),
                topLeft = rectTopLeft,
                size = rectSize,
                cornerRadius = corner,
                style = Stroke(width = width)
            )
        }
        drawRoundRect(
            brush = glowBrush,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = corner,
            style = Stroke(width = glowStroke)
        )
        drawRoundRect(
            brush = glowBrush,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = corner,
            style = Stroke(width = haloStroke)
        )
        drawRoundRect(
            brush = edgeBrush,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = corner,
            style = Stroke(width = outerStroke)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = safeAlpha(when (mode) {
                EdgeLightMode.AMBIENT_SERENE -> 0.18f * overlayAlpha * pulse
                EdgeLightMode.AMBIENT_NIGHT -> 0.10f * overlayAlpha * pulse
                EdgeLightMode.AMBIENT_FOCUS -> 0.28f * overlayAlpha * pulse
                else -> 0.38f * overlayAlpha * pulse
            })),
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = corner,
            style = Stroke(width = coreStroke)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = safeAlpha(when (mode) {
                EdgeLightMode.AMBIENT_SERENE -> 0.06f * overlayAlpha
                EdgeLightMode.AMBIENT_NIGHT -> 0.03f * overlayAlpha
                EdgeLightMode.AMBIENT_FOCUS -> 0.10f * overlayAlpha
                else -> 0.12f * overlayAlpha
            })),
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
