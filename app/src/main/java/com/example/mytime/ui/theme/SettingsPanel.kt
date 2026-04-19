package com.example.mytime.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytime.R
import com.example.mytime.ui.ClockFont
import com.example.mytime.ui.ClockState
import com.example.mytime.ui.ParticleWeather
import com.example.mytime.ui.SleepSoundMode
import com.example.mytime.ui.ThemePreset
import com.example.mytime.ui.UiFontFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

@Composable
internal fun SettingsMenu(
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
internal fun TimeWheelColumn(
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
internal fun ThemePreset.label(): String {
    return when (this) {
        ThemePreset.AUTO -> stringResource(id = R.string.theme_auto)
        ThemePreset.FOCUS -> stringResource(id = R.string.theme_focus)
        ThemePreset.PLAYFUL -> stringResource(id = R.string.theme_playful)
        ThemePreset.SERENE -> stringResource(id = R.string.theme_serene)
        ThemePreset.NIGHT -> stringResource(id = R.string.theme_night)
    }
}
