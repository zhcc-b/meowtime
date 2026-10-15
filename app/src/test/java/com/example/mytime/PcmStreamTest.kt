package com.example.mytime

import com.example.mytime.ui.streamPcm
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcmStreamTest {
    @Test fun cancellationEndsAStalledSessionBeforeRestart() = runTest {
        var oldWrites = 0
        var closed = false
        val old = launch {
            try { streamPcm(ShortArray(4), { _, _, _ -> oldWrites++; 0 }, {}) }
            finally { closed = true }
        }
        runCurrent()
        old.cancelAndJoin()
        assertTrue(closed)
        val writesAtStop = oldWrites
        val fresh = launch { streamPcm(ShortArray(4), { _, _, _ -> 0 }, {}) }
        runCurrent()
        advanceTimeBy(50)
        assertEquals(writesAtStop, oldWrites)
        fresh.cancelAndJoin()
    }
    @Test fun partialWritesKeepTheUnwrittenSamples() = runTest {
        val offsets = mutableListOf<Int>()
        val job = launch {
            streamPcm(ShortArray(4), { _, offset, size ->
                offsets += offset
                if (offsets.size <= 2) minOf(2, size) else 0
            }, {})
        }
        runCurrent()
        assertEquals(listOf(0, 2, 0), offsets)
        job.cancelAndJoin()
    }
}
