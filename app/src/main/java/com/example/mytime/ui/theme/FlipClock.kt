package com.example.mytime.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mytime.R
import com.example.mytime.ui.ClockFont
import com.example.mytime.ui.ClockState
import kotlinx.coroutines.isActive
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
    onToggleDynamicWallpaper: (Boolean) -> Unit,
    onToggle24HourFormat: (Boolean) -> Unit
) {
    val currentFont = state.selectedFont.family

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 背景层
        state.backgroundRes?.let { resId ->
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = 1.25f, 
                        scaleY = 1.25f,
                        rotationX = (-state.parallaxOffset.y * 0.05f).coerceIn(-5f, 5f),
                        rotationY = (state.parallaxOffset.x * 0.05f).coerceIn(-5f, 5f)
                    )
                    .offset { 
                        IntOffset(
                            (-state.parallaxOffset.x * 0.3f).roundToInt(),
                            (-state.parallaxOffset.y * 0.3f).roundToInt()
                        )
                    }
                    .alpha(0.6f),
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.5f), BlendMode.Multiply)
            )
        }

        if (state.isParticleSystemEnabled) {
            SeamlessParticleLayer()
        }

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
            mainDisplayModifier = mainDisplayTransform
        )

        SettingsMenu(
            visible = state.isSettingsVisible,
            state = state,
            onClose = onCloseSettings,
            onToggleBurnIn = onToggleBurnIn,
            onToggleSound = onToggleSound,
            onSelectFont = onSelectFont,
            onToggleParallax = onToggleParallax,
            onToggleParticles = onToggleParticles,
            onToggleDynamicWallpaper = onToggleDynamicWallpaper,
            onToggle24HourFormat = onToggle24HourFormat
        )
    }
}

@Composable
fun SeamlessParticleLayer() {
    val particles = remember { List(70) { ParticleData() } }
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
        particles.forEach { p ->
            val currentYProgress = (p.startY + (seconds * p.speed)) % 1.0f
            val y = currentYProgress * size.height
            val x = p.startX * size.width
            val edgeAlpha = when {
                currentYProgress < 0.1f -> currentYProgress / 0.1f
                currentYProgress > 0.9f -> (1.0f - currentYProgress) / 0.1f
                else -> 1.0f
            }
            drawCircle(color = Color.White.copy(alpha = p.alpha * edgeAlpha), radius = p.size, center = Offset(x, y))
        }
    }
}

class ParticleData {
    val startX = Random.nextFloat(); val startY = Random.nextFloat()
    val speed = 0.05f + Random.nextFloat() * 0.1f; val size = 1f + Random.nextFloat() * 3f; val alpha = 0.1f + Random.nextFloat() * 0.4f
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
    onToggleDynamicWallpaper: (Boolean) -> Unit,
    onToggle24HourFormat: (Boolean) -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { onClose() }
                )
                Card(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.98f)),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(id = R.string.settings_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        SettingToggle(stringResource(id = R.string.settings_gyroscope), state.isParallaxEnabled, onToggleParallax)
                        SettingToggle(stringResource(id = R.string.settings_particles), state.isParticleSystemEnabled, onToggleParticles)
                        SettingToggle(stringResource(id = R.string.settings_wallpaper), state.isDynamicWallpaperEnabled, onToggleDynamicWallpaper)
                        SettingToggle(stringResource(id = R.string.settings_burnin), state.isBurnInProtectionEnabled, onToggleBurnIn)
                        SettingToggle(stringResource(id = R.string.settings_24_hour), state.is24HourFormat, onToggle24HourFormat)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        SettingToggle(stringResource(id = R.string.settings_sound_button), state.isSoundButtonVisible, onToggleSound)
                        Column {
                            Text(stringResource(id = R.string.settings_clock_font), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.allFonts) { fontItem ->
                                    val isSelected = fontItem.name == state.selectedFont.name
                                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent).border(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).clickable { onSelectFont(fontItem) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(text = fontItem.name, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f), fontFamily = fontItem.family, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(id = R.string.settings_done), color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF4CAF50)))
    }
}

