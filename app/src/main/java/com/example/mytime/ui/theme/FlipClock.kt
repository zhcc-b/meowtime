package com.example.mytime.ui.theme

import android.graphics.RectF
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mytime.R
import com.example.mytime.ui.ClockFont
import com.example.mytime.ui.ClockState
import com.example.mytime.ui.ParticleWeather
import com.example.mytime.ui.UiFontFamily
import coil.compose.AsyncImage
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
    onToggle24HourFormat: (Boolean) -> Unit
) {
    val currentFont = state.selectedFont.family
    var timeRect by remember { mutableStateOf(RectF()) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 背景层
        val backgroundModifier = Modifier
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
            .alpha(0.6f)

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
            SeamlessParticleLayer(weather = state.particleWeather)
        }

        FilamentCatOverlay(
            enabled = state.isCatSystemEnabled,
            weather = state.particleWeather,
            forbiddenRect = timeRect,
            modifier = Modifier.fillMaxSize()
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
            mainDisplayModifier = mainDisplayTransform,
            onTimeBoundsChanged = { timeRect = it }
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
            onToggleParticleWeatherAuto = onToggleParticleWeatherAuto,
            onSelectParticleWeather = onSelectParticleWeather,
            onToggleCats = onToggleCats,
            onToggleDynamicWallpaper = onToggleDynamicWallpaper,
            onToggle24HourFormat = onToggle24HourFormat
        )
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

private val LiquidGlassTint = Color(0xFFD7E8FF)
private val LiquidGlassCool = Color(0xFF9CC7FF)
private val LiquidGlassShadow = Color(0xFF09111C)
private val LiquidGlassText = Color(0xFFF7FBFF)

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
            ParticleWeather.CLOUDY, ParticleWeather.FOG -> 1.2f
            else -> 1.0f
        }
        drawCloudCluster(
            center = Offset(x, y),
            size = Size(width, height),
            color = palette.cloud.copy(alpha = cloudAlpha)
        )
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
            ParticleWeather.CLOUDY -> 0.07f
            else -> 0.09f
        } + sin(seconds * 0.18f + index).coerceIn(-1f, 1f) * 0.01f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    palette.fog.copy(alpha = alpha),
                    palette.fog.copy(alpha = alpha * 0.7f),
                    Color.Transparent
                )
            ),
            topLeft = Offset(drift - size.width * 0.55f, y),
            size = Size(size.width * 1.10f, size.height * 0.09f),
            cornerRadius = CornerRadius(size.height * 0.05f, size.height * 0.05f)
        )
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
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.24f),
                        LiquidGlassTint.copy(alpha = 0.16f),
                        LiquidGlassCool.copy(alpha = 0.10f)
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.56f),
                        Color.White.copy(alpha = 0.18f),
                        LiquidGlassCool.copy(alpha = 0.16f)
                    )
                ),
                shape = shape
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = highlightAlpha),
                            Color.Transparent,
                            LiquidGlassShadow.copy(alpha = 0.10f)
                        )
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
        highlightAlpha = if (selected) 0.28f else 0.18f
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.12f),
                                LiquidGlassCool.copy(alpha = 0.08f)
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
                        .background(Color(0xFF07101A).copy(alpha = 0.38f))
                        .clickable { onClose() }
                )
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(332.dp),
                    shape = RoundedCornerShape(topStart = 34.dp, bottomStart = 34.dp),
                    padding = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
                    highlightAlpha = 0.26f
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
                            text = "Liquid controls",
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
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        SettingToggle(stringResource(id = R.string.settings_sound_button), state.isSoundButtonVisible, onToggleSound)
                        LiquidGlassSurface(
                            shape = RoundedCornerShape(24.dp),
                            padding = PaddingValues(16.dp),
                            highlightAlpha = 0.18f
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
                                        LiquidGlassChip(
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
                        LiquidGlassChip(
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
    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        padding = PaddingValues(16.dp),
        highlightAlpha = 0.18f
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
                    LiquidGlassChip(
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
                    LiquidGlassChip(
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
    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        highlightAlpha = 0.18f
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
    mainDisplayModifier: Modifier = Modifier,
    onTimeBoundsChanged: (RectF) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val baseFontSize = ((screenWidth / 8).coerceAtLeast(32) * 1.48f) * state.selectedFont.sizeMultiplier
    val footerFontSize = (screenWidth / 20).coerceIn(16, 24).sp

    val alpha by animateFloatAsState(targetValue = if (state.isBurnInProtectionEnabled) 0.65f else 0.9f, animationSpec = tween(1000), label = "burnInAlpha")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(26.dp),
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = state.location,
                    color = LiquidGlassText.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontFamily = UiFontFamily,
                    letterSpacing = 0.8.sp
                )
            }
            LiquidGlassSurface(
                shape = RoundedCornerShape(28.dp),
                padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BatteryStatus(state.batteryLevel)
                    Spacer(modifier = Modifier.width(4.dp))
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
        }
        MainTimeDisplay(
            state = state,
            fontFamily = fontFamily,
            baseSize = baseFontSize,
            modifier = Modifier
                .align(Alignment.Center)
                .then(mainDisplayModifier)
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInRoot()
                    onTimeBoundsChanged(
                        RectF(rect.left, rect.top, rect.right, rect.bottom)
                    )
                }
        )
        Text(
            text = "${state.date} · ${state.dayOfWeek}",
            color = LiquidGlassText.copy(alpha = 0.9f),
            fontSize = footerFontSize,
            fontFamily = UiFontFamily,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            LiquidGlassTint.copy(alpha = 0.10f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
        if (state.isSoundButtonVisible) AudioButton(onPlayAudio, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun MainTimeDisplay(state: ClockState, fontFamily: FontFamily, baseSize: Float, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val availableWidth = maxWidth.value
        val widthFactor = (if (state.amPm.isNotBlank()) 5.35f else 4.95f) * state.selectedFont.widthFitMultiplier
        val fittedBaseSize = ((availableWidth - 12f) / widthFactor).coerceAtLeast(34f)
        val resolvedBaseSize = minOf(baseSize, fittedBaseSize)
        val secondsScale = state.selectedFont.secondsScale

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FlipDigit(state.hour, state.selectedFont, resolvedBaseSize)
            Text(":", color = LiquidGlassText.copy(alpha = 0.84f), fontSize = (resolvedBaseSize * 0.78f).sp, fontWeight = FontWeight.Light)
            FlipDigit(state.minute, state.selectedFont, resolvedBaseSize)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (state.amPm.isNotBlank()) 18.dp else 10.dp)
            ) {
                if (state.amPm.isNotBlank()) {
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        highlightAlpha = 0.16f
                    ) {
                        Text(
                            state.amPm,
                            color = LiquidGlassText.copy(alpha = 0.7f),
                            fontSize = (resolvedBaseSize * 0.28f).sp,
                            fontFamily = UiFontFamily
                        )
                    }
                }
                FlipDigit(
                    state.second,
                    state.selectedFont,
                    (if (state.amPm.isNotBlank()) resolvedBaseSize * 0.23f else resolvedBaseSize * 0.28f) * secondsScale,
                    compact = true
                )
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
    val staticTopValue = if (progress.value < 0.5f) outgoingValue else currentValue
    val staticBottomValue = if (progress.value < 0.5f) outgoingValue else currentValue
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
    val baseTint = if (isTop) Color.White.copy(alpha = 0.22f) else LiquidGlassCool.copy(alpha = 0.14f)
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
                        Color.White.copy(alpha = if (isTop) 0.20f else 0.14f),
                        LiquidGlassTint.copy(alpha = 0.14f),
                        LiquidGlassShadow.copy(alpha = if (elevated) 0.18f else 0.12f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
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
