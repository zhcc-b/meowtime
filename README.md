# MeowTime

MeowTime is a full-screen page-turning clock app built with Jetpack Compose, focused on expressive visuals, customizable effects, and always-on desk mode usability.

## App Screenshot
![MeowTime Screenshot](./docs/images/app-screenshot.png)

## Current Features

1. Flip Clock animations: Hour, minute, and second page-turning effects.
2. 12/24-hour format toggle: Switchable in real-time via settings.
3. Dynamic wallpaper: Morning/afternoon/night backgrounds cycle by time period.
4. Burn-in Protection: Scheduled micro-shifts and brightness strategies.
5. Gyroscope 3D Parallax: Toggleable.
6. Weather Particle Background: Rain / Snowflake / Hail / Wind / Drizzle / Blizzard modes.
7. Weather Mode Control: Manual weather selection in settings, with optional auto-random weather every 8 hours.
8. Realtime 3D Cat Layer: Filament + TextureView based cat rendering integrated into the main clock scene.
9. Cat Behavior FSM: Idle / Walking / Reacting states with randomized movement and clock-event reactions.
10. Weather-linked Cat Behavior: Cat movement cadence, speed, head motion, and reaction timing adapt by current weather.
11. Font Switching: Multiple built-in local fonts (Google services-independent).
12. Location Display: Prioritizes device positioning; falls back to HTTPS IP if unavailable.
13. Battery status display: Top battery icon and numerical value.
14. Cat sound button: Play sound effects via the cat button in the bottom-right corner.
15. Settings persistence: Primary toggles, font choice, and weather mode are saved in DataStore.
16. Chinese text support: `values-zh-rCN` resources provided.


## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Filament (`filament-android`, `gltfio-android`)
- Android ViewModel + StateFlow
- DataStore Preferences

## Build & Run

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Version

- Current app version: `1.1`
