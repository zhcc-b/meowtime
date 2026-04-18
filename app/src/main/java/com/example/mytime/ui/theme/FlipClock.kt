package com.example.mytime.ui.theme

import android.content.res.Configuration
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytime.R
import com.example.mytime.ui.ClockMode
import com.example.mytime.ui.ClockFont
import com.example.mytime.ui.ClockState
import com.example.mytime.ui.EdgeLightMode
import com.example.mytime.ui.ParticleWeather
import com.example.mytime.ui.PomodoroPhase
import com.example.mytime.ui.SleepSoundMode
import com.example.mytime.ui.ThemePreset
import com.example.mytime.ui.UiFontFamily
import com.example.mytime.ui.profile
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun FlipClockScreen(
    state: ClockState,
    onPlayAudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onToggleBurnIn: (Boolean) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onSelectFont: (ClockFont) -> Unit,
    onToggleParallax: (Boolean) -> Unit,
    onToggleParticles: (Boolean) -> Unit,
    onToggleParticleWeatherAuto: (Boolean) -> Unit,
    onSelectParticleWeather: (ParticleWeather) -> Unit,
    onToggleCats: (Boolean) -> Unit,
    onToggleDynamicWallpaper: (Boolean) -> Unit,
    onToggle24HourFormat: (Boolean) -> Unit,
    onSetClockMode: (ClockMode) -> Unit,
    onToggleModeRunning: () -> Unit,
    onResetMode: () -> Unit,
    onSetPomodoroFocus: (Int) -> Unit,
    onSetPomodoroBreak: (Int) -> Unit,
    onSetCountdown: (Int) -> Unit,
    onToggleHourlyChime: (Boolean) -> Unit,
    onToggleDailyAlarm: (Boolean) -> Unit,
    onSetDailyAlarmHour: (Int) -> Unit,
    onSetDailyAlarmMinute: (Int) -> Unit,
    onSnoozeDailyAlarm: () -> Unit,
    onDismissDailyAlarm: () -> Unit,
    onToggleBreakReminder: (Boolean) -> Unit,
    onSetThemePreset: (ThemePreset) -> Unit,
    onToggleThemeEdgeLight: (Boolean) -> Unit,
    onSelectSleepSound: (SleepSoundMode) -> Unit,
    onToggleWhiteNoise: (Boolean) -> Unit
) {
    val currentFont = state.selectedFont.family
    var timeRect by remember { mutableStateOf(RectF()) }
    var overlayInfo by remember { mutableStateOf<OverlayInfo?>(null) }
    val settingsDim = if (state.isSettingsVisible) 0.34f else 1f
    val safeParallax = state.parallaxOffset.sanitize()
    val themePresetTitle = stringResource(id = R.string.settings_theme_preset)
    val breakReminderTitle = stringResource(id = R.string.settings_break_reminder)
    val autoPresetInfo = stringResource(id = R.string.theme_info_auto)
    val focusPresetInfo = stringResource(id = R.string.theme_info_focus)
    val playfulPresetInfo = stringResource(id = R.string.theme_info_playful)
    val serenePresetInfo = stringResource(id = R.string.theme_info_serene)
    val nightPresetInfo = stringResource(id = R.string.theme_info_night)
    val breakReminderOnInfo = stringResource(id = R.string.break_reminder_info_on)
    val breakReminderOffInfo = stringResource(id = R.string.break_reminder_info_off)

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 背景层
        val backgroundModifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = 1.25f,
                scaleY = 1.25f,
                rotationX = (-safeParallax.y * 0.05f).coerceIn(-5f, 5f),
                rotationY = (safeParallax.x * 0.05f).coerceIn(-5f, 5f)
            )
            .offset {
                IntOffset(
                    (-safeParallax.x * 0.3f).roundToInt(),
                    (-safeParallax.y * 0.3f).roundToInt()
                )
            }
            .alpha(if (state.isSettingsVisible) 0.24f else 0.6f)

        if (state.backgroundUrl != null) {
            AsyncImage(
                model = state.backgroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = backgroundModifier,
                error = painterResource(id = R.drawable.jiguang),
                placeholder = painterResource(id = R.drawable.jiguang),
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.5f), BlendMode.Multiply)
            )
        } else {
            state.backgroundRes?.let { resId ->
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = backgroundModifier,
                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.5f), BlendMode.Multiply)
                )
            }
        }

        if (state.isParticleSystemEnabled) {
            Box(modifier = Modifier.alpha(if (state.isSettingsVisible) 0.22f else 1f)) {
                SeamlessParticleLayer(weather = state.particleWeather)
            }
        }

        FilamentCatOverlay(
            enabled = state.isCatSystemEnabled,
            weather = state.particleWeather,
            theme = state.activeThemePreset,
            forbiddenRect = timeRect,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (state.isSettingsVisible) 0.18f else 1f)
        )

        val mainDisplayTransform = Modifier
            .graphicsLayer {
                rotationX = (-state.parallaxOffset.y * 0.35f).coerceIn(-15f, 15f)
                rotationY = (state.parallaxOffset.x * 0.35f).coerceIn(-15f, 15f)
                cameraDistance = 15f * density
            }
            .offset {
                // 只对时间主体做位移，避免触控控件命中错位
                IntOffset(
                    (state.parallaxOffset.x * 1.2f + state.burnInOffset.x).roundToInt(),
                    (state.parallaxOffset.y * 1.2f + state.burnInOffset.y).roundToInt()
                )
            }

        ClockContent(
            state = state,
            fontFamily = currentFont,
            onPlayAudio = onPlayAudio,
            onToggleSettings = onOpenSettings,
            onSetClockMode = onSetClockMode,
            onToggleModeRunning = onToggleModeRunning,
            onResetMode = onResetMode,
            onSetPomodoroFocus = onSetPomodoroFocus,
            onSetPomodoroBreak = onSetPomodoroBreak,
            onSetCountdown = onSetCountdown,
            mainDisplayModifier = mainDisplayTransform,
            onTimeBoundsChanged = { timeRect = it },
            modifier = Modifier.alpha(settingsDim)
        )

        if (state.dailyAlarmEnabled && !state.isSettingsVisible) {
            DailyAlarmHint(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 18.dp,
                        bottom = if (state.clockMode == ClockMode.CLOCK) 18.dp else 116.dp
                    )
            )
        }

        SettingsMenu(
            visible = state.isSettingsVisible,
            state = state,
            onClose = onCloseSettings,
            onToggleBurnIn = onToggleBurnIn,
            onToggleSound = onToggleSound,
            onSelectFont = onSelectFont,
            onToggleParallax = onToggleParallax,
            onToggleParticles = onToggleParticles,
            onToggleParticleWeatherAuto = onToggleParticleWeatherAuto,
            onSelectParticleWeather = onSelectParticleWeather,
            onToggleCats = onToggleCats,
            onToggleDynamicWallpaper = onToggleDynamicWallpaper,
            onToggle24HourFormat = onToggle24HourFormat,
            onToggleHourlyChime = onToggleHourlyChime,
            onToggleDailyAlarm = onToggleDailyAlarm,
            onSetDailyAlarmHour = onSetDailyAlarmHour,
            onSetDailyAlarmMinute = onSetDailyAlarmMinute,
            onToggleBreakReminder = { enabled ->
                onToggleBreakReminder(enabled)
                overlayInfo = OverlayInfo(
                    title = breakReminderTitle,
                    body = if (enabled) breakReminderOnInfo else breakReminderOffInfo
                )
            },
            onSetThemePreset = { preset ->
                onSetThemePreset(preset)
                overlayInfo = OverlayInfo(
                    title = themePresetTitle,
                    body = when (preset) {
                        ThemePreset.AUTO -> autoPresetInfo
                        ThemePreset.FOCUS -> focusPresetInfo
                        ThemePreset.PLAYFUL -> playfulPresetInfo
                        ThemePreset.SERENE -> serenePresetInfo
                        ThemePreset.NIGHT -> nightPresetInfo
                    }
                )
            },
            onToggleThemeEdgeLight = onToggleThemeEdgeLight,
            onSelectSleepSound = onSelectSleepSound,
            onToggleWhiteNoise = onToggleWhiteNoise
        )

        EdgeLightOverlay(
            mode = state.effectiveEdgeLightMode(),
            modifier = Modifier.fillMaxSize()
        )

        overlayInfo?.let { info ->
            ExplanationOverlay(
                info = info,
                onDismiss = { overlayInfo = null }
            )
        }

        if (state.isDailyAlarmRinging) {
            DailyAlarmDialog(
                state = state,
                onSnooze = onSnoozeDailyAlarm,
                onDismiss = onDismissDailyAlarm
            )
        }
    }
}

@Composable
fun SeamlessParticleLayer(weather: ParticleWeather) {
    val particles = remember(weather) { List(weather.particleCount) { WeatherParticle(weather) } }
    val atmosphereBlobs = remember(weather) { List(weather.atmosphereBlobCount) { AtmosphereBlob(weather) } }
    var elapsedMillis by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it } / 1_000_000
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                elapsedMillis = (frameTimeNanos / 1_000_000) - startTime
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val seconds = elapsedMillis / 1000f
        drawWeatherBackdrop(weather = weather, seconds = seconds)
        drawAtmosphereBlobs(weather = weather, seconds = seconds, blobs = atmosphereBlobs)
        drawFogVeils(weather = weather, seconds = seconds)
        drawWeatherParticles(weather = weather, seconds = seconds, particles = particles)
    }
}

private data class WeatherPalette(
    val skyTop: Color,
    val skyMid: Color,
    val skyBottom: Color,
    val glow: Color,
    val cloud: Color,
    val fog: Color,
    val accent: Color
)

private data class WeatherParticle(
    val startX: Float,
    val startY: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
    val length: Float,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val phase: Float
)

private data class AtmosphereBlob(
    val startX: Float,
    val startY: Float,
    val widthFactor: Float,
    val heightFactor: Float,
    val alpha: Float,
    val speed: Float,
    val phase: Float
)

private val LiquidGlassTint = Color(0xFFDCEBFF)
private val LiquidGlassCool = Color(0xFFA8CDFF)
private val LiquidGlassShadow = Color(0xFF060E18)
private val LiquidGlassText = Color(0xFFF4F9FF)

private data class OverlayInfo(
    val title: String,
    val body: String
)

private fun ClockState.effectiveEdgeLightMode(): EdgeLightMode {
    if (edgeLightMode != EdgeLightMode.NONE) return edgeLightMode
    if (!isThemeEdgeLightEnabled) return EdgeLightMode.NONE
    return when (activeThemePreset) {
        ThemePreset.FOCUS -> EdgeLightMode.AMBIENT_FOCUS
        ThemePreset.PLAYFUL -> EdgeLightMode.AMBIENT_PLAYFUL
        ThemePreset.SERENE -> EdgeLightMode.AMBIENT_SERENE
        ThemePreset.NIGHT -> EdgeLightMode.AMBIENT_NIGHT
        else -> EdgeLightMode.NONE
    }
}

@Composable
private fun ExplanationOverlay(
    info: OverlayInfo,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC07111A))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        SettingsPanelSurface(
            modifier = Modifier
                .padding(horizontal = 26.dp)
                .fillMaxWidth(0.82f)
                .clickable(onClick = onDismiss),
            shape = RoundedCornerShape(30.dp),
            padding = PaddingValues(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = info.title,
                    color = LiquidGlassText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = UiFontFamily
                )
                Text(
                    text = info.body,
                    color = LiquidGlassText.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontFamily = UiFontFamily
                )
                Text(
                    text = stringResource(id = R.string.tap_anywhere_to_close),
                    color = LiquidGlassText.copy(alpha = 0.48f),
                    fontSize = 12.sp,
                    fontFamily = UiFontFamily
                )
            }
        }
    }
}

