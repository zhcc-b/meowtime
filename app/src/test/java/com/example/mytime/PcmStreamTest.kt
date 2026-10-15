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
    @Test fun failedAudioServiceExitsOnFirstError() = runTest {
        var writes = 0
        try {
            streamPcm(ShortArray(4), { _, _, _ -> writes++; -6 }, {})
            fail("Expected failed track to exit")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message!!.contains("-6"))
        }
        assertEquals(1, writes)
    }
    @Test fun zeroProgressHasABoundedTimeout() = runTest {
        var writes = 0
        try {
            streamPcm(ShortArray(4), { _, _, _ -> writes++; 0 }, {})
            fail("Expected stalled track to exit")
        } catch (expected: java.io.IOException) {
            assertEquals(400, writes)
        }
    }
}
