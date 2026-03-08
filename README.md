# MeowTime

MeowTime is a full-screen page-turning clock app built with Jetpack Compose, offering a visually stunning desktop clock experience with customizable visual effects.

## App Screenshot
![MeowTime Screenshot](./docs/images/app-screenshot.png)

## Current Features

1. Flip Clock animations: Hour, minute, and second page-turning effects.
2. 12/24-hour format toggle: Switchable in real-time via settings.
3. Dynamic wallpaper: Morning/afternoon/night backgrounds cycle by time period.
4. Burn-in Protection: Scheduled micro-shifts and brightness strategies.
5. Gyroscope 3D Parallax: Toggleable.
6. Particle Background Effects: Toggleable.
7. Font Switching: Multiple built-in local fonts (Google services-independent).
8. Location Display: Prioritizes device positioning; falls back to HTTPS IP if unavailable.
9. Battery status display: Top battery icon and numerical value.
10. Cat sound button: Play sound effects via the cat button in the bottom-right corner.
11. Settings persistence: Primary toggles and fonts saved in DataStore.
12. Chinese text support: `values-zh-rCN` resources provided.


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