@Composable
private fun EdgeLightOverlay(
    mode: EdgeLightMode,
    modifier: Modifier = Modifier
) {
    if (mode == EdgeLightMode.NONE) return

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
                    EdgeLightMode.NONE -> 4000
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
                    EdgeLightMode.NONE -> 1000
                },
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge_pulse"
    )

    val overlayAlpha = when (mode) {
        EdgeLightMode.BREAK_REMINDER -> 0.94f
        EdgeLightMode.TIMER_ALERT -> 1f
        EdgeLightMode.STOPWATCH_ACTIVE -> 0.86f
        EdgeLightMode.AMBIENT_FOCUS -> 0.42f
        EdgeLightMode.AMBIENT_PLAYFUL -> 0.62f
        EdgeLightMode.AMBIENT_SERENE -> 0.38f
        EdgeLightMode.AMBIENT_NIGHT -> 0.28f
        EdgeLightMode.NONE -> 0f
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
        EdgeLightMode.NONE -> emptyList()
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
            EdgeLightMode.NONE -> 0f
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
            EdgeLightMode.TIMER_ALERT, EdgeLightMode.BREAK_REMINDER -> outerStroke * 6f
            EdgeLightMode.STOPWATCH_ACTIVE -> outerStroke * 5f
            else -> outerStroke * 8f
        }
        val cornerRadiusPx = 42.dp.toPx()
        val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        val rectTopLeft = Offset(inset, inset)
        val rectSize = Size(size.width - inset * 2f, size.height - inset * 2f)
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
        // Wide low-alpha stroke: inner half produces a backlit screen-bloom effect.
        val bloomBrush = Brush.sweepGradient(
            colorStops = shiftedStops(palette, when (mode) {
                EdgeLightMode.AMBIENT_NIGHT -> 0.04f
                EdgeLightMode.AMBIENT_SERENE -> 0.06f
                EdgeLightMode.AMBIENT_FOCUS -> 0.06f
                EdgeLightMode.AMBIENT_PLAYFUL -> 0.09f
                else -> 0.14f
            }),
            center = center
        )

        drawRoundRect(
            brush = bloomBrush,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = corner,
            style = Stroke(width = bloomStroke)
        )
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

