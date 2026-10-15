package com.example.mytime

import com.example.mytime.ui.updateAndGetEffect
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class StateTransitionTest {
    data class Alarm(val consumed: Boolean = false, val battery: Int = 0)

    @Test fun casRetryDoesNotConsumeAlarmBeforeCommit() {
        val state = MutableStateFlow(Alarm())
        val calculating = CountDownLatch(1)
        val competingWrite = CountDownLatch(1)
        val attempts = AtomicInteger()
        val contender = thread {
            assertTrue(calculating.await(5, TimeUnit.SECONDS))
            state.value = state.value.copy(battery = 80)
            competingWrite.countDown()
        }
        val shouldRing = state.updateAndGetEffect { previous ->
            if (attempts.incrementAndGet() == 1) {
                calculating.countDown()
                assertTrue(competingWrite.await(5, TimeUnit.SECONDS))
            }
            previous.copy(consumed = true) to !previous.consumed
        }
        contender.join(5000)
        assertTrue(attempts.get() >= 2)
        assertTrue(shouldRing)
        assertEquals(80, state.value.battery)
        assertTrue(state.value.consumed)
        assertFalse(state.updateAndGetEffect { it to !it.consumed })
    }
}
