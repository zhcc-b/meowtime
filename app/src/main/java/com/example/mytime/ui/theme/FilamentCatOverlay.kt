package com.example.mytime.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mytime.ui.ParticleWeather
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

@Composable
fun FilamentCatOverlay(
    enabled: Boolean,
    weather: ParticleWeather,
    forbiddenRect: RectF,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FilamentCatsOverlayView(context).apply {
                setCatsEnabled(enabled)
                setWeather(weather)
                setForbiddenRect(forbiddenRect)
            }
        },
        update = { view ->
            view.setCatsEnabled(enabled)
            view.setWeather(weather)
            view.setForbiddenRect(forbiddenRect)
        }
    )
}

private class FilamentCatsOverlayView(context: Context) : FrameLayout(context) {
    private val textureView = AccessibleTextureView(context)
    private val renderer = CatFilamentRenderer(context)
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

private enum class CatState { IDLE, WALKING, REACTING }

private data class CatBehaviorProfile(
    val walkDelayMinMs: Long,
    val walkDelayMaxMs: Long,
    val idleMinSec: Float,
    val idleMaxSec: Float,
    val walkSpeedPxPerSec: Float,
    val reactMoveSpeedPxPerSec: Float,
    val reactDurationSec: Float,
    val walkBobAmp: Float,
    val idleHeadTurnAmpDeg: Float,
    val reactHeadTurnAmpDeg: Float
)

private data class ClipSet(
    val idle: Int,
    val walking: Int,
    val react: Int,
    val look: Int,
    val blink: Int
)

private data class CatActor(
    val name: String,
    val asset: FilamentAsset,
    val animator: Animator?,
    val clips: ClipSet,
    val tint: FloatArray,
    val carrierEntity: Int,
    val anchorEntity: Int,
    val transformTargets: IntArray,
    val modelCenterX: Float,
    val modelCenterY: Float,
    val modelCenterZ: Float,
    val modelHalfY: Float,
    val modelScale: Float,
    val modelYawOffsetDeg: Float,
    var state: CatState = CatState.IDLE,
    var previousState: CatState = CatState.IDLE,
    var stateElapsedSec: Float = 0f,
    var animTimeSec: Float = 0f,
    var positionPx: PointF = PointF(),
    var targetPx: PointF = PointF(),
    var laneYPx: Float = 0f,
    var yawDeg: Float = 0f,
    var idleDurationSec: Float = 0f,
    var idleTargetDurationSec: Float = 10f,
    var blinkCooldownSec: Float = Random.nextDouble(3.0, 6.0).toFloat(),
    var blinkElapsedSec: Float = 0f,
    var blinkDurationSec: Float = 0f,
    var pendingWalk: Boolean = false,
    var pendingReact: Boolean = false,
    var pendingLookAtClock: Boolean = false,
    var reactingLookAtClock: Boolean = false
)

private class CatFilamentRenderer(private val context: Context) {
    private val lock = Any()
    val targetFrameIntervalNs = AtomicLong(33_333_333L)

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var outputSurface: Surface? = null
    private var pendingSurface: Surface? = null
    private var lightEntity: Int = 0

    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var materialProvider: MaterialProvider? = null

    private var widthPx = 1
    private var heightPx = 1
    private var pendingWidthPx = 0
    private var pendingHeightPx = 0
    private var orthoHalfWidth = 3.2f
    private var orthoHalfHeight = 5.6f
    private var forbiddenRect = RectF()
    private var catsEnabled = true
    private var lastFrameNs = 0L
    private var weather: ParticleWeather = ParticleWeather.SNOW

    private val cats = mutableListOf<CatActor>()

