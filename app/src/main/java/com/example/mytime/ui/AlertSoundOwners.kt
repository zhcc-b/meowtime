package com.example.mytime.ui

internal enum class AlertKind { DAILY, TIMER }

/** Called by the main-thread lifecycle owner of the shared alarm sound. */
internal class AlertSoundOwners(private val start: () -> Unit, private val stop: () -> Unit) {
    private val active = mutableSetOf<AlertKind>()

    fun acquire(kind: AlertKind) {
        if (active.add(kind) && active.size == 1) start()
    }

    fun release(kind: AlertKind) {
        if (active.remove(kind) && active.isEmpty()) stop()
    }

    fun clear() {
        if (active.isNotEmpty()) { active.clear(); stop() }
    }
}
