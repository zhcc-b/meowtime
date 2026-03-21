package com.example.mytime.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.mytime.R

/**
 * 统一的字体模型，仅使用本地字体（兼容无 Google 服务设备）
 */
sealed class ClockFont(
    val name: String,
    val sizeMultiplier: Float = 1.0f,
    val widthFitMultiplier: Float = 1.0f,
    val verticalBias: Float = 0.1f,
    val secondsScale: Float = 1.0f
) {
    abstract val family: FontFamily

    class Local(
        name: String, 
        resId: Int, 
        sizeMultiplier: Float = 1.0f,
        widthFitMultiplier: Float = 1.0f,
        verticalBias: Float = 0.1f,
        secondsScale: Float = 1.0f
    ) : ClockFont(name, sizeMultiplier, widthFitMultiplier, verticalBias, secondsScale) {
        override val family: FontFamily = FontFamily(Font(resId))
    }
}

val UiFontFamily = FontFamily(Font(R.font.tx))

// 预定义字体列表
val AvailableClockFonts = listOf(
    ClockFont.Local(
        "Modern",
        R.font.tx,
        sizeMultiplier = 1.28f,
        widthFitMultiplier = 0.92f,
        verticalBias = 0.3f,
        secondsScale = 1.00f
    ),
    ClockFont.Local(
        "Thick",
        R.font.style1,
        sizeMultiplier = 1.16f,
        widthFitMultiplier = 0.94f,
        verticalBias = 0.35f,
        secondsScale = 0.98f
    ),
    ClockFont.Local(
        "Digital",
        R.font.digital,
        sizeMultiplier = 1.30f,
        widthFitMultiplier = 0.94f,
        verticalBias = 0.0f,
        secondsScale = 0.94f
    ),
    ClockFont.Local(
        "Pixel",
        R.font.pixels,
        sizeMultiplier = 1.38f,
        widthFitMultiplier = 0.92f,
        verticalBias = -0.2f,
        secondsScale = 0.84f
    )
)
