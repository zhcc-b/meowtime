package com.example.mytime.ui

import androidx.compose.ui.geometry.Offset
import com.example.mytime.R

/**
 * 封装时钟所需的所有状态
 */
data class ClockState(
    val hour: String = "00",
    val minute: String = "00",
    val second: String = "00",
    val amPm: String = "",
    val date: String = "--",
    val dayOfWeek: String = "--",
    val batteryLevel: String = "--",
    val location: String = "Loading...",
    val backgroundRes: Int? = null,
    
    // 设置项
    val isBurnInProtectionEnabled: Boolean = true,
    val isSoundButtonVisible: Boolean = true,
    val isSettingsVisible: Boolean = false,
    
    // 酷炫效果开关
    val isParallaxEnabled: Boolean = true,
    val isParticleSystemEnabled: Boolean = false,
    val isDynamicWallpaperEnabled: Boolean = true,
    val is24HourFormat: Boolean = true,
    
    // 字体设置
    val selectedFont: ClockFont = AvailableClockFonts[0],
    val allFonts: List<ClockFont> = AvailableClockFonts,

    // 视差位移 (由陀螺仪控制)
    val parallaxOffset: Offset = Offset.Zero,
    
    // 防烧屏位移 (由时间循环控制，定期微动)
    val burnInOffset: Offset = Offset.Zero
)