private fun AtmosphereBlob(weather: ParticleWeather): AtmosphereBlob {
    return when (weather) {
        ParticleWeather.SUNNY -> AtmosphereBlob(
            startX = Random.nextFloat(),
            startY = 0.08f + Random.nextFloat() * 0.22f,
            widthFactor = 0.20f + Random.nextFloat() * 0.18f,
            heightFactor = 0.07f + Random.nextFloat() * 0.05f,
            alpha = 0.08f + Random.nextFloat() * 0.08f,
            speed = 0.004f + Random.nextFloat() * 0.006f,
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.CLOUDY -> AtmosphereBlob(
            startX = Random.nextFloat(),
            startY = 0.08f + Random.nextFloat() * 0.36f,
            widthFactor = 0.26f + Random.nextFloat() * 0.24f,
            heightFactor = 0.10f + Random.nextFloat() * 0.08f,
            alpha = 0.11f + Random.nextFloat() * 0.12f,
            speed = 0.003f + Random.nextFloat() * 0.006f,
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.FOG -> AtmosphereBlob(
            startX = Random.nextFloat(),
            startY = 0.20f + Random.nextFloat() * 0.50f,
            widthFactor = 0.34f + Random.nextFloat() * 0.28f,
            heightFactor = 0.11f + Random.nextFloat() * 0.08f,
            alpha = 0.10f + Random.nextFloat() * 0.10f,
            speed = 0.002f + Random.nextFloat() * 0.004f,
            phase = Random.nextFloat() * 6.28f
        )
        else -> AtmosphereBlob(
            startX = Random.nextFloat(),
            startY = 0.04f + Random.nextFloat() * 0.34f,
            widthFactor = 0.24f + Random.nextFloat() * 0.24f,
            heightFactor = 0.10f + Random.nextFloat() * 0.08f,
            alpha = 0.10f + Random.nextFloat() * 0.12f,
            speed = 0.004f + Random.nextFloat() * 0.008f,
            phase = Random.nextFloat() * 6.28f
        )
    }
}

private fun WeatherParticle(weather: ParticleWeather): WeatherParticle {
    return when (weather) {
        ParticleWeather.SUNNY -> WeatherParticle(
            startX = Random.nextFloat(),
            startY = Random.nextFloat(),
            speed = 0.015f + Random.nextFloat() * 0.025f,
            size = 0.8f + Random.nextFloat() * 1.6f,
            alpha = 0.04f + Random.nextFloat() * 0.08f,
            length = 0f,
            swayAmplitude = 0.01f + Random.nextFloat() * 0.02f,
            swayFrequency = 0.6f + Random.nextFloat() * 1.1f,
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.CLOUDY -> WeatherParticle(
            startX = Random.nextFloat(),
            startY = Random.nextFloat(),
            speed = 0.02f + Random.nextFloat() * 0.03f,
            size = 0.8f + Random.nextFloat() * 1.2f,
            alpha = 0.03f + Random.nextFloat() * 0.07f,
            length = 10f + Random.nextFloat() * 8f,
            swayAmplitude = 0.008f + Random.nextFloat() * 0.014f,
            swayFrequency = 0.6f + Random.nextFloat() * 1.0f,
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.FOG -> WeatherParticle(
            startX = Random.nextFloat(),
            startY = 0.45f + Random.nextFloat() * 0.45f,
            speed = 0.01f + Random.nextFloat() * 0.02f,
            size = 1.2f + Random.nextFloat() * 2.0f,
            alpha = 0.03f + Random.nextFloat() * 0.06f,
            length = 16f + Random.nextFloat() * 18f,
            swayAmplitude = 0.01f + Random.nextFloat() * 0.018f,
            swayFrequency = 0.5f + Random.nextFloat() * 0.8f,
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.RAIN -> WeatherParticle(
            startX = Random.nextFloat(),
            startY = Random.nextFloat(),
            speed = 0.55f + Random.nextFloat() * 0.55f,
            size = 1.8f + Random.nextFloat() * 2.1f,
            alpha = 0.15f + Random.nextFloat() * 0.35f,
            length = 20f + Random.nextFloat() * 18f,
            swayAmplitude = 0.005f + Random.nextFloat() * 0.015f,
            swayFrequency = 1f + Random.nextFloat() * 1.2f,
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.DRIZZLE -> WeatherParticle(
            startX = Random.nextFloat(),
            startY = Random.nextFloat(),
            speed = 0.35f + Random.nextFloat() * 0.35f,
            size = 1.2f + Random.nextFloat() * 1.4f,
            alpha = 0.1f + Random.nextFloat() * 0.2f,
            length = 14f + Random.nextFloat() * 12f,
            swayAmplitude = 0.01f + Random.nextFloat() * 0.02f,
            swayFrequency = 1.2f + Random.nextFloat(),
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.SNOW -> WeatherParticle(
            startX = Random.nextFloat(),
            startY = Random.nextFloat(),
            speed = 0.018f + Random.nextFloat() * 0.045f,
            size = 2.4f + Random.nextFloat() * 4.4f,
            alpha = 0.18f + Random.nextFloat() * 0.45f,
            length = 0f,
            swayAmplitude = 0.016f + Random.nextFloat() * 0.038f,
            swayFrequency = 0.7f + Random.nextFloat() * 1.4f,
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.BLIZZARD -> WeatherParticle(
            startX = Random.nextFloat(),
            startY = Random.nextFloat(),
            speed = 0.10f + Random.nextFloat() * 0.14f,
            size = 2.0f + Random.nextFloat() * 3.6f,
            alpha = 0.16f + Random.nextFloat() * 0.32f,
            length = 0f,
            swayAmplitude = 0.05f + Random.nextFloat() * 0.06f,
            swayFrequency = 2.2f + Random.nextFloat() * 1.4f,
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.HAIL -> WeatherParticle(
            startX = Random.nextFloat(),
            startY = Random.nextFloat(),
            speed = 0.65f + Random.nextFloat() * 0.85f,
            size = 2.4f + Random.nextFloat() * 3.8f,
            alpha = 0.2f + Random.nextFloat() * 0.5f,
            length = 0f,
            swayAmplitude = 0.002f + Random.nextFloat() * 0.008f,
            swayFrequency = 0.5f + Random.nextFloat(),
            phase = Random.nextFloat() * 6.28f
        )
        ParticleWeather.WIND -> WeatherParticle(
            startX = Random.nextFloat(),
            startY = Random.nextFloat(),
            speed = 0.16f + Random.nextFloat() * 0.35f,
            size = 0.7f + Random.nextFloat() * 1.4f,
            alpha = 0.1f + Random.nextFloat() * 0.28f,
            length = 12f + Random.nextFloat() * 20f,
            swayAmplitude = 0.006f + Random.nextFloat() * 0.02f,
            swayFrequency = 0.8f + Random.nextFloat() * 1.3f,
            phase = Random.nextFloat() * 6.28f
        )
    }
}

private val ParticleWeather.particleCount: Int
    get() = when (this) {
        ParticleWeather.SUNNY -> 18
        ParticleWeather.CLOUDY -> 24
        ParticleWeather.FOG -> 30
        ParticleWeather.RAIN -> 120
        ParticleWeather.DRIZZLE -> 100
        ParticleWeather.SNOW -> 80
        ParticleWeather.BLIZZARD -> 130
        ParticleWeather.HAIL -> 110
        ParticleWeather.WIND -> 90
    }

private val ParticleWeather.atmosphereBlobCount: Int
    get() = when (this) {
        ParticleWeather.SUNNY -> 5
        ParticleWeather.CLOUDY -> 7
        ParticleWeather.FOG -> 8
        ParticleWeather.RAIN -> 8
        ParticleWeather.DRIZZLE -> 7
        ParticleWeather.SNOW -> 7
        ParticleWeather.BLIZZARD -> 10
        ParticleWeather.HAIL -> 8
        ParticleWeather.WIND -> 6
    }

private val ParticleWeather.palette: WeatherPalette
    get() = when (this) {
        ParticleWeather.SUNNY -> WeatherPalette(
            skyTop = Color(0xFF4A8DFF),
            skyMid = Color(0xFF77B7FF),
            skyBottom = Color(0xFFF2C98A),
            glow = Color(0xFFFFE9A8),
            cloud = Color(0xFFF8F3EA),
            fog = Color(0x66FFF3D6),
            accent = Color(0xFFFFF7DA)
        )
        ParticleWeather.CLOUDY -> WeatherPalette(
            skyTop = Color(0xFF52677A),
            skyMid = Color(0xFF75889C),
            skyBottom = Color(0xFFB1B6BE),
            glow = Color(0x88E7EEF7),
            cloud = Color(0xFFD8DEE6),
            fog = Color(0x66DDE5EE),
            accent = Color(0xFFF0F6FF)
        )
        ParticleWeather.FOG -> WeatherPalette(
            skyTop = Color(0xFF67737F),
            skyMid = Color(0xFF8A949D),
            skyBottom = Color(0xFFBCC4CA),
            glow = Color(0x55E7EDF2),
            cloud = Color(0xCCDCE3E8),
            fog = Color(0x88EFF4F6),
            accent = Color(0xFFF5F8FA)
        )
        ParticleWeather.RAIN -> WeatherPalette(
            skyTop = Color(0xFF24364C),
            skyMid = Color(0xFF3A4E66),
            skyBottom = Color(0xFF65788B),
            glow = Color(0x444C76B5),
            cloud = Color(0xFFBBC7D6),
            fog = Color(0x445B6E86),
            accent = Color(0xFFC7E1FF)
        )
        ParticleWeather.DRIZZLE -> WeatherPalette(
            skyTop = Color(0xFF314760),
            skyMid = Color(0xFF536A80),
            skyBottom = Color(0xFF8A9BAB),
            glow = Color(0x334F7AAD),
            cloud = Color(0xFFD1D9E1),
            fog = Color(0x446E8195),
            accent = Color(0xFFD7E8FF)
        )
        ParticleWeather.SNOW -> WeatherPalette(
            skyTop = Color(0xFF50667B),
            skyMid = Color(0xFF73899D),
            skyBottom = Color(0xFFB5C3CF),
            glow = Color(0x44DDEAF8),
            cloud = Color(0xFFE6EEF5),
            fog = Color(0x55F2F8FF),
            accent = Color(0xFFF8FBFF)
        )
        ParticleWeather.BLIZZARD -> WeatherPalette(
            skyTop = Color(0xFF43576A),
            skyMid = Color(0xFF607486),
            skyBottom = Color(0xFF9FB0BC),
            glow = Color(0x33D5E5F2),
            cloud = Color(0xFFE1E9F0),
            fog = Color(0x66F0F6FB),
            accent = Color(0xFFF6FBFF)
        )
        ParticleWeather.HAIL -> WeatherPalette(
            skyTop = Color(0xFF283445),
            skyMid = Color(0xFF465668),
            skyBottom = Color(0xFF768693),
            glow = Color(0x334F637E),
            cloud = Color(0xFFD4DFEA),
            fog = Color(0x445E7186),
            accent = Color(0xFFE9F6FF)
        )
        ParticleWeather.WIND -> WeatherPalette(
            skyTop = Color(0xFF5A738A),
            skyMid = Color(0xFF7E96A8),
            skyBottom = Color(0xFFC6D0D8),
            glow = Color(0x33F3E8D9),
            cloud = Color(0xFFE4E9ED),
            fog = Color(0x44EDF0F3),
            accent = Color(0xFFF7F7F2)
        )
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWeatherBackdrop(
    weather: ParticleWeather,
    seconds: Float
) {
    val palette = weather.palette
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.skyTop, palette.skyMid, palette.skyBottom)
        ),
        size = size
    )

    val glowCenter = when (weather) {
        ParticleWeather.SUNNY -> Offset(size.width * 0.84f, size.height * 0.18f)
        ParticleWeather.CLOUDY -> Offset(size.width * 0.74f, size.height * 0.22f)
        ParticleWeather.FOG -> Offset(size.width * 0.50f, size.height * 0.30f)
        ParticleWeather.SNOW, ParticleWeather.BLIZZARD -> Offset(size.width * 0.58f, size.height * 0.22f)
        else -> Offset(size.width * 0.68f, size.height * 0.18f)
    }
    val glowRadius = size.minDimension * when (weather) {
        ParticleWeather.SUNNY -> 0.42f
        ParticleWeather.CLOUDY -> 0.32f
        ParticleWeather.FOG -> 0.28f
        else -> 0.24f
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.glow.copy(alpha = 0.95f), Color.Transparent),
            center = glowCenter,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = glowCenter
    )

    val horizonAlpha = when (weather) {
        ParticleWeather.SUNNY -> 0.15f
        ParticleWeather.CLOUDY -> 0.09f
        ParticleWeather.FOG -> 0.25f
        ParticleWeather.SNOW, ParticleWeather.BLIZZARD -> 0.20f
        else -> 0.12f
    } + sin(seconds * 0.08f).coerceIn(-1f, 1f) * 0.015f

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, palette.fog.copy(alpha = horizonAlpha))
        ),
        topLeft = Offset(0f, size.height * 0.46f),
        size = Size(size.width, size.height * 0.54f)
    )

    if (weather != ParticleWeather.SUNNY) {
        drawNightStars(weather = weather, seconds = seconds)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNightStars(
    weather: ParticleWeather,
    seconds: Float
) {
    val count = when (weather) {
        ParticleWeather.SNOW -> 70
        ParticleWeather.BLIZZARD -> 82
        ParticleWeather.CLOUDY -> 52
        ParticleWeather.RAIN, ParticleWeather.DRIZZLE -> 42
        else -> 46
    }
    repeat(count) { index ->
        val seed = index * 0.173f
        val x = ((seed * 37.2f) % 1f) * size.width
        val y = (((seed * 91.7f) % 1f) * 0.62f + 0.02f) * size.height
        val twinkle = 0.32f + 0.28f * (0.5f + 0.5f * sin(seconds * 0.7f + index))
        val radius = if (index % 9 == 0) 2.4f else 1.2f + (index % 3) * 0.5f
        drawCircle(
            color = Color.White.copy(alpha = twinkle * if (weather == ParticleWeather.BLIZZARD) 0.7f else 1f),
            radius = radius,
            center = Offset(x, y)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAtmosphereBlobs(
    weather: ParticleWeather,
    seconds: Float,
    blobs: List<AtmosphereBlob>
) {
    val palette = weather.palette
    blobs.forEach { blob ->
        val x = (blob.startX + seconds * blob.speed).wrap(1f) * size.width
        val y = blob.startY * size.height + sin(seconds * 0.1f + blob.phase) * size.height * 0.02f
        val width = size.width * blob.widthFactor
        val height = size.height * blob.heightFactor
        val cloudAlpha = blob.alpha * when (weather) {
            ParticleWeather.SUNNY -> 0.9f
            ParticleWeather.CLOUDY, ParticleWeather.FOG -> 1.05f
            else -> 1.0f
        }
        // Draw wrapped copies on both sides to avoid visible popping when x wraps from right edge back to left.
        listOf(-size.width, 0f, size.width).forEach { shift ->
            drawCloudCluster(
                center = Offset(x + shift, y),
                size = Size(width, height),
                color = palette.cloud.copy(alpha = cloudAlpha)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloudCluster(
    center: Offset,
    size: Size,
    color: Color
) {
    val left = Offset(center.x - size.width * 0.24f, center.y + size.height * 0.08f)
    val right = Offset(center.x + size.width * 0.24f, center.y + size.height * 0.04f)
    val top = Offset(center.x, center.y - size.height * 0.10f)
    drawOval(
        color = color.copy(alpha = color.alpha * 0.72f),
        topLeft = Offset(left.x - size.width * 0.30f, left.y - size.height * 0.26f),
        size = Size(size.width * 0.60f, size.height * 0.52f)
    )
    drawOval(
        color = color.copy(alpha = color.alpha),
        topLeft = Offset(top.x - size.width * 0.34f, top.y - size.height * 0.32f),
        size = Size(size.width * 0.68f, size.height * 0.64f)
    )
    drawOval(
        color = color.copy(alpha = color.alpha * 0.80f),
        topLeft = Offset(right.x - size.width * 0.28f, right.y - size.height * 0.24f),
        size = Size(size.width * 0.56f, size.height * 0.48f)
    )
    drawRoundRect(
        color = color.copy(alpha = color.alpha * 0.76f),
        topLeft = Offset(center.x - size.width * 0.42f, center.y - size.height * 0.02f),
        size = Size(size.width * 0.84f, size.height * 0.34f),
        cornerRadius = CornerRadius(size.height * 0.22f, size.height * 0.22f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFogVeils(
    weather: ParticleWeather,
    seconds: Float
) {
    if (weather !in listOf(ParticleWeather.FOG, ParticleWeather.CLOUDY, ParticleWeather.DRIZZLE, ParticleWeather.SNOW, ParticleWeather.BLIZZARD)) {
        return
    }
    val palette = weather.palette
    val bandCount = when (weather) {
        ParticleWeather.FOG -> 4
        ParticleWeather.CLOUDY -> 2
        else -> 3
    }
    repeat(bandCount) { index ->
        val y = size.height * (0.50f + index * 0.10f)
        val drift = ((seconds * (0.012f + index * 0.005f)) + index * 0.13f).wrap(1f) * size.width
        val alpha = when (weather) {
            ParticleWeather.FOG -> 0.12f
            ParticleWeather.CLOUDY -> 0.045f
            else -> 0.09f
        } + sin(seconds * 0.18f + index).coerceIn(-1f, 1f) * 0.01f
        listOf(-size.width, 0f, size.width).forEach { shift ->
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.fog.copy(alpha = alpha),
                        palette.fog.copy(alpha = alpha * 0.7f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(drift + shift - size.width * 0.55f, y),
                size = Size(size.width * 1.10f, size.height * 0.09f),
                cornerRadius = CornerRadius(size.height * 0.05f, size.height * 0.05f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWeatherParticles(
    weather: ParticleWeather,
    seconds: Float,
    particles: List<WeatherParticle>
) {
    val color = weather.palette.accent
    particles.forEach { p ->
        when (weather) {
            ParticleWeather.SUNNY -> {
                val y = (p.startY + seconds * p.speed).wrap(1f) * size.height
                val x = (p.startX * size.width + sin(seconds * p.swayFrequency + p.phase) * p.swayAmplitude * size.width).wrap(size.width)
                drawCircle(
                    color = color.copy(alpha = p.alpha),
                    radius = p.size,
                    center = Offset(x, y)
                )
            }

            ParticleWeather.CLOUDY, ParticleWeather.FOG -> {
                val xProgress = (p.startX + seconds * p.speed).wrap(1f)
                val x = xProgress * size.width
                val y = p.startY * size.height + sin(seconds * p.swayFrequency + p.phase) * p.swayAmplitude * size.height
                drawLine(
                    color = color.copy(alpha = p.alpha),
                    start = Offset(x, y),
                    end = Offset(x + p.length, y + p.length * 0.04f),
                    strokeWidth = p.size,
                    cap = StrokeCap.Round
                )
            }

            ParticleWeather.WIND -> {
                val xProgress = (p.startX + seconds * p.speed).wrap(1f)
                val y = p.startY * size.height + sin(seconds * p.swayFrequency + p.phase) * p.swayAmplitude * size.height
                val x = xProgress * size.width
                val edgeAlpha = when {
                    xProgress < 0.08f -> xProgress / 0.08f
                    xProgress > 0.92f -> (1f - xProgress) / 0.08f
                    else -> 1f
                }
                drawLine(
                    color = color.copy(alpha = p.alpha * edgeAlpha),
                    start = Offset(x, y),
                    end = Offset(x + p.length * 1.8f, y + p.length * 0.1f),
                    strokeWidth = p.size,
                    cap = StrokeCap.Round
                )
            }

            ParticleWeather.RAIN, ParticleWeather.DRIZZLE -> {
                val yProgress = (p.startY + (seconds * p.speed)).wrap(1f)
                val sway = sin(seconds * p.swayFrequency + p.phase) * p.swayAmplitude * size.width
                val xBase = (p.startX * size.width + sway).wrap(size.width)
                val y = yProgress * size.height
                val edgeAlpha = when {
                    yProgress < 0.08f -> yProgress / 0.08f
                    yProgress > 0.92f -> (1f - yProgress) / 0.08f
                    else -> 1f
                }
                drawLine(
                    color = color.copy(alpha = p.alpha * edgeAlpha),
                    start = Offset(xBase, y),
                    end = Offset(xBase - p.length * 0.35f, y + p.length),
                    strokeWidth = p.size,
                    cap = StrokeCap.Round
                )
            }

            ParticleWeather.SNOW, ParticleWeather.BLIZZARD -> {
                val yProgress = (p.startY + (seconds * p.speed)).wrap(1f)
                val sway = sin(seconds * p.swayFrequency + p.phase) * p.swayAmplitude * size.width
                val x = (p.startX * size.width + sway).wrap(size.width)
                val y = yProgress * size.height
                val edgeAlpha = when {
                    yProgress < 0.1f -> yProgress / 0.1f
                    yProgress > 0.9f -> (1f - yProgress) / 0.1f
                    else -> 1f
                }
                drawSnowflake(
                    center = Offset(x, y),
                    radius = p.size,
                    color = color.copy(alpha = p.alpha * edgeAlpha),
                    stroke = (p.size * 0.28f).coerceAtLeast(0.8f)
                )
            }

            ParticleWeather.HAIL -> {
                val yProgress = (p.startY + (seconds * p.speed)).wrap(1f)
                val x = p.startX * size.width
                val y = yProgress * size.height
                val edgeAlpha = when {
                    yProgress < 0.08f -> yProgress / 0.08f
                    yProgress > 0.92f -> (1f - yProgress) / 0.08f
                    else -> 1f
                }
                drawCircle(
                    color = color.copy(alpha = p.alpha * edgeAlpha),
                    radius = p.size,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = Color.White.copy(alpha = p.alpha * 0.2f * edgeAlpha),
                    radius = p.size * 0.5f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

private fun Float.wrap(max: Float): Float {
    if (max <= 0f) return this
    var v = this % max
    if (v < 0f) v += max
    return v
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnowflake(
    center: Offset,
    radius: Float,
    color: Color,
    stroke: Float
) {
    val r = radius.coerceAtLeast(1.2f)
    val arm = r * 1.25f
    val branch = arm * 0.32f

    // 3 axes = 6-point snowflake
    repeat(3) { i ->
        val angle = i * (Math.PI / 3.0)
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        val start = Offset(center.x - dx * arm, center.y - dy * arm)
        val end = Offset(center.x + dx * arm, center.y + dy * arm)
        drawLine(color = color, start = start, end = end, strokeWidth = stroke, cap = StrokeCap.Round)

        val px = center.x + dx * arm * 0.58f
        val py = center.y + dy * arm * 0.58f
        val nx = -dy
        val ny = dx
        drawLine(
            color = color,
            start = Offset(px, py),
            end = Offset(px + nx * branch, py + ny * branch),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(px, py),
            end = Offset(px - nx * branch, py - ny * branch),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    padding: PaddingValues = PaddingValues(0.dp),
    highlightAlpha: Float = 0.22f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        LiquidGlassTint.copy(alpha = 0.10f),
                        LiquidGlassCool.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = shape
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.48f),
                        Color.White.copy(alpha = 0.12f),
                        LiquidGlassCool.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
            .drawWithContent {
                drawContent()
                // Top specular highlight.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = highlightAlpha * 0.8f),
                            Color.Transparent
                        ),
                        endY = size.height * 0.38f
                    )
                )
                // Bottom inner shadow.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            LiquidGlassShadow.copy(alpha = 0.08f)
                        ),
                        startY = size.height * 0.65f
                    )
                )
            }
            .padding(padding),
        content = content
    )
}

@Composable
private fun LiquidGlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        highlightAlpha = if (selected) 0.16f else 0.08f
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    if (selected) {
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF24364A).copy(alpha = 0.88f),
                                Color(0xFF182636).copy(alpha = 0.92f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF162231).copy(alpha = 0.78f),
                                Color(0xFF101B28).copy(alpha = 0.84f)
                            )
                        )
                    }
                )
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.08f),
                                LiquidGlassCool.copy(alpha = 0.04f)
                            )
                        )
                    )
            )
        }
        content()
    }
}

@Composable
private fun SettingsPanelSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(34.dp),
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E2A38).copy(alpha = 0.93f),
                        Color(0xFF151E2B).copy(alpha = 0.96f),
                        Color(0xFF0D1520).copy(alpha = 0.98f)
                    )
                ),
                shape = shape
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f)
                        )
                    )
                )
            }
            .padding(padding),
        content = content
    )
}

