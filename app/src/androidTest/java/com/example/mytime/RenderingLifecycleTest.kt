package com.example.mytime

import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytime.ui.theme.CatOverlayView
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderingLifecycleTest {
    @Test fun surfaceSurvivesVisibilityRotationAndRecreation() {
        // MIUI blocks self-launch from an instrumented background process.
        // A shell launch first gives the app its normal foreground lifecycle.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("am start -W -n com.example.mytime/.MainActivity")
            .use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(3) { iteration ->
                scenario.onActivity { activity ->
                    val overlay = findOverlay(activity.window.decorView)
                    assertNotNull("Cat overlay must be part of the screen", overlay)
                    overlay!!.setCatsEnabled(false)
                }
                Thread.sleep(150)
                scenario.onActivity { activity ->
                    findOverlay(activity.window.decorView)!!.setCatsEnabled(true)
                    activity.requestedOrientation = if (iteration % 2 == 0) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                Thread.sleep(450)
                scenario.recreate()
            }
            scenario.onActivity { activity ->
                assertNotNull(findOverlay(activity.window.decorView))
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun findOverlay(view: View): CatOverlayView? {
        if (view is CatOverlayView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findOverlay(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