@Composable
private fun ClockContent(
    state: ClockState,
    fontFamily: FontFamily,
    onPlayAudio: () -> Unit,
    onToggleSettings: () -> Unit,
    mainDisplayModifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val baseFontSize = ((screenWidth / 8).coerceAtLeast(32) * 1.6f) * state.selectedFont.sizeMultiplier
    val footerFontSize = (screenWidth / 20).coerceIn(16, 24).sp
    
    // 开启防烧屏时降低亮度
    val alpha by animateFloatAsState(targetValue = if (state.isBurnInProtectionEnabled) 0.65f else 0.9f, animationSpec = tween(1000), label = "burnInAlpha")

    Box(modifier = Modifier.fillMaxSize().alpha(alpha).padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = state.location, color = Color.White, fontSize = 13.sp, fontFamily = fontFamily)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryStatus(state.batteryLevel)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onToggleSettings,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.cd_settings),
                        modifier = Modifier.size(22.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
        MainTimeDisplay(
            state = state,
            fontFamily = fontFamily,
            baseSize = baseFontSize,
            modifier = Modifier.align(Alignment.Center).then(mainDisplayModifier)
        )
        Text(
            text = "${state.date} · ${state.dayOfWeek}",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = footerFontSize,
            fontFamily = fontFamily,
            modifier = Modifier.align(Alignment.BottomStart)
        )
        if (state.isSoundButtonVisible) AudioButton(onPlayAudio, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun MainTimeDisplay(state: ClockState, fontFamily: FontFamily, baseSize: Float, modifier: Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        FlipDigit(state.hour, fontFamily, baseSize)
        Text(":", color = Color.White.copy(alpha = 0.8f), fontSize = (baseSize * 0.8f).sp, fontWeight = FontWeight.Light)
        FlipDigit(state.minute, fontFamily, baseSize)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (state.amPm.isNotBlank()) 20.dp else 8.dp)
        ) {
            if (state.amPm.isNotBlank()) {
                Text(state.amPm, color = Color.White.copy(alpha = 0.6f), fontSize = (baseSize * 0.4f).sp, fontFamily = fontFamily)
            }
            FlipDigit(state.second, fontFamily, if (state.amPm.isNotBlank()) baseSize * 0.25f else baseSize * 0.32f)
        }
    }
}

@Composable
fun FlipDigit(value: String, fontFamily: FontFamily, fontSize: Float) {
    var prevValue by remember { mutableStateOf(value) }
    val rotation = remember { Animatable(0f) }
    val slotWidth = with(LocalDensity.current) { (fontSize * 1.35f).sp.toDp() }
    LaunchedEffect(value) {
        if (value != prevValue) {
            rotation.snapTo(0f); rotation.animateTo(180f, tween(500, easing = LinearOutSlowInEasing)); prevValue = value; rotation.snapTo(0f)
        }
    }
    Box(modifier = Modifier.width(slotWidth)) {
        val modifier = { rot: Float -> Modifier.graphicsLayer { rotationX = rot; cameraDistance = 12f * density } }
        if (rotation.value < 90f) {
            Text(
                prevValue,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = modifier(rotation.value).fillMaxWidth()
            )
        } else {
            Text(
                value,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = modifier(rotation.value - 180f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BatteryStatus(levelStr: String) {
    val level = levelStr.trimEnd('%').toIntOrNull() ?: 0
    val batteryColor = when {
        level <= 20 -> Color(0xFFFF4D4D)
        level <= 50 -> Color(0xFFFFD666)
        else -> Color.White.copy(alpha = 0.9f)
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
        Text(text = "$level", color = if (level > 50) Color.Black.copy(alpha = 0.7f) else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 3.dp))
    }
}

@Composable
private fun AudioButton(onClick: () -> Unit, modifier: Modifier) {
    Button(onClick = onClick, modifier = modifier.size(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues(0.dp), shape = CircleShape) {
        Image(
            painter = painterResource(id = R.drawable.cat_icon),
            contentDescription = stringResource(id = R.string.cd_play_sound),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
        )
    }
}