@Composable
private fun SettingsCardSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1C2734).copy(alpha = 0.92f),
                        Color(0xFF141D29).copy(alpha = 0.95f)
                    )
                ),
                shape = shape
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = shape
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.06f)
                        )
                    )
                )
            }
            .padding(padding),
        content = content
    )
}

@Composable
private fun SettingsChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    SettingsCardSurface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF2B3A4B).copy(alpha = 0.94f),
                                Color(0xFF1C2A39).copy(alpha = 0.98f)
                            )
                        )
                    )
            )
        }
        content()
    }
}

@Composable
private fun SettingsMenu(
    visible: Boolean,
    state: ClockState,
    onClose: () -> Unit,
    onToggleBurnIn: (Boolean) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onSelectFont: (ClockFont) -> Unit,
    onToggleParallax: (Boolean) -> Unit,
    onToggleParticles: (Boolean) -> Unit,
    onToggleParticleWeatherAuto: (Boolean) -> Unit,
    onSelectParticleWeather: (ParticleWeather) -> Unit,
    onToggleCats: (Boolean) -> Unit,
    onToggleDynamicWallpaper: (Boolean) -> Unit,
    onToggle24HourFormat: (Boolean) -> Unit,
    onToggleHourlyChime: (Boolean) -> Unit,
    onToggleDailyAlarm: (Boolean) -> Unit,
    onSetDailyAlarmHour: (Int) -> Unit,
    onSetDailyAlarmMinute: (Int) -> Unit,
    onToggleBreakReminder: (Boolean) -> Unit,
    onSetThemePreset: (ThemePreset) -> Unit,
    onToggleThemeEdgeLight: (Boolean) -> Unit,
    onSelectSleepSound: (SleepSoundMode) -> Unit,
    onToggleWhiteNoise: (Boolean) -> Unit
) {
    if (visible) {
        BackHandler(onBack = onClose)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06111D).copy(alpha = 0.58f))
        ) {
            val drawerWidth = (maxWidth * 0.88f).coerceAtMost(348.dp)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onClose() }
                )
                SettingsPanelSurface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(drawerWidth),
                    shape = RoundedCornerShape(topStart = 34.dp, bottomStart = 34.dp),
                    padding = PaddingValues(horizontal = 22.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            stringResource(id = R.string.settings_title),
                            color = LiquidGlassText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.settings_subtitle),
                            color = LiquidGlassText.copy(alpha = 0.54f),
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        SettingToggle(stringResource(id = R.string.settings_gyroscope), state.isParallaxEnabled, onToggleParallax)
                        SettingToggle(stringResource(id = R.string.settings_particles), state.isParticleSystemEnabled, onToggleParticles)
                        if (state.isParticleSystemEnabled) {
                            ParticleWeatherSelector(
                                isAuto = state.isParticleWeatherAuto,
                                selected = state.particleWeather,
                                onToggleAuto = onToggleParticleWeatherAuto,
                                onSelectWeather = onSelectParticleWeather
                            )
                        }
                        SettingToggle(stringResource(id = R.string.settings_cats), state.isCatSystemEnabled, onToggleCats)
                        SettingToggle(stringResource(id = R.string.settings_wallpaper), state.isDynamicWallpaperEnabled, onToggleDynamicWallpaper)
                        SettingToggle(stringResource(id = R.string.settings_burnin), state.isBurnInProtectionEnabled, onToggleBurnIn)
                        SettingToggle(stringResource(id = R.string.settings_24_hour), state.is24HourFormat, onToggle24HourFormat)
                        SleepSoundSelector(
                            selected = state.sleepSoundMode,
                            enabled = state.whiteNoiseEnabled,
                            remainingSeconds = state.sleepSoundRemainingSeconds,
                            onEnabledChange = onToggleWhiteNoise,
                            onSelect = onSelectSleepSound
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        SettingToggle(stringResource(id = R.string.settings_sound_button), state.isSoundButtonVisible, onToggleSound)
                        ThemePresetSelector(
                            selected = state.selectedThemePreset,
                            active = state.activeThemePreset,
                            onSelect = onSetThemePreset
                        )
                        SettingToggle(stringResource(id = R.string.settings_theme_edge_light), state.isThemeEdgeLightEnabled, onToggleThemeEdgeLight)
                        SettingToggle(stringResource(id = R.string.settings_hourly_chime), state.hourlyChimeEnabled, onToggleHourlyChime)
                        SettingToggle(stringResource(id = R.string.settings_break_reminder), state.breakReminderEnabled, onToggleBreakReminder)
                        DailyAlarmCard(
                            enabled = state.dailyAlarmEnabled,
                            hour = state.dailyAlarmHour,
                            minute = state.dailyAlarmMinute,
                            onEnabledChange = onToggleDailyAlarm,
                            onSetHour = onSetDailyAlarmHour,
                            onSetMinute = onSetDailyAlarmMinute
                        )
                        SettingsCardSurface(
                            shape = RoundedCornerShape(24.dp),
                            padding = PaddingValues(16.dp)
                        ) {
                            Column {
                                Text(
                                    stringResource(id = R.string.settings_clock_font),
                                    color = LiquidGlassText.copy(alpha = 0.68f),
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(state.allFonts) { fontItem ->
                                        val isSelected = fontItem.name == state.selectedFont.name
                                        SettingsChip(
                                            selected = isSelected,
                                            onClick = { onSelectFont(fontItem) }
                                        ) {
                                            Text(
                                                text = fontItem.name,
                                                color = if (isSelected) LiquidGlassText else LiquidGlassText.copy(alpha = 0.62f),
                                                fontFamily = fontItem.family,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsChip(
                            selected = true,
                            onClick = onClose,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                stringResource(id = R.string.settings_done),
                                color = LiquidGlassText,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticleWeatherSelector(
    isAuto: Boolean,
    selected: ParticleWeather,
    onToggleAuto: (Boolean) -> Unit,
    onSelectWeather: (ParticleWeather) -> Unit
) {
    SettingsCardSurface(
        shape = RoundedCornerShape(24.dp),
        padding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(id = R.string.settings_particle_weather),
                color = LiquidGlassText.copy(alpha = 0.68f),
                fontSize = 13.sp
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val isSelected = isAuto
                    SettingsChip(
                        selected = isSelected,
                        onClick = { onToggleAuto(true) }
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_particle_weather_auto),
                            color = if (isSelected) LiquidGlassText else LiquidGlassText.copy(alpha = 0.62f),
                            fontSize = 12.sp
                        )
                    }
                }
                items(ParticleWeather.entries) { weather ->
                    val isSelected = !isAuto && weather == selected
                    SettingsChip(
                        selected = isSelected,
                        onClick = { onSelectWeather(weather) }
                    ) {
                        Text(
                            text = weather.label(),
                            color = if (isSelected) LiquidGlassText else LiquidGlassText.copy(alpha = 0.62f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepSoundSelector(
    selected: SleepSoundMode,
    enabled: Boolean,
    remainingSeconds: Int,
    onEnabledChange: (Boolean) -> Unit,
    onSelect: (SleepSoundMode) -> Unit
) {
    SettingsCardSurface(
        shape = RoundedCornerShape(24.dp),
        padding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.settings_sleep_sound_type),
                    color = LiquidGlassText.copy(alpha = 0.88f),
                    fontSize = 16.sp
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFB7E2FF),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.92f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.22f),
                        uncheckedBorderColor = Color.Transparent,
                        checkedBorderColor = Color.Transparent
                    )
                )
            }
            Text(
                text = if (enabled && remainingSeconds > 0) {
                    stringResource(id = R.string.settings_sleep_sound_remaining, formatSleepSoundRemaining(remainingSeconds))
                } else {
                    stringResource(id = R.string.settings_sleep_sound_hint)
                },
                color = LiquidGlassText.copy(alpha = 0.52f),
                fontSize = 11.sp,
                fontFamily = UiFontFamily
            )
            Text(
                text = stringResource(id = R.string.settings_sleep_sound_auto_off),
                color = LiquidGlassText.copy(alpha = 0.42f),
                fontSize = 10.sp,
                fontFamily = UiFontFamily
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SleepSoundMode.entries.toList()) { mode ->
                    val isSelected = mode == selected
                    SettingsChip(
                        selected = isSelected,
                        onClick = { onSelect(mode) }
                    ) {
                        Text(
                            text = mode.label(),
                            color = if (isSelected) LiquidGlassText else LiquidGlassText.copy(alpha = 0.62f),
                            fontSize = 12.sp,
                            fontFamily = UiFontFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePresetSelector(
    selected: ThemePreset,
    active: ThemePreset,
    onSelect: (ThemePreset) -> Unit
) {
    SettingsCardSurface(
        shape = RoundedCornerShape(24.dp),
        padding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(id = R.string.settings_theme_preset),
                color = LiquidGlassText.copy(alpha = 0.68f),
                fontSize = 13.sp
            )
            Text(
                text = stringResource(id = R.string.settings_theme_active, active.label()),
                color = LiquidGlassText.copy(alpha = 0.50f),
                fontSize = 11.sp,
                fontFamily = UiFontFamily
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ThemePreset.entries.toList()) { preset ->
                    SettingsChip(selected = preset == selected, onClick = { onSelect(preset) }) {
                        Text(
                            text = preset.label(),
                            color = LiquidGlassText,
                            fontSize = 12.sp,
                            fontFamily = UiFontFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyAlarmCard(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    onEnabledChange: (Boolean) -> Unit,
    onSetHour: (Int) -> Unit,
    onSetMinute: (Int) -> Unit
) {
    SettingsCardSurface(
        shape = RoundedCornerShape(24.dp),
        padding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(id = R.string.settings_daily_alarm),
                        color = LiquidGlassText.copy(alpha = 0.88f),
                        fontSize = 16.sp
                    )
                    Text(
                        text = "%02d:%02d".format(hour, minute),
                        color = LiquidGlassText.copy(alpha = 0.50f),
                        fontSize = 11.sp,
                        fontFamily = UiFontFamily
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFB7E2FF),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.92f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.22f),
                        uncheckedBorderColor = Color.Transparent,
                        checkedBorderColor = Color.Transparent
                    )
                )
            }
            AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                    Text(
                        stringResource(id = R.string.settings_alarm_time),
                        color = LiquidGlassText.copy(alpha = 0.58f),
                        fontSize = 12.sp,
                        fontFamily = UiFontFamily
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeWheelColumn(
                            values = (0..23).toList(),
                            selected = hour,
                            labelFormatter = { "%02d".format(it) },
                            onSelected = onSetHour
                        )
                        Text(
                            ":",
                            color = LiquidGlassText.copy(alpha = 0.62f),
                            fontSize = 24.sp,
                            fontFamily = UiFontFamily,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        TimeWheelColumn(
                            values = (0..59).toList(),
                            selected = minute,
                            labelFormatter = { "%02d".format(it) },
                            onSelected = onSetMinute
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TimeWheelColumn(
    values: List<Int>,
    selected: Int,
    labelFormatter: (Int) -> String,
    onSelected: (Int) -> Unit
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val currentSelected by rememberUpdatedState(selected)
    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> kotlin.math.abs((item.offset + item.size / 2) - viewportCenter) }
                ?.index
                ?.coerceIn(0, values.lastIndex)
                ?: selectedIndex
        }
    }

    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress && centeredIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    LaunchedEffect(listState, values) {
        snapshotFlow { centeredIndex }
            .distinctUntilChanged()
            .collect { index ->
                val value = values[index]
                if (value != currentSelected) {
                    onSelected(value)
                }
            }
    }

    Box(
        modifier = Modifier
            .width(86.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .drawWithContent {
                val centerY = size.height / 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(6.dp.toPx(), centerY - 20.dp.toPx()),
                    size = Size(size.width - 12.dp.toPx(), 40.dp.toPx()),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
                drawContent()
            }
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = 51.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(values.size) { index ->
                val value = values[index]
                val distance = kotlin.math.abs(index - centeredIndex)
                Text(
                    text = labelFormatter(value),
                    color = LiquidGlassText.copy(alpha = if (distance == 0) 0.95f else 0.32f),
                    fontSize = if (distance == 0) 22.sp else 16.sp,
                    fontFamily = UiFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun ParticleWeather.label(): String {
    return when (this) {
        ParticleWeather.SUNNY -> stringResource(id = R.string.weather_sunny)
        ParticleWeather.CLOUDY -> stringResource(id = R.string.weather_cloudy)
        ParticleWeather.FOG -> stringResource(id = R.string.weather_fog)
        ParticleWeather.RAIN -> stringResource(id = R.string.weather_rain)
        ParticleWeather.SNOW -> stringResource(id = R.string.weather_snow)
        ParticleWeather.HAIL -> stringResource(id = R.string.weather_hail)
        ParticleWeather.WIND -> stringResource(id = R.string.weather_wind)
        ParticleWeather.DRIZZLE -> stringResource(id = R.string.weather_drizzle)
        ParticleWeather.BLIZZARD -> stringResource(id = R.string.weather_blizzard)
    }
}

@Composable
private fun SettingToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsCardSurface(
        shape = RoundedCornerShape(22.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = LiquidGlassText.copy(alpha = 0.88f), fontSize = 16.sp)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFB7E2FF),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.92f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.22f),
                    uncheckedBorderColor = Color.Transparent,
                    checkedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun ClockContent(
    state: ClockState,
    fontFamily: FontFamily,
    onPlayAudio: () -> Unit,
    onToggleSettings: () -> Unit,
    onSetClockMode: (ClockMode) -> Unit,
    onToggleModeRunning: () -> Unit,
    onResetMode: () -> Unit,
    onSetPomodoroFocus: (Int) -> Unit,
    onSetPomodoroBreak: (Int) -> Unit,
    onSetCountdown: (Int) -> Unit,
    mainDisplayModifier: Modifier = Modifier,
    onTimeBoundsChanged: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCompactPortrait = !isLandscape && screenWidth < 430
    val baseFontSize = ((screenWidth / 8).coerceAtLeast(32) * 1.48f) * state.selectedFont.sizeMultiplier
    val footerFontSize = (screenWidth / 20).coerceIn(16, 24).sp
    val quietLayout = state.activeThemePreset.profile().quietLayout || state.currentHour24 >= 23 || state.currentHour24 < 7

    val alpha by animateFloatAsState(
        targetValue = (if (state.isBurnInProtectionEnabled) 0.65f else 0.9f) * state.activeThemePreset.profile().dimStrength,
        animationSpec = tween(1000),
        label = "burnInAlpha"
    )

    Box(
        modifier = Modifier
            .then(modifier)
            .fillMaxSize()
            .alpha(alpha)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        if (isLandscape) {
            val displayModifier = Modifier
                .then(mainDisplayModifier)
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInRoot()
                    onTimeBoundsChanged(RectF(rect.left, rect.top, rect.right, rect.bottom))
                }

            LandscapeClockContent(
                state = state,
                fontFamily = fontFamily,
                baseFontSize = baseFontSize,
                displayModifier = displayModifier,
                onToggleSettings = onToggleSettings,
                onSetClockMode = onSetClockMode,
                onToggleModeRunning = onToggleModeRunning,
                onResetMode = onResetMode,
                onSetPomodoroFocus = onSetPomodoroFocus,
                onSetPomodoroBreak = onSetPomodoroBreak,
                onSetCountdown = onSetCountdown,
                onPlayAudio = onPlayAudio
            )
            return@Box
        }

        val displayModifier = Modifier
            .then(mainDisplayModifier)
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInRoot()
                onTimeBoundsChanged(
                    RectF(rect.left, rect.top, rect.right, rect.bottom)
                )
            }

        PortraitClockContent(
            state = state,
            fontFamily = fontFamily,
            baseFontSize = baseFontSize,
            footerFontSize = footerFontSize,
            quietLayout = quietLayout,
            isCompactPortrait = isCompactPortrait,
            screenHeight = screenHeight,
            displayModifier = displayModifier,
            onPlayAudio = onPlayAudio,
            onToggleSettings = onToggleSettings,
            onSetClockMode = onSetClockMode,
            onToggleModeRunning = onToggleModeRunning,
            onResetMode = onResetMode,
            onSetPomodoroFocus = onSetPomodoroFocus,
            onSetPomodoroBreak = onSetPomodoroBreak,
            onSetCountdown = onSetCountdown
        )
    }
}

@Composable
private fun ModeSwitcherRow(
    state: ClockState,
    onSetClockMode: (ClockMode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(ClockMode.entries.toList()) { mode ->
            SettingsChip(
                selected = state.clockMode == mode,
                onClick = { onSetClockMode(mode) }
            ) {
                Text(
                    text = mode.label(),
                    color = LiquidGlassText,
                    fontSize = 13.sp,
                    fontFamily = UiFontFamily
                )
            }
        }
    }
}

@Composable
private fun PortraitClockContent(
    state: ClockState,
    fontFamily: FontFamily,
    baseFontSize: Float,
    footerFontSize: TextUnit,
    quietLayout: Boolean,
    isCompactPortrait: Boolean,
    screenHeight: Int,
    displayModifier: Modifier,
    onPlayAudio: () -> Unit,
    onToggleSettings: () -> Unit,
    onSetClockMode: (ClockMode) -> Unit,
    onToggleModeRunning: () -> Unit,
    onResetMode: () -> Unit,
    onSetPomodoroFocus: (Int) -> Unit,
    onSetPomodoroBreak: (Int) -> Unit,
    onSetCountdown: (Int) -> Unit
) {
    var controlsCollapsed by remember(state.clockMode) { mutableStateOf(false) }
    val portraitMainSize = baseFontSize * if (isCompactPortrait) 0.98f else 1.06f
    LaunchedEffect(state.clockMode, state.timerRunning) {
        controlsCollapsed = state.clockMode != ClockMode.CLOCK && state.timerRunning
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            DateDayBlock(
                state = state,
                footerFontSize = footerFontSize,
                modifier = Modifier.weight(1f)
            )
            LiquidGlassSurface(
                shape = RoundedCornerShape(28.dp),
                padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = onToggleSettings,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.cd_settings),
                        modifier = Modifier.size(22.dp),
                        tint = LiquidGlassText.copy(alpha = 0.76f)
                    )
                }
            }
        }

        ModeSwitcherRow(
            state = state,
            onSetClockMode = onSetClockMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (quietLayout) 10.dp else 14.dp)
        )

        Spacer(modifier = Modifier.height(if (isCompactPortrait) 16.dp else 20.dp))

        MainTimeDisplay(
            state = state,
            fontFamily = fontFamily,
            baseSize = portraitMainSize,
            modifier = displayModifier,
            showSeconds = false
        )

        Spacer(modifier = Modifier.height(if (isCompactPortrait) 10.dp else 14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompactPortrait) 10.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortraitSecondsCard(
                state = state,
                minuteReferenceSize = portraitMainSize,
                modifier = Modifier.weight(0.34f)
            )
            ThemePresetPill(
                label = stringResource(id = R.string.current_preset_label, state.activeThemePreset.label()),
                modifier = Modifier.weight(0.66f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        PomodoroInfoCard(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompactPortrait) 10.dp else 16.dp)
        )

        if (state.clockMode != ClockMode.CLOCK) {
            Spacer(modifier = Modifier.height(10.dp))
            if (controlsCollapsed) {
                RunningControlOrb(
                    state = state,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onExpand = { controlsCollapsed = false }
                )
            } else {
                ModeControlPanel(
                    state = state,
                    onToggleModeRunning = onToggleModeRunning,
                    onResetMode = onResetMode,
                    onSetPomodoroFocus = onSetPomodoroFocus,
                    onSetPomodoroBreak = onSetPomodoroBreak,
                    onSetCountdown = onSetCountdown,
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isCompactPortrait) 10.dp else 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        CompanionStatusCard(
            state = state,
            quietLayout = quietLayout,
            compact = true,
            modifier = Modifier
                .fillMaxWidth(0.72f)
        )

        Spacer(modifier = Modifier.weight(if (screenHeight < 760) 0.22f else 0.38f))

        if (state.isSoundButtonVisible) {
            AudioButton(onPlayAudio, modifier = Modifier.padding(bottom = 18.dp))
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DateDayBlock(state: ClockState, footerFontSize: TextUnit, modifier: Modifier = Modifier) {
    SettingsCardSurface(
        shape = RoundedCornerShape(26.dp),
        padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.dayOfWeek,
                color = LiquidGlassText.copy(alpha = 0.92f),
                fontSize = (footerFontSize.value + 6).sp,
                fontFamily = UiFontFamily,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.date,
                color = LiquidGlassText.copy(alpha = 0.68f),
                fontSize = footerFontSize,
                fontFamily = UiFontFamily
            )
        }
    }
}

@Composable
private fun PomodoroInfoCard(state: ClockState, modifier: Modifier = Modifier) {
    val nextPomodoroLabel = stringResource(id = R.string.next_pomodoro_in, state.pomodoroFocusMinutes)
    val currentRemaining = when (state.clockMode) {
        ClockMode.POMODORO -> formatCountdownLabel(state.pomodoroRemainingSeconds)
        ClockMode.COUNTDOWN -> formatCountdownLabel(state.countdownRemainingSeconds)
        else -> formatCountdownLabel(state.pomodoroFocusMinutes * 60)
    }
    val progress = when (state.clockMode) {
        ClockMode.POMODORO -> {
            val total = if (state.pomodoroPhase == PomodoroPhase.FOCUS) {
                state.pomodoroFocusMinutes * 60
            } else {
                state.pomodoroBreakMinutes * 60
            }
            1f - (state.pomodoroRemainingSeconds / total.coerceAtLeast(1).toFloat())
        }
        ClockMode.COUNTDOWN -> 1f - (state.countdownRemainingSeconds / (state.countdownDurationMinutes * 60).coerceAtLeast(1).toFloat())
        ClockMode.STOPWATCH -> (state.stopwatchElapsedSeconds % 3600) / 3600f
        ClockMode.CLOCK -> 0.38f
    }.coerceIn(0f, 1f)

    SettingsCardSurface(
        shape = RoundedCornerShape(28.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemePresetPill(label = nextPomodoroLabel)
            Text(
                text = stringResource(id = R.string.current_remaining, currentRemaining),
                color = LiquidGlassText.copy(alpha = 0.9f),
                fontSize = 16.sp,
                fontFamily = UiFontFamily
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFD6EAFF).copy(alpha = 0.88f),
                                    Color(0xFF8CB9E8).copy(alpha = 0.72f)
                                )
                            )
                        )
                )
            }
            Text(
                text = state.modeHeadline(),
                color = LiquidGlassText.copy(alpha = 0.62f),
                fontSize = 13.sp,
                fontFamily = UiFontFamily
            )
        }
    }
}

@Composable
private fun PortraitSecondsCard(
    state: ClockState,
    minuteReferenceSize: Float,
    modifier: Modifier = Modifier
) {
    val display = remember(state) { state.timerDisplay() }
    SettingsCardSurface(
        shape = RoundedCornerShape(22.dp),
        padding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(id = R.string.seconds_label),
                color = LiquidGlassText.copy(alpha = 0.64f),
                fontSize = 11.sp,
                fontFamily = UiFontFamily
            )
            FlipDigit(
                value = display.second,
                font = state.selectedFont,
                fontSize = (minuteReferenceSize * 0.8f) * state.selectedFont.secondsScale,
                compact = true
            )
        }
    }
}

@Composable
private fun LandscapeClockContent(
    state: ClockState,
    fontFamily: FontFamily,
    baseFontSize: Float,
    displayModifier: Modifier,
    onToggleSettings: () -> Unit,
    onSetClockMode: (ClockMode) -> Unit,
    onToggleModeRunning: () -> Unit,
    onResetMode: () -> Unit,
    onSetPomodoroFocus: (Int) -> Unit,
    onSetPomodoroBreak: (Int) -> Unit,
    onSetCountdown: (Int) -> Unit,
    onPlayAudio: () -> Unit
) {
    var transientModeLabel by remember { mutableStateOf<String?>(null) }
    var controlsCollapsed by remember(state.clockMode) { mutableStateOf(false) }
    LaunchedEffect(transientModeLabel) {
        if (transientModeLabel != null) {
            kotlinx.coroutines.delay(3000)
            transientModeLabel = null
        }
    }
    LaunchedEffect(state.clockMode, state.timerRunning) {
        controlsCollapsed = state.clockMode != ClockMode.CLOCK && state.timerRunning
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.clockMode) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragCancel = { totalDrag = 0f },
                    onDragEnd = {
                        val modes = ClockMode.entries
                        val currentIndex = modes.indexOf(state.clockMode)
                        val nextMode = when {
                            totalDrag <= -72f && currentIndex < modes.lastIndex -> modes[currentIndex + 1]
                            totalDrag >= 72f && currentIndex > 0 -> modes[currentIndex - 1]
                            else -> null
                        }
                        if (nextMode != null) {
                            onSetClockMode(nextMode)
                            transientModeLabel = nextMode.name.lowercase().replaceFirstChar { it.titlecase() }
                        }
                        totalDrag = 0f
                    }
                )
            }
    ) {
        LiquidGlassSurface(
            shape = RoundedCornerShape(28.dp),
            padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 2.dp)
        ) {
            IconButton(
                onClick = onToggleSettings,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(id = R.string.cd_settings),
                    modifier = Modifier.size(22.dp),
                    tint = LiquidGlassText.copy(alpha = 0.76f)
                )
            }
        }

        MainTimeDisplay(
            state = state,
            fontFamily = fontFamily,
            baseSize = baseFontSize * 1.30f,
            modifier = displayModifier
                .align(Alignment.Center)
                .padding(start = 34.dp, end = 34.dp, bottom = 22.dp)
        )

        LandscapeModeFooter(
            modes = ClockMode.entries.toList(),
            currentMode = state.clockMode,
            label = transientModeLabel ?: stringResource(id = R.string.current_preset_compact, state.activeThemePreset.label()),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
        )

        if (state.clockMode != ClockMode.CLOCK) {
            if (controlsCollapsed) {
                RunningControlOrb(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 18.dp),
                    onExpand = { controlsCollapsed = false }
                )
            } else {
                ModeControlPanel(
                    state = state,
                    onToggleModeRunning = onToggleModeRunning,
                    onResetMode = onResetMode,
                    onSetPomodoroFocus = onSetPomodoroFocus,
                    onSetPomodoroBreak = onSetPomodoroBreak,
                    onSetCountdown = onSetCountdown,
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 18.dp)
                        .widthIn(max = 276.dp)
                )
            }
        }

        if (state.isSoundButtonVisible) {
            AudioButton(
                onPlayAudio,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun LandscapeModeFooter(
    modes: List<ClockMode>,
    currentMode: ClockMode,
    label: String,
    modifier: Modifier = Modifier
) {
    SettingsCardSurface(
        shape = RoundedCornerShape(22.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                color = LiquidGlassText.copy(alpha = 0.84f),
                fontFamily = UiFontFamily,
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                modes.forEach { mode ->
                    val active = mode == currentMode
                    Box(
                        modifier = Modifier
                            .size(if (active) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) Color.White.copy(alpha = 0.88f)
                                else Color.White.copy(alpha = 0.26f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSidebarRail(
    state: ClockState,
    onSetClockMode: (ClockMode) -> Unit,
    onToggleModeRunning: () -> Unit,
    onResetMode: () -> Unit,
    onSetPomodoroFocus: (Int) -> Unit,
    onSetPomodoroBreak: (Int) -> Unit,
    onSetCountdown: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(124.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        ClockMode.entries.forEach { mode ->
            SettingsChip(
                selected = state.clockMode == mode,
                onClick = { onSetClockMode(mode) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = mode.label(),
                    color = LiquidGlassText.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontFamily = UiFontFamily
                )
            }
        }
    }
}

@Composable
private fun ThemePresetPill(label: String, modifier: Modifier = Modifier) {
    SettingsCardSurface(
        shape = RoundedCornerShape(18.dp),
        padding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = LiquidGlassText.copy(alpha = 0.82f),
            fontFamily = UiFontFamily,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun DailyAlarmHint(state: ClockState, modifier: Modifier = Modifier) {
    SettingsCardSurface(
        shape = RoundedCornerShape(20.dp),
        padding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(id = R.string.alarm_enabled_hint),
                color = LiquidGlassText.copy(alpha = 0.62f),
                fontSize = 10.sp,
                fontFamily = UiFontFamily
            )
            Text(
                text = "%02d:%02d".format(state.dailyAlarmHour, state.dailyAlarmMinute),
                color = LiquidGlassText.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontFamily = UiFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DailyAlarmDialog(
    state: ClockState,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06111D).copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        SettingsCardSurface(
            shape = RoundedCornerShape(32.dp),
            padding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 420.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.alarm_dialog_title),
                    color = LiquidGlassText,
                    fontSize = 24.sp,
                    fontFamily = UiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "%02d:%02d".format(state.dailyAlarmHour, state.dailyAlarmMinute),
                    color = LiquidGlassText.copy(alpha = 0.90f),
                    fontSize = 34.sp,
                    fontFamily = UiFontFamily,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(id = R.string.alarm_dialog_body),
                    color = LiquidGlassText.copy(alpha = 0.60f),
                    fontSize = 13.sp,
                    fontFamily = UiFontFamily,
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsChip(selected = false, onClick = onSnooze) {
                        Text(
                            text = stringResource(id = R.string.alarm_snooze_10),
                            color = LiquidGlassText,
                            fontFamily = UiFontFamily,
                            fontSize = 13.sp
                        )
                    }
                    SettingsChip(selected = true, onClick = onDismiss) {
                        Text(
                            text = stringResource(id = R.string.alarm_dismiss),
                            color = LiquidGlassText,
                            fontFamily = UiFontFamily,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningControlOrb(
    state: ClockState,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "controlPulse").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "controlPulseValue"
    )
    val halo = rememberInfiniteTransition(label = "controlHalo").animateFloat(
        initialValue = 0.72f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "controlHaloValue"
    )
    val label = when (state.clockMode) {
        ClockMode.POMODORO -> if (state.pomodoroPhase == PomodoroPhase.FOCUS) "Focus" else "Break"
        ClockMode.COUNTDOWN -> "Timer"
        ClockMode.STOPWATCH -> "Run"
        ClockMode.CLOCK -> ""
    }
    val progress = state.modeProgressFraction()

    Box(
        modifier = modifier
            .size(84.dp)
            .graphicsLayer(
                scaleX = pulse.value,
                scaleY = pulse.value
            )
            .clickable(onClick = onExpand),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 5.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f - stroke
            val outerRadius = baseRadius * halo.value
            val innerRingRadius = baseRadius * 0.74f

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFFBFE3FF).copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRadius * 1.1f
                ),
                radius = outerRadius * 1.1f,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFA8D6FF).copy(alpha = 0.14f),
                radius = outerRadius * 0.88f,
                center = center,
                style = Stroke(width = 2.2.dp.toPx())
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color(0xFF1C2A39).copy(alpha = 0.86f)
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = stroke)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = innerRingRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - innerRingRadius, center.y - innerRingRadius),
                size = Size(innerRingRadius * 2f, innerRingRadius * 2f),
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFFDDF0FF),
                        Color(0xFFA5D1FF),
                        Color(0xFFDDF0FF)
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0.06f, 1f),
                useCenter = false,
                topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                size = Size(baseRadius * 2f, baseRadius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val dotAngle = Math.toRadians((360f * progress - 90f).toDouble())
            val dotCenter = Offset(
                x = center.x + kotlin.math.cos(dotAngle).toFloat() * baseRadius,
                y = center.y + kotlin.math.sin(dotAngle).toFloat() * baseRadius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = 4.dp.toPx(),
                center = dotCenter
            )
            drawCircle(
                color = Color(0xFFB9E1FF).copy(alpha = 0.46f),
                radius = 8.dp.toPx(),
                center = dotCenter
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFFEAF7FF).copy(alpha = 0.95f),
                        Color(0xFFA7D8FF).copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.28f
                ),
                radius = baseRadius * 0.28f,
                center = center
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = LiquidGlassText.copy(alpha = 0.82f),
                fontFamily = UiFontFamily,
                fontSize = 11.sp
            )
            Text(
                text = when (state.clockMode) {
                    ClockMode.POMODORO -> formatCountdownLabel(state.pomodoroRemainingSeconds)
                    ClockMode.COUNTDOWN -> formatCountdownLabel(state.countdownRemainingSeconds)
                    ClockMode.STOPWATCH -> formatCountdownLabel(state.stopwatchElapsedSeconds)
                    ClockMode.CLOCK -> ""
                },
                color = LiquidGlassText,
                fontFamily = UiFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ModeControlPanel(
    state: ClockState,
    onToggleModeRunning: () -> Unit,
    onResetMode: () -> Unit,
    onSetPomodoroFocus: (Int) -> Unit,
    onSetPomodoroBreak: (Int) -> Unit,
    onSetCountdown: (Int) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    SettingsCardSurface(
        shape = RoundedCornerShape(24.dp),
        padding = PaddingValues(
            horizontal = if (compact) 14.dp else 16.dp,
            vertical = if (compact) 12.dp else 14.dp
        ),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Text(
                text = state.modeHeadline(),
                color = LiquidGlassText.copy(alpha = 0.92f),
                fontFamily = UiFontFamily,
                fontSize = if (compact) 14.sp else 15.sp
            )
            if (state.clockMode != ClockMode.CLOCK) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SettingsChip(selected = state.timerRunning, onClick = onToggleModeRunning) {
                        Text(
                            text = stringResource(id = if (state.timerRunning) R.string.mode_pause else R.string.mode_start),
                            color = LiquidGlassText,
                            fontFamily = UiFontFamily,
                            fontSize = if (compact) 12.sp else 13.sp
                        )
                    }
                    SettingsChip(selected = false, onClick = onResetMode) {
                        Text(
                            text = stringResource(id = R.string.mode_reset),
                            color = LiquidGlassText,
                            fontFamily = UiFontFamily,
                            fontSize = if (compact) 12.sp else 13.sp
                        )
                    }
                }
            }
            when (state.clockMode) {
                ClockMode.POMODORO -> {
                    TimerWheelSettingRow(
                        label = stringResource(id = R.string.mode_focus_duration),
                        value = state.pomodoroFocusMinutes,
                        values = (5..90).toList(),
                        onSelected = onSetPomodoroFocus
                    )
                    TimerWheelSettingRow(
                        label = stringResource(id = R.string.mode_break_duration),
                        value = state.pomodoroBreakMinutes,
                        values = (1..30).toList(),
                        onSelected = onSetPomodoroBreak
                    )
                }
                ClockMode.COUNTDOWN -> {
                    TimerWheelSettingRow(
                        label = stringResource(id = R.string.mode_countdown_duration),
                        value = state.countdownDurationMinutes,
                        values = (1..180).toList(),
                        onSelected = onSetCountdown
                    )
                }
                ClockMode.STOPWATCH -> {
                    Text(
                        text = stringResource(id = R.string.mode_stopwatch_stats, formatDurationWords(state.stopwatchElapsedSeconds)),
                        color = LiquidGlassText.copy(alpha = 0.72f),
                        fontFamily = UiFontFamily,
                        fontSize = if (compact) 11.sp else 12.sp
                    )
                }
                ClockMode.CLOCK -> {
                    Text(
                        text = stringResource(id = R.string.mode_clock_hint),
                        color = LiquidGlassText.copy(alpha = 0.72f),
                        fontFamily = UiFontFamily,
                        fontSize = if (compact) 11.sp else 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerWheelSettingRow(
    label: String,
    value: Int,
    values: List<Int>,
    onSelected: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = LiquidGlassText.copy(alpha = 0.66f),
            fontFamily = UiFontFamily,
            fontSize = 11.sp
        )
        Row(
            modifier = Modifier.height(128.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeWheelColumn(
                values = values,
                selected = value,
                labelFormatter = { it.toString().padStart(2, '0') },
                onSelected = onSelected
            )
        }
    }
}

@Composable
private fun CompanionStatusCard(
    state: ClockState,
    quietLayout: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    SettingsCardSurface(
        shape = RoundedCornerShape(22.dp),
        padding = PaddingValues(
            horizontal = if (compact) 14.dp else 16.dp,
            vertical = if (compact) 10.dp else 12.dp
        ),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
            Text(
                text = state.companionMessage.ifBlank { state.defaultCompanionMessage() },
                color = LiquidGlassText.copy(alpha = 0.9f),
                fontFamily = UiFontFamily,
                fontSize = if (compact) 12.sp else 13.sp
            )
            if (!quietLayout) {
                Text(
                    text = stringResource(
                        id = R.string.focus_stats,
                        formatDurationWords(state.focusedSecondsToday),
                        state.completedPomodoros,
                        state.completedBreaks
                    ),
                    color = LiquidGlassText.copy(alpha = 0.62f),
                    fontFamily = UiFontFamily,
                    fontSize = if (compact) 10.sp else 11.sp
                )
            }
        }
    }
}

@Composable
private fun MainTimeDisplay(
    state: ClockState,
    fontFamily: FontFamily,
    baseSize: Float,
    modifier: Modifier,
    showSeconds: Boolean = true
) {
    val display = remember(state) { state.timerDisplay() }
    BoxWithConstraints(modifier = modifier) {
        val availableWidth = maxWidth.value
        val widthFactor = (if (showSeconds) 4.95f else 4.18f) * state.selectedFont.widthFitMultiplier
        val fittedBaseSize = ((availableWidth - 12f) / widthFactor).coerceAtLeast(34f)
        val resolvedBaseSize = minOf(baseSize, fittedBaseSize)
        val secondsScale = state.selectedFont.secondsScale

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FlipDigit(display.hour, state.selectedFont, resolvedBaseSize)
            Text(":", color = LiquidGlassText.copy(alpha = 0.84f), fontSize = (resolvedBaseSize * 0.78f).sp, fontWeight = FontWeight.Light)
            FlipDigit(display.minute, state.selectedFont, resolvedBaseSize)
            if (showSeconds) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FlipDigit(
                        display.second,
                        state.selectedFont,
                        (resolvedBaseSize * 0.28f) * secondsScale,
                        compact = true
                    )
                }
            }
        }
    }
}

@Composable
fun FlipDigit(value: String, font: ClockFont, fontSize: Float, compact: Boolean = false) {
    var outgoingValue by remember { mutableStateOf(value) }
    var currentValue by remember { mutableStateOf(value) }
    val progress = remember { Animatable(1f) }
    val density = LocalDensity.current
    val slotWidth = with(density) { (fontSize * if (compact) 1.84f else 1.86f).sp.toDp() }
    val slotHeight = with(density) { (fontSize * if (compact) 1.08f else 1.12f).sp.toDp() }
    val gap = if (compact) 1.dp else 2.dp
    val totalHeight = slotHeight + gap
    val flipDuration = if (compact) 720 else 860
    val topEnd = if (compact) 220 else 250
    val holdStart = if (compact) 280 else 330
    val holdEnd = if (compact) 330 else 390

    LaunchedEffect(value) {
        if (value != currentValue) {
            outgoingValue = currentValue
            currentValue = value
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                keyframes {
                    durationMillis = flipDuration
                    0f at 0
                    0.46f at topEnd
                    0.50f at holdStart
                    0.54f at holdEnd
                    1f at flipDuration
                }
            )
            outgoingValue = currentValue
        }
    }

    val topRotation = if (progress.value < 0.5f) -180f * progress.value else -90f
    val bottomRotation = if (progress.value > 0.5f) 90f - ((progress.value - 0.5f) * 180f) else 90f
    // Strict sequential: top stays with outgoing during top flip, then switches to current
    val staticTopValue = if (progress.value < 0.5f) outgoingValue else currentValue
    // Bottom stays with outgoing until bottom flip starts
    val staticBottomValue = outgoingValue
    val topPhase = (progress.value / 0.5f).coerceIn(0f, 1f)
    val bottomPhase = ((progress.value - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val hingePulse = (1f - (kotlin.math.abs(progress.value - 0.5f) / 0.5f)).coerceIn(0f, 1f)
    val topFlipDepth = sin(topPhase * Math.PI.toFloat()).coerceIn(0f, 1f)
    val bottomFlipDepth = sin(bottomPhase * Math.PI.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .width(slotWidth)
            .height(totalHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            FlipDigitHalf(
                value = staticTopValue,
                fontFamily = font.family,
                fontSize = fontSize,
                slotHeight = slotHeight,
                isTop = true,
                modifier = Modifier.fillMaxWidth(),
                hingeShadowStrength = 0.08f + hingePulse * 0.10f,
                compact = compact,
                verticalBias = font.verticalBias
            )
            FlipDigitHalf(
                value = staticBottomValue,
                fontFamily = font.family,
                fontSize = fontSize,
                slotHeight = slotHeight,
                isTop = false,
                modifier = Modifier.fillMaxWidth(),
                hingeShadowStrength = 0.10f + hingePulse * 0.12f,
                compact = compact,
                verticalBias = font.verticalBias
            )
        }
        if (progress.value < 0.5f) {
            FlipDigitHalf(
                value = outgoingValue,
                fontFamily = font.family,
                fontSize = fontSize,
                slotHeight = slotHeight,
                isTop = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationX = topRotation
                        cameraDistance = 22f * density.density
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = topFlipDepth * if (compact) 1.5f else 2.5f
                        scaleY = 1f - topFlipDepth * 0.018f
                    },
                elevated = true,
                edgeShadowStrength = 0.12f + topPhase * 0.26f,
                hingeShadowStrength = 0.18f + topPhase * 0.28f,
                sheenStrength = 0.14f - topPhase * 0.08f,
                backFaceShade = 0.08f + topFlipDepth * 0.20f,
                compact = compact,
                verticalBias = font.verticalBias
            )
        }
        if (progress.value > 0.5f) {
            FlipDigitHalf(
                value = currentValue,
                fontFamily = font.family,
                fontSize = fontSize,
                slotHeight = slotHeight,
                isTop = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationX = bottomRotation
                        cameraDistance = 22f * density.density
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = -bottomFlipDepth * if (compact) 1.2f else 2.0f
                        scaleY = 1f - bottomFlipDepth * 0.015f
                    },
                elevated = true,
                edgeShadowStrength = 0.30f - bottomPhase * 0.18f,
                hingeShadowStrength = 0.26f - bottomPhase * 0.14f,
                sheenStrength = 0.04f + bottomPhase * 0.10f,
                backFaceShade = 0.18f + bottomFlipDepth * 0.16f,
                compact = compact,
                verticalBias = font.verticalBias
            )
        }
        HingeOverlay(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(if (compact) 6.dp else 10.dp),
            strength = (if (compact) 0.18f else 0.24f) + hingePulse * (if (compact) 0.22f else 0.34f),
            compact = compact
        )
    }
}

@Composable
private fun FlipDigitHalf(
    value: String,
    fontFamily: FontFamily,
    fontSize: Float,
    slotHeight: androidx.compose.ui.unit.Dp,
    isTop: Boolean,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    edgeShadowStrength: Float = 0.08f,
    hingeShadowStrength: Float = 0.10f,
    sheenStrength: Float = 0.14f,
    backFaceShade: Float = 0f,
    compact: Boolean = false,
    verticalBias: Float = 0.1f
) {
    val halfHeight = slotHeight / 2
    val shape = if (isTop) {
        RoundedCornerShape(
            topStart = if (compact) 18.dp else 28.dp,
            topEnd = if (compact) 18.dp else 28.dp,
            bottomStart = if (compact) 8.dp else 12.dp,
            bottomEnd = if (compact) 8.dp else 12.dp
        )
    } else {
        RoundedCornerShape(
            topStart = if (compact) 8.dp else 12.dp,
            topEnd = if (compact) 8.dp else 12.dp,
            bottomStart = if (compact) 18.dp else 28.dp,
            bottomEnd = if (compact) 18.dp else 28.dp
        )
    }
    val baseTint = if (isTop) Color.White.copy(alpha = 0.18f) else LiquidGlassCool.copy(alpha = 0.11f)
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = fontFamily,
        color = LiquidGlassText.copy(alpha = 0.92f),
        textAlign = TextAlign.Center
    )

    Box(
        modifier = modifier
            .height(halfHeight)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isTop) 0.17f else 0.11f),
                        LiquidGlassTint.copy(alpha = 0.11f),
                        LiquidGlassShadow.copy(alpha = if (elevated) 0.24f else 0.17f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.16f)
                    )
                ),
                shape
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            baseTint.copy(alpha = baseTint.alpha + sheenStrength * 0.3f),
                            Color.Transparent,
                            Color.Black.copy(alpha = (if (isTop) 0.05f else 0.12f) + edgeShadowStrength)
                        )
                    )
                )
                if (backFaceShade > 0f) {
                    drawRect(Color.Black.copy(alpha = backFaceShade))
                }
                if (isTop) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = hingeShadowStrength),
                                Color.Black.copy(alpha = hingeShadowStrength * 1.12f)
                            ),
                            startY = size.height * 0.62f,
                            endY = size.height
                        )
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.12f + edgeShadowStrength * 0.35f),
                        topLeft = Offset(0f, size.height - if (compact) 1.dp.toPx() else 1.5.dp.toPx()),
                        size = Size(size.width, if (compact) 1.dp.toPx() else 1.5.dp.toPx())
                    )
                } else {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = hingeShadowStrength * 1.18f),
                                Color.Black.copy(alpha = hingeShadowStrength * 0.72f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.42f
                        )
                    )
                    drawRect(
                        color = Color.White.copy(alpha = 0.10f + sheenStrength * 0.24f),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, if (compact) 1.dp.toPx() else 1.5.dp.toPx())
                    )
                }
            }
            .clipToBounds()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(halfHeight)
        ) {
            val textLayout = textMeasurer.measure(
                text = AnnotatedString(value),
                style = textStyle,
                maxLines = 1
            )
            val fullHeightPx = slotHeight.toPx()
            val halfHeightPx = size.height
            val drawX = (size.width - textLayout.size.width) / 2f
            // Optical center sits slightly below the geometric center for these display fonts.
            val centeredY = (fullHeightPx - textLayout.size.height) / 2f + fontSize * verticalBias
            val drawY = if (isTop) centeredY else centeredY - halfHeightPx
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(drawX, drawY)
            )
        }
    }
}

