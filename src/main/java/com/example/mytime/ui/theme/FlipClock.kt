package com.example.mytime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.mytime.R

// font
val customFont = FontFamily(Font(R.font.style1))


@Composable
fun FlipClockWithBackground(
    batteryLevel: String,
    location: String,
    date: String,
    dayOfWeek: String,
    hour: String,
    minute: String,
    second: String,
    amPm: String,
    backgroundRes: Int,
    onPlayAudio: () -> Unit

) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 添加背景图片
        Image(
            painter = painterResource(id = backgroundRes),
            contentDescription = "Background Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.6f).blur(8.dp),
            colorFilter = ColorFilter.tint(
                Color.Black.copy(alpha = 0.5f),
                BlendMode.Multiply
            )
        )

        // 添加时钟组件
        DynamicFlipClock(
            batteryLevel = batteryLevel,
            location = location,
            date = date,
            dayOfWeek = dayOfWeek,
            hour = hour,
            minute = minute,
            second = second,
            amPm = amPm,
            onPlayAudio = onPlayAudio
        )
    }
}


@Composable
fun DynamicFlipClock(
    batteryLevel: String,
    location: String,
    date: String,
    dayOfWeek: String,
    hour: String,
    minute: String,
    second: String,
    amPm: String,
    onPlayAudio: () -> Unit
) {
    // 定义用于动态偏移的 Animatable
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    // 定义循环动画效果
    LaunchedEffect(Unit) {
        while (true) {
            offsetX.animateTo(
                targetValue = 10f,
                animationSpec = tween(durationMillis = 90000, easing = LinearOutSlowInEasing)
            )
            offsetX.animateTo(
                targetValue = -10f,
                animationSpec = tween(durationMillis = 90000, easing = LinearOutSlowInEasing)
            )
            offsetY.animateTo(
                targetValue = 10f,
                animationSpec = tween(durationMillis = 90000, easing = LinearOutSlowInEasing)
            )
            offsetY.animateTo(
                targetValue = -10f,
                animationSpec = tween(durationMillis = 90000, easing = LinearOutSlowInEasing)
            )
        }
    }

    // Box 包裹动态偏移的 FlipClock
    Box(
        modifier = Modifier
            .offset(x = offsetX.value.dp, y = offsetY.value.dp) 
            .graphicsLayer {
                // 确保裁剪内容，防止溢出
                clip = true
            }
    ) {
        FlipClock(
            batteryLevel = batteryLevel,
            location = location,
            date = date,
            dayOfWeek = dayOfWeek,
            hour = hour,
            minute = minute,
            second = second,
            amPm = amPm,
            onPlayAudio = onPlayAudio
        )
    }
}


@Composable
fun FlipClock(
    batteryLevel: String,
    location: String,
    date: String,
    dayOfWeek: String,
    hour: String,
    minute: String,
    second: String,
    amPm: String,
    onPlayAudio: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val totalCharCount = 8
    val baseFontSize = ((screenWidthDp / totalCharCount).coerceAtLeast(32)) * 1.6f

    val hourMinuteFontSize = baseFontSize
    val secondFontSize = (baseFontSize * 0.2f)
    val amPmFontSize = (baseFontSize * 0.5f)

    val fixedInfoFontSize = 13.sp

    val isDarkMode = isSystemInDarkTheme()
    val appAlpha = if (isDarkMode) 0.6f else 0.8f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(appAlpha)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = location, color = Color.White, fontSize = fixedInfoFontSize, fontFamily = customFont)
            BatteryStatusRow(batteryLevel = batteryLevel, fontSize = fixedInfoFontSize)
        }

        // 底部栏
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = "$date - $dayOfWeek",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = (fixedInfoFontSize * 3.5f),
                fontFamily = customFont
            )
        }

        // 中间时间显示
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center)
        ) {
            // 小时
            FlipDigit(
                value = hour,
                fontSize = hourMinuteFontSize,
                textColor = Color.White.copy(alpha = 0.7f),
                fontFamily = customFont
            )

            // 分隔符
            Text(
                text = "•",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = (hourMinuteFontSize * 1.2f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = customFont
            )

            // 分钟
            FlipDigit(
                value = minute,
                fontSize = hourMinuteFontSize,
                textColor = Color.White.copy(alpha = 0.7f),
                fontFamily = customFont
            )

            // 秒数和AM/PM的垂直布局
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // AM/PM
                Text(
                    text = amPm,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = amPmFontSize.sp,
                    fontFamily = customFont
                )

                // 秒数
                FlipDigit(
                    value = second,
                    fontSize = secondFontSize,
                    textColor = Color.White.copy(alpha = 0.8f),
                    fontFamily = customFont
                )
            }
        }
        // 添加右下角的播放按钮
        Button(
            onClick = { onPlayAudio() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
        ) {
            Image(
                painter = painterResource(id = R.drawable.cat_icon),
                contentDescription = "Play Audio",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}


@Composable
fun FlipDigit(
    value: String,
    fontSize: Float = 64f,
    textColor: Color = Color.White,
    fontFamily: FontFamily = FontFamily.Default
) {
    var prevValue by remember { mutableStateOf(value) }
    val rotation = remember { Animatable(0f) } 

    LaunchedEffect(value) {
        if (value != prevValue) {
            // 新值出现，开始动画
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 180f, 
                animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
            )
            prevValue = value
            rotation.snapTo(0f) 
        }
    }

    Box(modifier = Modifier) {
        // 旧值逐渐隐藏
        if (rotation.value < 90f) {
            Text(
                text = prevValue,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = fontFamily,
                modifier = Modifier.graphicsLayer {
                    rotationX = rotation.value 
                    cameraDistance = 1000f * density
                }
            )
        }

        // 新值逐渐显示
        if (rotation.value >= 90f) {
            Text(
                text = value,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = fontFamily,
                modifier = Modifier.graphicsLayer {
                    rotationX = rotation.value - 180f 
                    cameraDistance = 1000f * density
                }
            )
        }
    }
}


@Composable
fun BatteryIconHorizontal(level: Int, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val width = size.toPx()
        val height = size.toPx() / 2

        // 绘制电池主体
        drawRect(
            color = Color.White,
            topLeft = Offset(0f, size.toPx() / 4),
            size = Size(width * 0.9f, height)
        )

        // 绘制电池触点
        drawRect(
            color = Color.White,
            topLeft = Offset(width * 0.9f, size.toPx() / 3),
            size = Size(width * 0.1f, height / 2)
        )

        // 绘制电池电量
        drawRect(
            color = if (level > 20) Color.White else Color.Red,
            topLeft = Offset(0f, size.toPx() / 4),
            size = Size(width * 0.9f * (level / 100f), height)
        )
    }
}


@Composable
fun BatteryStatusRow(batteryLevel: String, fontSize: androidx.compose.ui.unit.TextUnit = 14.sp) {
    val level = batteryLevel.trimEnd('%').toIntOrNull() ?: 0

    Row(verticalAlignment = Alignment.CenterVertically) {
        BatteryIconHorizontal(level = level, size = fontSize.value.dp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = batteryLevel, color = Color.Gray, fontSize = fontSize)
    }
}
