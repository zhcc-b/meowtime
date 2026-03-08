# MeowTime

MeowTime 是一个基于 Jetpack Compose 的全屏翻页时钟 App，主打高颜值桌面时钟体验和可调节视觉效果。

## App Screenshot
![MeowTime Screenshot](./docs/images/app-screenshot.png)

## Current Features

1. Flip Clock 动效：小时、分钟、秒钟翻页动画。
2. 12/24 小时制切换：可在设置中实时切换。
3. 动态壁纸：按时间段切换早晨/下午/夜间背景。
4. 防烧屏保护：定时微位移与亮度策略。
5. 陀螺仪 3D 视差：可开关。
6. 粒子背景特效：可开关。
7. 字体切换：内置多种本地字体（不依赖 Google 服务）。
8. 位置信息显示：优先设备定位，失败时走 HTTPS IP 兜底。
9. 电量状态显示：顶部电量图标与数值。
10. 猫咪音效按钮：右下角猫猫按钮播放音效。
11. 设置持久化：主要开关和字体会保存在 DataStore。
12. 中文文案支持：已提供 `values-zh-rCN` 资源。

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Android ViewModel + StateFlow
- DataStore Preferences

## Build & Run

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Version

- Current app version: `1.1`