    private fun behaviorProfile(): CatBehaviorProfile {
        return when (weather) {
            ParticleWeather.SUNNY -> CatBehaviorProfile(
                walkDelayMinMs = 4_000L,
                walkDelayMaxMs = 18_000L,
                idleMinSec = 5f,
                idleMaxSec = 18f,
                walkSpeedPxPerSec = 175f,
                reactMoveSpeedPxPerSec = 60f,
                reactDurationSec = 1.0f,
                walkBobAmp = 0.045f,
                idleHeadTurnAmpDeg = 5f,
                reactHeadTurnAmpDeg = 7f
            )
            ParticleWeather.CLOUDY -> CatBehaviorProfile(
                walkDelayMinMs = 5_500L,
                walkDelayMaxMs = 20_000L,
                idleMinSec = 7f,
                idleMaxSec = 20f,
                walkSpeedPxPerSec = 155f,
                reactMoveSpeedPxPerSec = 54f,
                reactDurationSec = 1.05f,
                walkBobAmp = 0.04f,
                idleHeadTurnAmpDeg = 4f,
                reactHeadTurnAmpDeg = 6f
            )
            ParticleWeather.FOG -> CatBehaviorProfile(
                walkDelayMinMs = 7_000L,
                walkDelayMaxMs = 24_000L,
                idleMinSec = 8f,
                idleMaxSec = 24f,
                walkSpeedPxPerSec = 135f,
                reactMoveSpeedPxPerSec = 48f,
                reactDurationSec = 1.15f,
                walkBobAmp = 0.035f,
                idleHeadTurnAmpDeg = 4f,
                reactHeadTurnAmpDeg = 5f
            )
            ParticleWeather.RAIN -> CatBehaviorProfile(
                walkDelayMinMs = 2_000L,
                walkDelayMaxMs = 14_000L,
                idleMinSec = 3f,
                idleMaxSec = 14f,
                walkSpeedPxPerSec = 220f,
                reactMoveSpeedPxPerSec = 70f,
                reactDurationSec = 1.0f,
                walkBobAmp = 0.055f,
                idleHeadTurnAmpDeg = 3f,
                reactHeadTurnAmpDeg = 7f
            )
            ParticleWeather.DRIZZLE -> CatBehaviorProfile(
                walkDelayMinMs = 4_000L,
                walkDelayMaxMs = 20_000L,
                idleMinSec = 5f,
                idleMaxSec = 20f,
                walkSpeedPxPerSec = 185f,
                reactMoveSpeedPxPerSec = 60f,
                reactDurationSec = 1.0f,
                walkBobAmp = 0.05f,
                idleHeadTurnAmpDeg = 4f,
                reactHeadTurnAmpDeg = 6f
            )
            ParticleWeather.SNOW -> CatBehaviorProfile(
                walkDelayMinMs = 8_000L,
                walkDelayMaxMs = 36_000L,
                idleMinSec = 10f,
                idleMaxSec = 32f,
                walkSpeedPxPerSec = 125f,
                reactMoveSpeedPxPerSec = 45f,
                reactDurationSec = 1.2f,
                walkBobAmp = 0.035f,
                idleHeadTurnAmpDeg = 5f,
                reactHeadTurnAmpDeg = 6f
            )
            ParticleWeather.BLIZZARD -> CatBehaviorProfile(
                walkDelayMinMs = 10_000L,
                walkDelayMaxMs = 45_000L,
                idleMinSec = 14f,
                idleMaxSec = 45f,
                walkSpeedPxPerSec = 105f,
                reactMoveSpeedPxPerSec = 38f,
                reactDurationSec = 1.35f,
                walkBobAmp = 0.03f,
                idleHeadTurnAmpDeg = 6f,
                reactHeadTurnAmpDeg = 5f
            )
            ParticleWeather.HAIL -> CatBehaviorProfile(
                walkDelayMinMs = 1_500L,
                walkDelayMaxMs = 9_000L,
                idleMinSec = 2f,
                idleMaxSec = 10f,
                walkSpeedPxPerSec = 260f,
                reactMoveSpeedPxPerSec = 78f,
                reactDurationSec = 0.9f,
                walkBobAmp = 0.065f,
                idleHeadTurnAmpDeg = 3f,
                reactHeadTurnAmpDeg = 8f
            )
            ParticleWeather.WIND -> CatBehaviorProfile(
                walkDelayMinMs = 2_000L,
                walkDelayMaxMs = 11_000L,
                idleMinSec = 3f,
                idleMaxSec = 12f,
                walkSpeedPxPerSec = 245f,
                reactMoveSpeedPxPerSec = 72f,
                reactDurationSec = 0.95f,
                walkBobAmp = 0.06f,
                idleHeadTurnAmpDeg = 7f,
                reactHeadTurnAmpDeg = 9f
            )
        }
    }

