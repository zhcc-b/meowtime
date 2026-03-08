package com.example.mytime.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.mytime.R

/**
 * 统一的字体模型，仅使用本地字体（兼容无 Google 服务设备）
 */
sealed class ClockFont(val name: String, val sizeMultiplier: Float = 1.0f) {
    abstract val family: FontFamily

    class Local(
        name: String, 
        resId: Int, 
        sizeMultiplier: Float = 1.0f
    ) : ClockFont(name, sizeMultiplier) {
        override val family: FontFamily = FontFamily(Font(resId))
    }
}

// 预定义字体列表
val AvailableClockFonts = listOf(
    ClockFont.Local("Modern", R.font.tx, sizeMultiplier = 1.3f),
    ClockFont.Local("Style 1", R.font.style1, sizeMultiplier = 1.0f),
    ClockFont.Local("Digital", R.font.digital, sizeMultiplier = 1.5f),
    ClockFont.Local("Pixel", R.font.pixels, sizeMultiplier = 1.3f)
)