@Composable
private fun HingeOverlay(
    modifier: Modifier = Modifier,
    strength: Float,
    compact: Boolean = false
) {
    Canvas(modifier = modifier) {
        val y = size.height / 2f
        drawLine(
            color = Color.Black.copy(alpha = strength * 0.72f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height * if (compact) 0.18f else 0.24f
        )
        drawLine(
            color = Color.White.copy(alpha = strength * 0.24f),
            start = Offset(0f, y - size.height * 0.18f),
            end = Offset(size.width, y - size.height * 0.18f),
            strokeWidth = size.height * if (compact) 0.08f else 0.12f
        )
        drawLine(
            color = LiquidGlassShadow.copy(alpha = strength * 0.40f),
            start = Offset(0f, y + size.height * 0.18f),
            end = Offset(size.width, y + size.height * 0.18f),
            strokeWidth = size.height * if (compact) 0.12f else 0.16f
        )
    }
}

@Composable
private fun BatteryStatus(levelStr: String) {
    val level = levelStr.trimEnd('%').toIntOrNull() ?: 0
    val batteryColor = when {
        level <= 20 -> Color(0xFFFF4D4D)
        level <= 50 -> Color(0xFFFFD666)
        else -> LiquidGlassText.copy(alpha = 0.95f)
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(end = 4.dp)) {
        Canvas(modifier = Modifier.size(width = 38.dp, height = 18.dp)) {
            val width = size.width; val height = size.height
            val strokeWidth = 1.5.dp.toPx(); val cornerRadius = 3.dp.toPx()
            drawRoundRect(color = Color.White.copy(alpha = 0.3f), size = Size(width * 0.9f, height), cornerRadius = CornerRadius(cornerRadius), style = Stroke(width = strokeWidth))
            drawRoundRect(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(width * 0.9f + 1.dp.toPx(), height * 0.3f), size = Size(width * 0.08f, height * 0.4f), cornerRadius = CornerRadius(1.dp.toPx()), style = Fill)
            val padding = 2.5.dp.toPx()
            val fillWidth = (width * 0.9f - padding * 2) * (level / 100f)
            if (fillWidth > 0) drawRoundRect(color = batteryColor, topLeft = Offset(padding, padding), size = Size(fillWidth, height - padding * 2), cornerRadius = CornerRadius(1.5.dp.toPx()), style = Fill)
        }
        Text(text = "$level", color = if (level > 50) Color.Black.copy(alpha = 0.68f) else LiquidGlassText, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 3.dp))
    }
}

