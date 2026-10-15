package com.example.mytime

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytime.ui.ClockMode
import com.example.mytime.ui.ClockViewModel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerLifecycleTest {
    @Test fun stopwatchIncludesTimeWhileActivityTickerIsStopped() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val store = ViewModelStore()
        lateinit var model: ClockViewModel
        instrumentation.runOnMainSync {
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(application))[ClockViewModel::class.java]
        }
        try {
            Thread.sleep(500) // Let persisted preferences load before selecting the test mode.
            instrumentation.runOnMainSync {
                model.setClockMode(ClockMode.STOPWATCH)
                model.setAppActive(true)
                model.toggleModeRunning()
                model.setAppActive(false)
            }
            Thread.sleep(3200)
            instrumentation.runOnMainSync { model.setAppActive(true) }
            Thread.sleep(200)
            assertTrue("Stopped UI ticker must not pause elapsed time", model.uiState.value.stopwatchElapsedSeconds >= 3)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
