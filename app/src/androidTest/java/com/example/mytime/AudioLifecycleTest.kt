package com.example.mytime

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytime.ui.AlarmTonePlayer
import com.example.mytime.ui.WhiteNoisePlayer
import com.example.mytime.ui.SleepSoundMode
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AudioLifecycleTest {
    @Test fun nativeTracksCanBeCancelledAndRestarted() = runTest {
        val alarm = AlarmTonePlayer()
        val sleep = WhiteNoisePlayer()
        repeat(3) {
            for (play in listOf<suspend () -> Unit>({ alarm.play() }, { sleep.play(SleepSoundMode.RAIN) }, { sleep.play(SleepSoundMode.WHITE_NOISE) })) {
                val job = launch { play() }
                runCurrent()
                // Wait in real time for the Android audio service, outside the virtual test clock.
                withContext(Dispatchers.IO) { delay(150) }
                job.cancelAndJoin()
            }
        }
    }
}
