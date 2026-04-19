package com.example.mytime.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import com.example.mytime.ui.ParticleWeather
import com.example.mytime.ui.ThemePreset
import java.util.Calendar

internal class CatOverlayView(context: Context) : FrameLayout(context) {
    private val textureView = AccessibleTextureView(context)
    private val renderer = CatRenderer(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAttached = false
    private var isEnabledCats = true
    private var receiverRegistered = false
    private var loopRunning = false
    private var lastRenderNs = 0L
    private var renderSurface: Surface? = null

    private val walkRunnables: List<Runnable> = List(1) { catIndex ->
        Runnable {
            renderer.requestWalk(catIndex)
            scheduleRandomWalk(catIndex)
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttached || !isEnabledCats || !loopRunning) return
            val intervalNs = renderer.targetFrameIntervalNs.get()
            if (frameTimeNanos - lastRenderNs >= intervalNs) {
                renderer.render(frameTimeNanos)
                lastRenderNs = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK -> {
                    renderer.triggerRandomReaction()
                    val minute = Calendar.getInstance().get(Calendar.MINUTE)
                    if (minute == 0 || minute == 30) {
                        renderer.triggerLookAtTimeBoth()
                    }
                }

                Intent.ACTION_TIME_CHANGED -> renderer.triggerLookAtTimeBoth()
            }
        }
    }

    init {
        setWillNotDraw(true)
        isClickable = false
        isFocusable = false
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setupTextureView()
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun setupTextureView() {
        textureView.isOpaque = false
        textureView.isClickable = false
        textureView.isFocusable = false
        textureView.isFocusableInTouchMode = false
        textureView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            false
        }
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                renderSurface?.release()
                renderSurface = Surface(surfaceTexture)
                renderer.attachSurface(renderSurface!!, width, height)
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                renderer.onSurfaceResized(width, height)
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                renderSurface?.let { surface ->
                    renderer.detachSurface(surface)
                    surface.release()
                }
                renderSurface = null
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
        }
    }

    fun setForbiddenRect(rect: RectF) {
        renderer.setForbiddenRect(rect)
    }

    fun setWeather(weather: ParticleWeather) {
        renderer.setWeather(weather)
    }

    fun setTheme(theme: ThemePreset) {
        renderer.setTheme(theme)
    }

    fun setCatsEnabled(enabled: Boolean) {
        isEnabledCats = enabled
        renderer.setCatsEnabled(enabled)
        visibility = if (enabled) VISIBLE else GONE
        if (isAttached) {
            if (enabled) startLoop() else stopLoop()
        }
    }

    private fun startLoop() {
        if (loopRunning) return
        loopRunning = true
        lastRenderNs = 0L
        if (textureView.isAvailable && renderSurface == null) {
            val surfaceTexture = textureView.surfaceTexture
            if (surfaceTexture != null) {
                renderSurface = Surface(surfaceTexture)
                renderer.attachSurface(renderSurface!!, textureView.width, textureView.height)
            }
        }
        registerClockReceiver()
        Choreographer.getInstance().postFrameCallback(frameCallback)
        walkRunnables.forEachIndexed { index, _ -> scheduleRandomWalk(index) }
    }

    private fun stopLoop() {
        if (!loopRunning) return
        loopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        walkRunnables.forEach { mainHandler.removeCallbacks(it) }
        unregisterClockReceiver()
    }

    private fun scheduleRandomWalk(catIndex: Int) {
        if (!isAttached || !isEnabledCats) return
        val delayMs = renderer.nextWalkDelayMs()
        mainHandler.postDelayed(walkRunnables[catIndex], delayMs)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        if (isEnabledCats) {
            startLoop()
        }
    }

    override fun onDetachedFromWindow() {
        isAttached = false
        stopLoop()
        renderSurface?.let { surface ->
            renderer.detachSurface(surface)
            surface.release()
        }
        renderSurface = null
        renderer.destroy()
        super.onDetachedFromWindow()
    }

    private fun registerClockReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        context.registerReceiver(clockReceiver, filter)
        receiverRegistered = true
    }

    private fun unregisterClockReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(clockReceiver) }
        receiverRegistered = false
    }
}

private class AccessibleTextureView(context: Context) : TextureView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

