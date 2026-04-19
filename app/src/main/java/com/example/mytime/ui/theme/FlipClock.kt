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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
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
import com.example.mytime.ui.ParticleWeather
import com.example.mytime.ui.PomodoroPhase
import com.example.mytime.ui.SleepSoundMode
import com.example.mytime.ui.ThemePreset
import com.example.mytime.ui.UiFontFamily
import com.example.mytime.ui.profile
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
fun FlipClockScreen(
    state: ClockState,
    onPlayAudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSelectFont: (ClockFont) -> Unit,
    onToggleParallax: (Boolean) -> Unit,
    onToggleParticleWeatherAuto: (Boolean) -> Unit,
    onSelectParticleWeather: (ParticleWeather) -> Unit,
    onToggleCats: (Boolean) -> Unit,
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
    onSetThemePreset: (ThemePreset) -> Unit,
    onSelectSleepSound: (SleepSoundMode) -> Unit,
    onToggleWhiteNoise: (Boolean) -> Unit
) {
    var timeRect by remember { mutableStateOf(RectF()) }
    var overlayInfo by remember { mutableStateOf<OverlayInfo?>(null) }
    var shouldScrollToAlarmSettings by remember { mutableStateOf(false) }
    val settingsDim = if (state.isSettingsVisible) 0.34f else 1f
    val safeParallax = state.parallaxOffset.sanitize()
    val themePresetTitle = stringResource(id = R.string.settings_theme_preset)
    val autoPresetInfo = stringResource(id = R.string.theme_info_auto)
    val focusPresetInfo = stringResource(id = R.string.theme_info_focus)
    val playfulPresetInfo = stringResource(id = R.string.theme_info_playful)
    val serenePresetInfo = stringResource(id = R.string.theme_info_serene)
    val nightPresetInfo = stringResource(id = R.string.theme_info_night)

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

        state.backgroundRes?.let { resId ->
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = backgroundModifier,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.5f), BlendMode.Multiply)
            )
        }

        Box(modifier = Modifier.alpha(if (state.isSettingsVisible) 0.22f else 1f)) {
            SeamlessParticleLayer(weather = state.particleWeather)
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
                onClick = {
                    shouldScrollToAlarmSettings = true
                    onOpenSettings()
                },
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
            shouldScrollToAlarmSettings = shouldScrollToAlarmSettings,
            onAlarmSettingsScrollHandled = { shouldScrollToAlarmSettings = false },
            onClose = onCloseSettings,
            onSelectFont = onSelectFont,
            onToggleParallax = onToggleParallax,
            onToggleParticleWeatherAuto = onToggleParticleWeatherAuto,
            onSelectParticleWeather = onSelectParticleWeather,
            onToggleCats = onToggleCats,
            onToggle24HourFormat = onToggle24HourFormat,
            onToggleHourlyChime = onToggleHourlyChime,
            onToggleDailyAlarm = onToggleDailyAlarm,
            onSetDailyAlarmHour = onSetDailyAlarmHour,
            onSetDailyAlarmMinute = onSetDailyAlarmMinute,
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

private val LiquidGlassTint = Color(0xFFDCEBFF)
private val LiquidGlassCool = Color(0xFFA8CDFF)
private val LiquidGlassShadow = Color(0xFF060E18)
private val LiquidGlassText = Color(0xFFF4F9FF)

private data class OverlayInfo(
    val title: String,
    val body: String
)

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
    shape: Shape = RoundedCornerShape(16.dp),
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                brush = if (selected) {
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF2B3A4B).copy(alpha = 0.96f),
                            Color(0xFF1C2A39).copy(alpha = 0.98f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1C2734).copy(alpha = 0.92f),
                            Color(0xFF141D29).copy(alpha = 0.95f)
                        )
                    )
                },
                shape = shape
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (selected) 0.24f else 0.16f),
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
                            Color.White.copy(alpha = if (selected) 0.08f else 0.04f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.06f)
                        )
                    )
                )
            }
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SettingsMenu(
    visible: Boolean,
    state: ClockState,
    shouldScrollToAlarmSettings: Boolean,
    onAlarmSettingsScrollHandled: () -> Unit,
    onClose: () -> Unit,
    onSelectFont: (ClockFont) -> Unit,
    onToggleParallax: (Boolean) -> Unit,
    onToggleParticleWeatherAuto: (Boolean) -> Unit,
    onSelectParticleWeather: (ParticleWeather) -> Unit,
    onToggleCats: (Boolean) -> Unit,
    onToggle24HourFormat: (Boolean) -> Unit,
    onToggleHourlyChime: (Boolean) -> Unit,
    onToggleDailyAlarm: (Boolean) -> Unit,
    onSetDailyAlarmHour: (Int) -> Unit,
    onSetDailyAlarmMinute: (Int) -> Unit,
    onSetThemePreset: (ThemePreset) -> Unit,
    onSelectSleepSound: (SleepSoundMode) -> Unit,
    onToggleWhiteNoise: (Boolean) -> Unit
) {
    if (visible) {
        BackHandler(onBack = onClose)
        val scrollState = rememberScrollState()
        LaunchedEffect(visible, shouldScrollToAlarmSettings) {
            if (visible && shouldScrollToAlarmSettings) {
                withFrameNanos { }
                scrollState.animateScrollTo((scrollState.maxValue * 0.84f).roundToInt())
                onAlarmSettingsScrollHandled()
            }
        }
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
                        modifier = Modifier.verticalScroll(scrollState),
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
                        ParticleWeatherSelector(
                            isAuto = state.isParticleWeatherAuto,
                            selected = state.particleWeather,
                            onToggleAuto = onToggleParticleWeatherAuto,
                            onSelectWeather = onSelectParticleWeather
                        )
                        SettingToggle(stringResource(id = R.string.settings_cats), state.isCatSystemEnabled, onToggleCats)
                        SettingToggle(stringResource(id = R.string.settings_24_hour), state.is24HourFormat, onToggle24HourFormat)
                        SleepSoundSelector(
                            selected = state.sleepSoundMode,
                            enabled = state.whiteNoiseEnabled,
                            remainingSeconds = state.sleepSoundRemainingSeconds,
                            onEnabledChange = onToggleWhiteNoise,
                            onSelect = onSelectSleepSound
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        ThemePresetSelector(
                            selected = state.selectedThemePreset,
                            active = state.activeThemePreset,
                            onSelect = onSetThemePreset
                        )
                        SettingToggle(stringResource(id = R.string.settings_hourly_chime), state.hourlyChimeEnabled, onToggleHourlyChime)
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
                    colors = liquidSwitchColors()
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
                    colors = liquidSwitchColors()
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
                            onSelected = onSetHour,
                            wrapAround = true
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
                            onSelected = onSetMinute,
                            wrapAround = true
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
    onSelected: (Int) -> Unit,
    wrapAround: Boolean = false,
    compact: Boolean = false
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val itemCount = if (wrapAround) values.size * 400 else values.size
    val baseIndex = remember(values, wrapAround) {
        if (wrapAround) {
            val midpoint = itemCount / 2
            midpoint - midpoint.floorMod(values.size)
        } else {
            0
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = baseIndex + selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val currentSelected by rememberUpdatedState(selected)
    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> kotlin.math.abs((item.offset + item.size / 2) - viewportCenter) }
                ?.index
                ?.let { if (wrapAround) it.coerceIn(0, itemCount - 1) else it.coerceIn(0, values.lastIndex) }
                ?: (baseIndex + selectedIndex)
        }
    }

    LaunchedEffect(selectedIndex, wrapAround, itemCount) {
        val targetIndex = if (wrapAround) {
            val currentRound = ((centeredIndex - selectedIndex).toFloat() / values.size).roundToInt()
            (currentRound * values.size + selectedIndex).coerceIn(0, itemCount - 1)
        } else {
            selectedIndex
        }
        if (!listState.isScrollInProgress && centeredIndex != targetIndex) {
            listState.scrollToItem(targetIndex)
        }
    }

    LaunchedEffect(listState, values, wrapAround) {
        snapshotFlow { centeredIndex }
            .distinctUntilChanged()
            .collect { index ->
                val value = values[index.floorMod(values.size)]
                if (value != currentSelected) {
                    onSelected(value)
                }
            }
    }

    Box(
        modifier = Modifier
            .width(if (compact) 72.dp else 86.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .drawWithContent {
                val centerY = size.height / 2f
                val highlightHeight = if (compact) 34.dp else 40.dp
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(6.dp.toPx(), centerY - highlightHeight.toPx() / 2f),
                    size = Size(size.width - 12.dp.toPx(), highlightHeight.toPx()),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
                drawContent()
            }
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = if (compact) 29.dp else 51.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(itemCount) { index ->
                val value = values[index.floorMod(values.size)]
                val distance = kotlin.math.abs(index - centeredIndex)
                Text(
                    text = labelFormatter(value),
                    color = LiquidGlassText.copy(alpha = if (distance == 0) 0.95f else 0.32f),
                    fontSize = if (compact) {
                        if (distance == 0) 18.sp else 13.sp
                    } else {
                        if (distance == 0) 22.sp else 16.sp
                    },
                    fontFamily = UiFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .height(if (compact) 34.dp else 40.dp)
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
                colors = liquidSwitchColors()
            )
        }
    }
}

@Composable
private fun liquidSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Color(0xFFB7E2FF),
    uncheckedThumbColor = Color.White.copy(alpha = 0.92f),
    uncheckedTrackColor = Color.White.copy(alpha = 0.22f),
    uncheckedBorderColor = Color.Transparent,
    checkedBorderColor = Color.Transparent
)

@Composable
private fun ClockContent(
    state: ClockState,
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
        targetValue = 0.65f * state.activeThemePreset.profile().dimStrength,
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
                .reportBounds(onTimeBoundsChanged)

            LandscapeClockContent(
                state = state,
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
            .reportBounds(onTimeBoundsChanged)

        PortraitClockContent(
            state = state,
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
            Text(
                text = state.location,
                color = LiquidGlassText.copy(alpha = 0.52f),
                fontSize = (footerFontSize.value * 0.72f).sp,
                fontFamily = UiFontFamily,
                letterSpacing = 1.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LandscapeDateLocationBlock(state: ClockState, modifier: Modifier = Modifier) {
    SettingsCardSurface(
        shape = RoundedCornerShape(26.dp),
        padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        modifier = modifier.widthIn(min = 190.dp, max = 330.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = state.location,
                color = LiquidGlassText.copy(alpha = 0.82f),
                fontSize = 16.sp,
                fontFamily = UiFontFamily,
                letterSpacing = 1.3.sp,
                maxLines = 1
            )
            Text(
                text = "${state.date} · ${state.dayOfWeek}",
                color = LiquidGlassText.copy(alpha = 0.76f),
                fontSize = 15.sp,
                fontFamily = UiFontFamily,
                maxLines = 1
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

        LandscapeDateLocationBlock(
            state = state,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 2.dp, start = 2.dp)
        )

        MainTimeDisplay(
            state = state,
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
private fun DailyAlarmHint(
    state: ClockState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = state.dailyAlarmProgressFraction()
    val isSnoozing = state.dailyAlarmSnoozeRemainingSeconds > 0
    val title = if (isSnoozing) {
        stringResource(id = R.string.alarm_snooze_hint)
    } else {
        stringResource(id = R.string.alarm_enabled_hint)
    }
    val detail = if (isSnoozing) {
        stringResource(id = R.string.alarm_next_in, formatDurationWords(state.dailyAlarmSnoozeRemainingSeconds))
    } else {
        stringResource(id = R.string.alarm_next_in, formatDurationWords(state.secondsUntilDailyAlarm()))
    }
    Box(modifier = modifier) {
        SettingsCardSurface(
            shape = RoundedCornerShape(20.dp),
            padding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            modifier = Modifier
                .clickable(onClick = onClick)
                .drawWithContent {
                    drawContent()
                    val stroke = 2.4.dp.toPx()
                    val corner = 20.dp.toPx()
                    val inset = stroke / 2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = stroke)
                    )
                    drawRoundedBorderProgress(
                        progress = progress,
                        inset = inset,
                        corner = corner,
                        stroke = stroke + 0.8.dp.toPx()
                    )
                }
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
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
                Text(
                    text = detail,
                    color = LiquidGlassText.copy(alpha = 0.48f),
                    fontSize = 9.sp,
                    fontFamily = UiFontFamily
                )
            }
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
            .background(Color(0xFF06111D).copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center
    ) {
        val dialogShape = RoundedCornerShape(34.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 520.dp)
                .clip(dialogShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1D2938).copy(alpha = 0.94f),
                            Color(0xFF111A26).copy(alpha = 0.97f),
                            Color(0xFF0B121C).copy(alpha = 0.98f)
                        )
                    ),
                    shape = dialogShape
                )
                .border(
                    width = 0.6.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = dialogShape
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.08f)
                            )
                        )
                    )
                }
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.alarm_dialog_title),
                    color = LiquidGlassText,
                    fontSize = 28.sp,
                    fontFamily = UiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "%02d:%02d".format(state.dailyAlarmHour, state.dailyAlarmMinute),
                    color = LiquidGlassText.copy(alpha = 0.90f),
                    fontSize = 46.sp,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlarmDialogActionButton(
                        title = stringResource(id = R.string.alarm_snooze_title),
                        subtitle = stringResource(id = R.string.alarm_snooze_subtitle),
                        titleColor = Color.White,
                        subtitleColor = Color.White.copy(alpha = 0.72f),
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF4C9DFF),
                                Color(0xFF1464DD)
                            )
                        ),
                        onClick = onSnooze,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                    )
                    AlarmDialogActionButton(
                        title = stringResource(id = R.string.alarm_dismiss),
                        subtitle = null,
                        titleColor = Color(0xFF1C2531),
                        subtitleColor = Color(0xFF1C2531).copy(alpha = 0.70f),
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFE5E9EF).copy(alpha = 0.96f),
                                Color(0xFFB8C0CB).copy(alpha = 0.95f)
                            )
                        ),
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmDialogActionButton(
    title: String,
    subtitle: String?,
    titleColor: Color,
    subtitleColor: Color,
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .background(brush = brush, shape = shape)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.26f),
                shape = shape
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f)
                        )
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = titleColor,
                fontFamily = UiFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontFamily = UiFontFamily,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
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
                    if (compact) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            TimerWheelSettingRow(
                                label = stringResource(id = R.string.mode_focus_duration),
                                value = state.pomodoroFocusMinutes,
                                values = (5..90).toList(),
                                onSelected = onSetPomodoroFocus,
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                            TimerWheelSettingRow(
                                label = stringResource(id = R.string.mode_break_duration),
                                value = state.pomodoroBreakMinutes,
                                values = (1..30).toList(),
                                onSelected = onSetPomodoroBreak,
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
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
                }
                ClockMode.COUNTDOWN -> {
                    TimerWheelSettingRow(
                        label = stringResource(id = R.string.mode_countdown_duration),
                        value = state.countdownDurationMinutes,
                        values = (1..180).toList(),
                        onSelected = onSetCountdown,
                        compact = compact
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
    onSelected: (Int) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Text(
            text = label,
            color = LiquidGlassText.copy(alpha = 0.66f),
            fontFamily = UiFontFamily,
            fontSize = if (compact) 10.sp else 11.sp,
            maxLines = 1
        )
        Row(
            modifier = Modifier.height(if (compact) 92.dp else 128.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeWheelColumn(
                values = values,
                selected = value,
                labelFormatter = { it.toString().padStart(2, '0') },
                onSelected = onSelected,
                compact = compact
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

private fun ClockState.secondsUntilDailyAlarm(): Int {
    val nowSeconds = currentHour24 * 3600 + (minute.toIntOrNull() ?: 0) * 60 + (second.toIntOrNull() ?: 0)
    val alarmSeconds = dailyAlarmHour * 3600 + dailyAlarmMinute * 60
    val remaining = (alarmSeconds - nowSeconds).floorMod(24 * 60 * 60)
    return if (remaining == 0 && !isDailyAlarmRinging) 24 * 60 * 60 else remaining
}

private fun ClockState.dailyAlarmProgressFraction(): Float {
    if (dailyAlarmSnoozeRemainingSeconds > 0) {
        val snoozeTotalSeconds = 10f * 60f
        return 1f - (dailyAlarmSnoozeRemainingSeconds / snoozeTotalSeconds)
    }
    val remaining = secondsUntilDailyAlarm()
    return 1f - (remaining / (24f * 60f * 60f))
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

private fun DrawScope.drawRoundedBorderProgress(
    progress: Float,
    inset: Float,
    corner: Float,
    stroke: Float
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    if (clampedProgress <= 0f || size.width <= stroke || size.height <= stroke) return

    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    val radius = corner
        .coerceAtMost((right - left) / 2f)
        .coerceAtMost((bottom - top) / 2f)

    val path = android.graphics.Path().apply {
        val centerX = (left + right) / 2f
        moveTo(centerX, top)
        lineTo(right - radius, top)
        quadTo(right, top, right, top + radius)
        lineTo(right, bottom - radius)
        quadTo(right, bottom, right - radius, bottom)
        lineTo(left + radius, bottom)
        quadTo(left, bottom, left, bottom - radius)
        lineTo(left, top + radius)
        quadTo(left, top, left + radius, top)
        lineTo(centerX, top)
        close()
    }

    val progressPath = android.graphics.Path()
    val measure = android.graphics.PathMeasure(path, false)
    measure.getSegment(0f, measure.length * clampedProgress, progressPath, true)

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        shader = android.graphics.LinearGradient(
            0f,
            0f,
            size.width,
            size.height,
            intArrayOf(
                android.graphics.Color.argb(245, 191, 236, 255),
                android.graphics.Color.argb(235, 236, 223, 255),
                android.graphics.Color.argb(245, 255, 205, 238)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
    }

    drawContext.canvas.nativeCanvas.drawPath(progressPath, paint)
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

private fun Modifier.reportBounds(onBoundsChanged: (RectF) -> Unit): Modifier {
    return onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInRoot()
        onBoundsChanged(RectF(rect.left, rect.top, rect.right, rect.bottom))
    }
}

private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod
