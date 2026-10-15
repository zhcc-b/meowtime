package com.example.mytime.ui

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
        while (offset < buffer.size) {
            currentCoroutineContext().ensureActive()
            val written = write(buffer, offset, buffer.size - offset)
            if (written > 0) offset += written else delay(5)
        }
    }
}
