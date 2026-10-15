package com.example.mytime.ui

internal object LocationRecoveryPolicy {
    fun retryDelayMillis(attempt: Int): Long = when (attempt) {
        0 -> 30_000L
        1 -> 60_000L
        2 -> 120_000L
        else -> 300_000L
    }
    fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    fun isCacheFresh(savedAt: Long, now: Long): Boolean = savedAt > 0 && now - savedAt in 0..86_400_000L
}
