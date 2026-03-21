# MeowTime

MeowTime is a full-screen desk clock for Android, built with Jetpack Compose and tuned for always-on landscape use. The current branch focuses on a liquid-glass visual language, mechanical split-flap time transitions, weather-reactive backgrounds, and a Filament cat layer that lives around the clock instead of covering it.

## App Screenshot
![MeowTime Screenshot](./docs/images/app-screenshot.png)

## Current Features

1. Liquid-glass clock UI: translucent top controls, footer plate, and settings drawer with layered highlights and shadows.
2. Mechanical split-flap animation: hour, minute, and second digits use a two-stage flip with hinge shading, back-face darkening, and compact second tiles.
3. 12/24-hour format toggle: switchable live from settings.
4. Dynamic wallpaper: online wallpapers by time-of-day with local fallback when loading fails.
5. Burn-in protection: alpha reduction plus periodic micro-shift offsets.
6. Gyroscope parallax: optional scene tilt and depth motion.
7. Apple-weather-inspired backdrop: animated sky glow, cloud veils, fog layers, and weather particles.
8. Weather modes: `SUNNY`, `CLOUDY`, `FOG`, `RAIN`, `SNOW`, `HAIL`, `WIND`, `DRIZZLE`, `BLIZZARD`.
9. Weather control: manual weather selection in settings or auto-random rotation every 8 hours.
10. Realtime cat layer: Filament + TextureView rendering with one cat actor constrained away from the main time area.
11. Weather-linked cat behavior: movement cadence and reactions change with the current weather mode.
12. Local Google Fonts bundle: clock fonts are packaged in-app and do not rely on Google Play Services.
13. Font options tuned for the flip layout: `Modern`, `Style 1`, `Digital`, `Pixel`, each with its own layout calibration.
14. Location display: prioritizes device location and falls back to HTTPS IP lookup.
15. Battery indicator: icon + numeric percentage in the top-right status cluster.
16. Cat sound button: plays audio from the bottom-right paw button.
17. Persistent settings: main toggles, weather mode, and font choice are stored with DataStore.
18. Chinese resources: `values-zh-rCN` included.

## Fonts

The current packaged fonts are local replacements based on Google Fonts:

- `Modern`: Oxanium
- `Style 1`: Aldrich
- `Digital`: Orbitron
- `Pixel`: Silkscreen

They are bundled under `app/src/main/res/font/` to keep the app usable on devices without reliable Google font-provider support.

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Filament (`filament-android`, `gltfio-android`)
- Android ViewModel + StateFlow
- DataStore Preferences
- Coil Compose

## Build & Run

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Version

- Current app version: `1.1`
