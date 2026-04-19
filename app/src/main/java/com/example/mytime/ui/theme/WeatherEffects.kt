package com.example.mytime.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.mytime.ui.ParticleWeather
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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
