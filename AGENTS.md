# meowtime project guidance

- This is a Kotlin / Jetpack Compose Android app. Inspect the existing Gradle versions before introducing APIs or dependencies.
- For coroutine, Flow, and concurrency work, read `.agents/skills/kotlin-concurrency-and-flow/SKILL.md` and its relevant reference. The skill is installed locally for this project.
- Keep async work owned by a visible lifecycle. Preserve coroutine cancellation and unregister Android listeners on cancellation. Bound blocking adapters and diagnostic queues.
- Keep the app name `meowtime`, with no Test suffix. The weather refresh action belongs in Settings, not on the main clock.
- Display only the city name in the location label, without GPS/IP or approximate-location suffixes.
- Keep location/forecast caches on transient failure, provide bounded retries, and request explicit consent before enabling public-IP city lookup.
- Store build outputs, temporary work, device logs, and backups under `/Users/zhcc/Downloads/Codex`; do not use Documents/iCloud for these files.
- Verify relevant changes with `./gradlew :app:assembleDebug :app:testDebugUnitTest --no-watch-fs`, and use the connected device for UI/location behavior. Do not claim that a short run proves a memory leak or long-running crash has been fixed.