private data class TimerDisplay(val hour: String, val minute: String, val second: String, val amPm: String = "")

@Composable
private fun ClockMode.label(): String {
    return when (this) {
        ClockMode.CLOCK -> stringResource(id = R.string.mode_clock)
        ClockMode.POMODORO -> stringResource(id = R.string.mode_pomodoro)
        ClockMode.COUNTDOWN -> stringResource(id = R.string.mode_countdown)
        ClockMode.STOPWATCH -> stringResource(id = R.string.mode_stopwatch)
    }
}

@Composable
private fun SleepSoundMode.label(): String {
    return when (this) {
        SleepSoundMode.RAIN -> stringResource(id = R.string.sleep_sound_rain)
        SleepSoundMode.WHITE_NOISE -> stringResource(id = R.string.sleep_sound_white_noise)
    }
}

private fun formatSleepSoundRemaining(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun ThemePreset.label(): String {
    return when (this) {
        ThemePreset.AUTO -> stringResource(id = R.string.theme_auto)
        ThemePreset.FOCUS -> stringResource(id = R.string.theme_focus)
        ThemePreset.PLAYFUL -> stringResource(id = R.string.theme_playful)
        ThemePreset.SERENE -> stringResource(id = R.string.theme_serene)
        ThemePreset.NIGHT -> stringResource(id = R.string.theme_night)
    }
}

private fun ClockState.timerDisplay(): TimerDisplay {
    return when (clockMode) {
        ClockMode.CLOCK -> TimerDisplay(hour, minute, second, amPm)
        ClockMode.POMODORO -> secondsToDisplay(pomodoroRemainingSeconds)
        ClockMode.COUNTDOWN -> secondsToDisplay(countdownRemainingSeconds)
        ClockMode.STOPWATCH -> secondsToDisplay(stopwatchElapsedSeconds)
    }
}

@Composable
private fun ClockState.modeHeadline(): String {
    return when (clockMode) {
        ClockMode.CLOCK -> activeThemePreset.label()
        ClockMode.POMODORO -> if (pomodoroPhase == PomodoroPhase.FOCUS) "Focus session" else "Break session"
        ClockMode.COUNTDOWN -> "Countdown target ${countdownDurationMinutes} min"
        ClockMode.STOPWATCH -> "Track live focus time"
    }
}

private fun ClockState.defaultCompanionMessage(): String {
    return when (clockMode) {
        ClockMode.CLOCK -> "Cat is keeping the time calm."
        ClockMode.POMODORO -> if (pomodoroPhase == PomodoroPhase.FOCUS) "Cat is watching your focus sprint." else "Cat says stretch a little."
        ClockMode.COUNTDOWN -> "Countdown is running with cat supervision."
        ClockMode.STOPWATCH -> "Cat is tracking your streak."
    }
}

private fun ClockState.modeProgressFraction(): Float {
    return when (clockMode) {
        ClockMode.POMODORO -> {
            val total = if (pomodoroPhase == PomodoroPhase.FOCUS) pomodoroFocusMinutes * 60 else pomodoroBreakMinutes * 60
            1f - (pomodoroRemainingSeconds / total.coerceAtLeast(1).toFloat())
        }
        ClockMode.COUNTDOWN -> 1f - (countdownRemainingSeconds / (countdownDurationMinutes * 60).coerceAtLeast(1).toFloat())
        ClockMode.STOPWATCH -> ((stopwatchElapsedSeconds % 3600) / 3600f)
        ClockMode.CLOCK -> 0f
    }.coerceIn(0f, 1f)
}

@Composable
private fun ParticleWeather.portraitSummary(): String {
    return when (this) {
        ParticleWeather.SNOW -> stringResource(id = R.string.weather_heavy_snow)
        ParticleWeather.BLIZZARD -> stringResource(id = R.string.weather_blizzard)
        ParticleWeather.RAIN -> stringResource(id = R.string.weather_night_rain)
        ParticleWeather.DRIZZLE -> stringResource(id = R.string.weather_soft_drizzle)
        ParticleWeather.CLOUDY -> stringResource(id = R.string.weather_multi_cloudy)
        ParticleWeather.FOG -> stringResource(id = R.string.weather_dense_fog)
        ParticleWeather.HAIL -> stringResource(id = R.string.weather_hail)
        ParticleWeather.WIND -> stringResource(id = R.string.weather_wind)
        ParticleWeather.SUNNY -> stringResource(id = R.string.weather_clear_night)
    }
}

private fun ParticleWeather.portraitTemperature(): String {
    val temp = when (this) {
        ParticleWeather.SUNNY -> 1
        ParticleWeather.CLOUDY -> -1
        ParticleWeather.FOG -> -3
        ParticleWeather.RAIN -> 2
        ParticleWeather.SNOW -> -2
        ParticleWeather.BLIZZARD -> -8
        ParticleWeather.HAIL -> -4
        ParticleWeather.WIND -> -5
        ParticleWeather.DRIZZLE -> 0
    }
    return "${temp}°C"
}

private fun formatCountdownLabel(totalSeconds: Int): String {
    val minutes = (totalSeconds / 60).coerceAtLeast(0)
    val seconds = (totalSeconds % 60).coerceAtLeast(0)
    return "%02d:%02d".format(minutes, seconds)
}

private fun secondsToDisplay(totalSeconds: Int): TimerDisplay {
    val hours = (totalSeconds / 3600).coerceAtLeast(0)
    val minutes = ((totalSeconds % 3600) / 60).coerceAtLeast(0)
    val seconds = (totalSeconds % 60).coerceAtLeast(0)
    return TimerDisplay(
        hour = hours.toString().padStart(2, '0'),
        minute = minutes.toString().padStart(2, '0'),
        second = seconds.toString().padStart(2, '0')
    )
}

private fun formatDurationWords(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

@Composable
private fun AudioButton(onClick: () -> Unit, modifier: Modifier) {
    LiquidGlassSurface(
        modifier = modifier
            .size(58.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        padding = PaddingValues(0.dp),
        highlightAlpha = 0.22f
    ) {
        Image(
            painter = painterResource(id = R.drawable.cat_icon),
            contentDescription = stringResource(id = R.string.cd_play_sound),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(11.dp))
        )
    }
}

private fun Offset.sanitize(): Offset = Offset(
    x = x.takeIf { it.isFinite() } ?: 0f,
    y = y.takeIf { it.isFinite() } ?: 0f
)