    fun nextWalkDelayMs(): Long {
        synchronized(lock) {
            val profile = behaviorProfile()
            return Random.nextLong(profile.walkDelayMinMs, profile.walkDelayMaxMs + 1)
        }
    }

    fun setWeather(weather: ParticleWeather) {
        synchronized(lock) {
            if (this.weather == weather) return
            this.weather = weather
            cats.forEach { cat ->
                if (cat.state == CatState.IDLE) {
                    cat.idleTargetDurationSec = randomIdleTargetSec()
                }
            }
        }
    }

    private fun randomIdleTargetSec(): Float {
        val profile = behaviorProfile()
        return profile.idleMinSec + Random.nextFloat() * (profile.idleMaxSec - profile.idleMinSec)
    }

    private fun ensureEngine() {
        if (engine != null) return
        Utils.init()
        val eng = Engine.create()
        engine = eng

        renderer = eng.createRenderer()
        scene = eng.createScene()
        view = eng.createView()
        camera = eng.createCamera(EntityManager.get().create())

        val cam = camera ?: return
        val v = view ?: return
        val r = renderer ?: return
        v.camera = cam
        v.scene = scene
        v.blendMode = View.BlendMode.TRANSLUCENT
        v.isPostProcessingEnabled = false
        r.setClearOptions(Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        })

        val provider = UbershaderProvider(eng)
        materialProvider = provider
        assetLoader = AssetLoader(eng, provider, EntityManager.get())
        resourceLoader = ResourceLoader(eng)
        lightEntity = EntityManager.get().create().also { light ->
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 0.98f, 0.95f)
                .intensity(32_000f)
                .direction(0.5f, -1.0f, -0.8f)
                .castShadows(false)
                .build(eng, light)
            scene?.addEntity(light)
        }

