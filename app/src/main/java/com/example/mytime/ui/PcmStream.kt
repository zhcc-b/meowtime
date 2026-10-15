package com.example.mytime.ui

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Nonblocking writes keep cancellation bounded even if AudioFlinger stops consuming. */
internal suspend fun streamPcm(
    buffer: ShortArray,
    write: (ShortArray, Int, Int) -> Int,
    fill: (ShortArray) -> Unit
) {
    while (true) {
        currentCoroutineContext().ensureActive()
        fill(buffer)
        var offset = 0
        var stalledWrites = 0
        while (offset < buffer.size) {
            currentCoroutineContext().ensureActive()
            val written = write(buffer, offset, buffer.size - offset)
            when {
                written < 0 -> throw IOException("AudioTrack write failed: $written")
                written > buffer.size - offset -> throw IOException("Invalid AudioTrack write size: $written")
                written == 0 -> {
                    if (++stalledWrites >= 400) throw IOException("AudioTrack made no progress for 2 seconds")
                    delay(5)
                }
                else -> { offset += written; stalledWrites = 0 }
            }
        }
    }
}