        loadCats()
    }

    private fun updateCameraProjection(width: Int, height: Int) {
        widthPx = width.coerceAtLeast(1)
        heightPx = height.coerceAtLeast(1)

        val aspect = widthPx.toDouble() / heightPx.toDouble()
        orthoHalfWidth = 3.2f
        orthoHalfHeight = (orthoHalfWidth / aspect).toFloat()

        camera?.setProjection(
            Camera.Projection.ORTHO,
            -orthoHalfWidth.toDouble(),
            orthoHalfWidth.toDouble(),
            -orthoHalfHeight.toDouble(),
            orthoHalfHeight.toDouble(),
            0.1,
            25.0
        )
        camera?.lookAt(
            0.0, 0.0, 8.0,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0
        )
        view?.viewport = Viewport(0, 0, widthPx, heightPx)
        initializeCatPositionsIfNeeded()
    }

    fun render(frameTimeNanos: Long) {
        ensureEngine()
        val eng = engine ?: return
        val r = renderer ?: return
        val v = view ?: return

        syncSurfaceAndSwapChain(eng)
        val chain = swapChain ?: return

        val nowNs = if (frameTimeNanos > 0L) frameTimeNanos else System.nanoTime()
        if (lastFrameNs == 0L) lastFrameNs = nowNs
        val dtSec = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        synchronized(lock) {
            updateCats(dtSec)
        }

        if (r.beginFrame(chain, nowNs)) {
            r.render(v)
            r.endFrame()
        }
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        synchronized(lock) {
            pendingSurface = surface
            pendingWidthPx = width
            pendingHeightPx = height
        }
    }

    fun onSurfaceResized(width: Int, height: Int) {
        synchronized(lock) {
            pendingWidthPx = width
            pendingHeightPx = height
        }
    }

    fun detachSurface(surface: Surface) {
        synchronized(lock) {
            if (pendingSurface === surface) {
                pendingSurface = null
            }
        }
    }

    fun setForbiddenRect(rect: RectF) {
        synchronized(lock) { forbiddenRect = RectF(rect) }
    }

    fun setCatsEnabled(enabled: Boolean) {
        synchronized(lock) { catsEnabled = enabled }
    }

    fun requestWalk(catIndex: Int) {
        synchronized(lock) {
            cats.getOrNull(catIndex)?.pendingWalk = true
        }
    }

    fun triggerRandomReaction() {
        synchronized(lock) {
            if (cats.isEmpty()) return
            cats[Random.nextInt(cats.size)].pendingReact = true
        }
    }

    fun triggerLookAtTimeBoth() {
        synchronized(lock) { cats.forEach { it.pendingLookAtClock = true } }
    }

    private fun loadCats() {
        val scn = scene ?: return
        val loader = assetLoader ?: return
        val resLoader = resourceLoader ?: return
        val tm = engine?.transformManager ?: return

        val definitions = listOf(
            Triple("bobcat.glb", "bobcat", floatArrayOf(0.690f, 0.769f, 0.871f, 1f))  // #B0C4DE
        )

        definitions.forEachIndexed { index, (fileName, name, tint) ->
            val asset = loadGlbAsset("models/$fileName", loader, resLoader) ?: return@forEachIndexed
            val animator = asset.instance?.animator
            val clips = resolveClips(animator)
            tintAssetMaterials(asset, tint)
            scn.addEntities(asset.entities)

            val carrierEntity = EntityManager.get().create()
            tm.create(carrierEntity)
            val carrierInst = tm.getInstance(carrierEntity)

            val transformTargets = asset.entities.filter { tm.hasComponent(it) }.toIntArray()
            val preferredRoot = asset.root
            val anchorEntity = if (transformTargets.contains(preferredRoot)) {
                preferredRoot
            } else {
                transformTargets.firstOrNull() ?: preferredRoot
            }

            // Preserve model / skin hierarchy. Only parent the scene root to our carrier.
            val rootInst = tm.getInstance(asset.root)
            if (rootInst != 0) {
                tm.setParent(rootInst, carrierInst)
            }

            val box = asset.boundingBox
            val center = box.center
            val halfExtent = box.halfExtent
            val modelScale = if (heightPx > widthPx) 0.82f else 0.5f
            val start = initialPoint(index)
            cats += CatActor(
                name = name,
                asset = asset,
                animator = animator,
                clips = clips,
                tint = tint,
                carrierEntity = carrierEntity,
                anchorEntity = anchorEntity,
                transformTargets = transformTargets,
                modelCenterX = center[0],
                modelCenterY = center[1],
                modelCenterZ = center[2],
                modelHalfY = halfExtent[1],
                modelScale = modelScale,
                modelYawOffsetDeg = 90f,
                positionPx = PointF(start.x, start.y),
                targetPx = PointF(start.x, start.y),
                laneYPx = start.y,
                idleTargetDurationSec = randomIdleTargetSec()
            )
        }
    }

    private fun loadGlbAsset(path: String, loader: AssetLoader, resLoader: ResourceLoader): FilamentAsset? {
        val bytes = runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull() ?: return null
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes).flip()
        val asset = loader.createAsset(buffer) ?: return null
        resLoader.loadResources(asset)
        asset.releaseSourceData()
        return asset
    }

    private fun tintAssetMaterials(asset: FilamentAsset, tint: FloatArray) {
        val eng = engine ?: return
        val rm = eng.renderableManager
        asset.entities.forEach { entity ->
            if (!rm.hasComponent(entity)) return@forEach
            val inst = rm.getInstance(entity)
            rm.setCulling(inst, false)
            val primCount = rm.getPrimitiveCount(inst)
            for (i in 0 until primCount) {
                val material = rm.getMaterialInstanceAt(inst, i)
                setBaseColor(material, tint)
            }
        }
    }

    private fun setBaseColor(material: MaterialInstance, tint: FloatArray) {
        // Blend tint toward white so source texture remains visible.
        val blended = floatArrayOf(
            0.7f + tint[0] * 0.3f,
            0.7f + tint[1] * 0.3f,
            0.7f + tint[2] * 0.3f,
            1f
        )
        runCatching { material.setParameter("baseColorFactor", blended[0], blended[1], blended[2], blended[3]) }
        runCatching { material.setParameter("emissiveFactor", tint[0] * 0.22f, tint[1] * 0.22f, tint[2] * 0.22f) }
        runCatching { material.setParameter("metallicFactor", 0f) }
        runCatching { material.setParameter("roughnessFactor", 0.92f) }
    }

    private fun resolveClips(animator: Animator?): ClipSet {
        if (animator == null || animator.animationCount <= 0) return ClipSet(-1, -1, -1, -1, -1)
        val names = (0 until animator.animationCount).associateWith { idx ->
            runCatching { animator.getAnimationName(idx).lowercase() }.getOrDefault("")
        }
        val idle = pickClip(names, listOf("idle", "sit", "breath", "rest", "dance"), listOf("death", "die"))
        val walk = pickClip(names, listOf("walk", "run", "move"))
        val react = pickClip(
            names,
            listOf("react", "twitch", "ear", "blink", "meow", "yes", "no", "hit", "headbutt", "bite"),
            listOf("death", "die")
        )
        val look = pickClip(names, listOf("look", "up", "head", "turn", "yes", "no"), listOf("death", "die"))
        val blink = pickClip(names, listOf("blink", "yes", "no"), listOf("death", "die"))
        return ClipSet(
            idle = if (idle >= 0) idle else 0,
            walking = if (walk >= 0) walk else min(1, animator.animationCount - 1),
            react = if (react >= 0) react else if (idle >= 0) idle else 0,
            look = if (look >= 0) look else if (react >= 0) react else if (idle >= 0) idle else 0,
            blink = if (blink >= 0) blink else if (react >= 0) react else if (look >= 0) look else if (idle >= 0) idle else 0
        )
    }

    private fun pickClip(names: Map<Int, String>, keys: List<String>, exclude: List<String> = emptyList()): Int {
        return names.entries.firstOrNull { (_, name) ->
            keys.any { key -> name.contains(key) } && exclude.none { bad -> name.contains(bad) }
        }?.key ?: -1
    }

    private fun updateCats(dt: Float) {
        if (!catsEnabled) {
            targetFrameIntervalNs.set(100_000_000L)
            return
        }
        val behavior = behaviorProfile()
        val timeCenter = PointF(forbiddenRect.centerX(), forbiddenRect.centerY())

        cats.forEach { cat ->
            if (cat.pendingLookAtClock) {
                cat.pendingLookAtClock = false
                cat.previousState = cat.state
                cat.state = CatState.REACTING
                cat.stateElapsedSec = 0f
                cat.reactingLookAtClock = true
                cat.targetPx = PointF(timeCenter.x, cat.laneYPx)
            } else if (cat.pendingReact) {
                cat.pendingReact = false
                cat.previousState = cat.state
                cat.state = CatState.REACTING
                cat.stateElapsedSec = 0f
                cat.reactingLookAtClock = false
            } else if (cat.pendingWalk) {
                cat.pendingWalk = false
                cat.previousState = cat.state
                cat.state = CatState.WALKING
                cat.stateElapsedSec = 0f
                cat.targetPx = generateWalkTarget(forbiddenRect, cat.positionPx, cat.laneYPx)
            }

            cat.stateElapsedSec += dt
            cat.animTimeSec += dt

            when (cat.state) {
                CatState.IDLE -> {
                    cat.idleDurationSec += dt
                    cat.positionPx = PointF(cat.positionPx.x, cat.laneYPx)
                    if (cat.idleDurationSec >= cat.idleTargetDurationSec) {
                        cat.pendingWalk = true
                    }
                    cat.blinkCooldownSec -= dt
                    if (cat.blinkDurationSec > 0f) {
                        cat.blinkElapsedSec += dt
                        if (cat.blinkElapsedSec >= cat.blinkDurationSec) {
                            cat.blinkDurationSec = 0f
                            cat.blinkElapsedSec = 0f
                            cat.blinkCooldownSec = Random.nextDouble(3.0, 6.0).toFloat()
                        }
                    } else if (cat.blinkCooldownSec <= 0f) {
                        cat.blinkDurationSec = Random.nextDouble(0.10, 0.16).toFloat()
                        cat.blinkElapsedSec = 0f
                    }
                }

                CatState.WALKING -> {
                    cat.idleDurationSec = 0f
                    val dx = cat.targetPx.x - cat.positionPx.x
                    val dy = cat.targetPx.y - cat.positionPx.y
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist < 8f) {
                        transitionToIdle(cat)
                    } else {
                        val step = min(behavior.walkSpeedPxPerSec * dt, dist)
                        cat.positionPx = PointF(
                            cat.positionPx.x + dx / dist * step,
                            cat.laneYPx
                        )
                        val targetYaw = Math.toDegrees(atan2(-dy, dx).toDouble()).toFloat()
                        cat.yawDeg = rotateToward(cat.yawDeg, targetYaw, 260f * dt)
                    }
                }

                CatState.REACTING -> {
                    cat.idleDurationSec = 0f
                    if (cat.reactingLookAtClock) {
                        val dx = cat.targetPx.x - cat.positionPx.x
                        val dy = cat.targetPx.y - cat.positionPx.y
                        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (dist > 14f) {
                            val step = min(behavior.reactMoveSpeedPxPerSec * dt, dist)
                            cat.positionPx = PointF(
                                cat.positionPx.x + dx / dist * step,
                                cat.laneYPx
                            )
                        }
                        val targetYaw = Math.toDegrees(atan2(-dy, dx).toDouble()).toFloat()
                        cat.yawDeg = rotateToward(cat.yawDeg, targetYaw, 180f * dt)
                    }
                    if (cat.stateElapsedSec > behavior.reactDurationSec) {
                        if (cat.previousState == CatState.WALKING) {
                            cat.state = CatState.WALKING
                            cat.stateElapsedSec = 0f
                        } else {
                            transitionToIdle(cat)
                        }
                        cat.reactingLookAtClock = false
                    }
                }
            }
            if (cat.state != CatState.WALKING) {
                cat.positionPx = PointF(cat.positionPx.x, cat.laneYPx)
            }

            applyAnimation(cat)
            applyTransform(cat)
        }

        val allIdleLongEnough = cats.isNotEmpty() && cats.all { it.state == CatState.IDLE && it.idleDurationSec > 10f }
        targetFrameIntervalNs.set(if (allIdleLongEnough) 100_000_000L else 33_333_333L)
    }

    private fun generateWalkTarget(timeRect: RectF, current: PointF, laneY: Float): PointF {
        if (heightPx > widthPx) {
            val x = Random.nextFloat() * (widthPx * 0.68f - widthPx * 0.32f) + widthPx * 0.32f
            val y = laneY.coerceIn(heightPx * 0.90f, heightPx * 0.965f)
            return PointF(x, y)
        }
        val sideMid = widthPx * 0.5f
        val targetXRange = if (current.x < sideMid) {
            (widthPx * 0.62f)..(widthPx * 0.90f)
        } else {
            (widthPx * 0.10f)..(widthPx * 0.38f)
        }
        val fixedY = laneY.coerceIn(heightPx * 0.955f, heightPx * 0.995f)

        if (timeRect.width() <= 0f || timeRect.height() <= 0f) {
            return PointF(
                Random.nextFloat() * (targetXRange.endInclusive - targetXRange.start) + targetXRange.start,
                fixedY
            )
        }
        val cx = timeRect.centerX()
        val cy = timeRect.centerY()
        val rejectRadius = timeRect.width() * 0.75f
        repeat(10) {
            val p = PointF(
                Random.nextFloat() * (targetXRange.endInclusive - targetXRange.start) + targetXRange.start,
                fixedY
            )
            val dist = hypot((p.x - cx).toDouble(), (p.y - cy).toDouble()).toFloat()
            if (dist >= rejectRadius) return p
        }
        return PointF(
            Random.nextFloat() * (targetXRange.endInclusive - targetXRange.start) + targetXRange.start,
            fixedY
        )
    }

    private fun applyTransform(cat: CatActor) {
        val eng = engine ?: return
        val tm = eng.transformManager
        val behavior = behaviorProfile()

        val x = ((cat.positionPx.x / widthPx) - 0.5f) * 2f * orthoHalfWidth
        // Ground the cat by model feet instead of pivot so it always stays near screen bottom.
        val minModelY = cat.modelCenterY - cat.modelHalfY
        val groundMargin = orthoHalfHeight * 0.04f
        val y = (-orthoHalfHeight + groundMargin) - (cat.modelScale * minModelY)

        val transform = FloatArray(16)
        Matrix.setIdentityM(transform, 0)
        val walkBob = if (cat.state == CatState.WALKING) behavior.walkBobAmp * sin(cat.animTimeSec * 10f) else 0f
        val breathe = if (cat.state == CatState.IDLE) 1f + (0.02f * sin(cat.animTimeSec * 2.1f)) else 1f
        val blinkScaleY = if (cat.blinkDurationSec > 0f) {
            val t = (cat.blinkElapsedSec / cat.blinkDurationSec).coerceIn(0f, 1f)
            1f - 0.06f * pulseEase(t)
        } else {
            1f
        }
        val idleHeadTurn = if (cat.state == CatState.IDLE) behavior.idleHeadTurnAmpDeg * sin(cat.animTimeSec * 0.8f) else 0f
        val reactHeadTurn = if (cat.state == CatState.REACTING) behavior.reactHeadTurnAmpDeg * sin(cat.stateElapsedSec * 5f) else 0f
        Matrix.translateM(transform, 0, x, y + walkBob, 0f)
        Matrix.rotateM(transform, 0, cat.yawDeg + cat.modelYawOffsetDeg + idleHeadTurn + reactHeadTurn, 0f, 1f, 0f)
        Matrix.scaleM(transform, 0, cat.modelScale, cat.modelScale * breathe * blinkScaleY, cat.modelScale)
        // Keep the source model's local pivot unchanged; vertical placement is controlled by world y offset.

        val carrierInst = tm.getInstance(cat.carrierEntity)
        if (carrierInst != 0) {
            tm.setTransform(carrierInst, transform)
        }
    }

    private fun applyAnimation(cat: CatActor) {
        val animator = cat.animator ?: return
        if (animator.animationCount <= 0) return
        val baseClip = when (cat.state) {
            CatState.IDLE -> cat.clips.idle
            CatState.WALKING -> cat.clips.walking
            CatState.REACTING -> if (cat.reactingLookAtClock) cat.clips.look else cat.clips.react
        }.coerceIn(0, animator.animationCount - 1)
        val clip = if (cat.state == CatState.IDLE && cat.blinkDurationSec > 0f) {
            cat.clips.blink.coerceIn(0, animator.animationCount - 1)
        } else {
            baseClip
        }

        val duration = runCatching { animator.getAnimationDuration(clip) }.getOrElse { 1f }.coerceAtLeast(0.1f)
        val time = when {
            cat.state == CatState.REACTING -> {
                val raw = (cat.stateElapsedSec / duration).coerceIn(0f, 1f)
                easeInOut(raw) * duration
            }
            cat.state == CatState.IDLE && cat.blinkDurationSec > 0f -> {
                val raw = (cat.blinkElapsedSec / cat.blinkDurationSec).coerceIn(0f, 1f)
                raw * duration
            }
            else -> cat.animTimeSec % duration
        }
        animator.applyAnimation(clip, time)
        animator.updateBoneMatrices()
    }

    private fun transitionToIdle(cat: CatActor) {
        cat.state = CatState.IDLE
        cat.stateElapsedSec = 0f
        cat.idleDurationSec = 0f
        cat.idleTargetDurationSec = randomIdleTargetSec()
    }

    private fun easeInOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return (0.5 - 0.5 * cos(x * PI)).toFloat()
    }

    private fun pulseEase(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return sin(x * PI).toFloat()
    }

    private fun rotateToward(currentDeg: Float, targetDeg: Float, maxStepDeg: Float): Float {
        val delta = (((targetDeg - currentDeg) + 540f) % 360f) - 180f
        val step = delta.coerceIn(-maxStepDeg, maxStepDeg)
        return currentDeg + step
    }

    private fun syncSurfaceAndSwapChain(eng: Engine) {
        val nextSurface: Surface?
        val nextWidth: Int
        val nextHeight: Int
        synchronized(lock) {
            nextSurface = pendingSurface
            nextWidth = pendingWidthPx
            nextHeight = pendingHeightPx
        }

        if (nextWidth > 0 && nextHeight > 0 && (nextWidth != widthPx || nextHeight != heightPx)) {
            updateCameraProjection(nextWidth, nextHeight)
        }

        if (nextSurface === outputSurface) return

        swapChain?.let {
            eng.destroySwapChain(it)
            swapChain = null
        }

        outputSurface = nextSurface
        if (nextSurface != null && nextSurface.isValid) {
            swapChain = eng.createSwapChain(nextSurface)
        }
    }

    private fun initialPoint(index: Int): PointF {
        val baseY = if (heightPx > widthPx) heightPx * 0.955f else heightPx * 0.992f
        val x = if (heightPx > widthPx) {
            widthPx * 0.5f
        } else if (index == 0) {
            widthPx * 0.08f
        } else {
            widthPx * 0.78f
        }
        return PointF(x, baseY)
    }

    private fun initializeCatPositionsIfNeeded() {
        cats.forEachIndexed { index, cat ->
            if (cat.positionPx.x <= 2f && cat.positionPx.y <= 2f) {
                val start = initialPoint(index)
                cat.positionPx = PointF(start.x, start.y)
                cat.targetPx = PointF(start.x, start.y)
            }
        }
    }

    fun destroy() {
        val eng = engine ?: return
        val scn = scene
        cats.forEach { cat ->
            scn?.removeEntities(cat.asset.entities)
            assetLoader?.destroyAsset(cat.asset)
            runCatching {
                if (eng.transformManager.hasComponent(cat.carrierEntity)) {
                    eng.transformManager.destroy(cat.carrierEntity)
                }
            }
            EntityManager.get().destroy(cat.carrierEntity)
        }
        cats.clear()

        if (lightEntity != 0) {
            scn?.removeEntity(lightEntity)
            runCatching { eng.destroyEntity(lightEntity) }
            EntityManager.get().destroy(lightEntity)
            lightEntity = 0
        }

        resourceLoader?.destroy()
        assetLoader?.destroy()
        materialProvider?.destroyMaterials()

        swapChain?.let { eng.destroySwapChain(it) }
        view?.let { eng.destroyView(it) }
        renderer?.let { eng.destroyRenderer(it) }
        scene?.let { eng.destroyScene(it) }
        camera?.let {
            eng.destroyCameraComponent(it.entity)
            EntityManager.get().destroy(it.entity)
        }

        eng.destroy()
        swapChain = null
        outputSurface = null
        pendingSurface = null
        camera = null
        view = null
        scene = null
        renderer = null
        assetLoader = null
        resourceLoader = null
        materialProvider = null
        engine = null
    }
}
