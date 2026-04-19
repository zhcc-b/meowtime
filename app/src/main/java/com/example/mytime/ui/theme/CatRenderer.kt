package com.example.mytime.ui.theme

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.opengl.Matrix
import android.view.Surface
import com.example.mytime.ui.ParticleWeather
import com.example.mytime.ui.ThemePreset
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal class CatRenderer(private val context: Context) {
    private val lock = Any()
    // Default to ~20fps to reduce battery use; adaptive logic can raise/lower it.
    val targetFrameIntervalNs = AtomicLong(50_000_000L)

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
    private var personality = CatPersonality.CALM
    private var lastFrameNs = 0L
    private var weather: ParticleWeather = ParticleWeather.SNOW

    private val cats = mutableListOf<CatActor>()

    private fun behaviorProfile(): CatBehaviorProfile {
        return when (weather) {
            ParticleWeather.SUNNY -> CatBehaviorProfile(
                walkDelayMinMs = 6_000L,
                walkDelayMaxMs = 22_000L,
                idleMinSec = 6f,
                idleMaxSec = 20f,
                walkSpeedPxPerSec = 110f,
                reactMoveSpeedPxPerSec = 42f,
                reactDurationSec = 1.2f,
                walkBobAmp = 0.028f,
                idleHeadTurnAmpDeg = 3f,
                reactHeadTurnAmpDeg = 4f
            )
            ParticleWeather.CLOUDY -> CatBehaviorProfile(
                walkDelayMinMs = 7_000L,
                walkDelayMaxMs = 24_000L,
                idleMinSec = 8f,
                idleMaxSec = 22f,
                walkSpeedPxPerSec = 95f,
                reactMoveSpeedPxPerSec = 38f,
                reactDurationSec = 1.3f,
                walkBobAmp = 0.024f,
                idleHeadTurnAmpDeg = 2.5f,
                reactHeadTurnAmpDeg = 3.5f
            )
            ParticleWeather.FOG -> CatBehaviorProfile(
                walkDelayMinMs = 9_000L,
                walkDelayMaxMs = 30_000L,
                idleMinSec = 10f,
                idleMaxSec = 28f,
                walkSpeedPxPerSec = 85f,
                reactMoveSpeedPxPerSec = 34f,
                reactDurationSec = 1.4f,
                walkBobAmp = 0.020f,
                idleHeadTurnAmpDeg = 2f,
                reactHeadTurnAmpDeg = 3f
            )
            ParticleWeather.RAIN -> CatBehaviorProfile(
                walkDelayMinMs = 5_000L,
                walkDelayMaxMs = 18_000L,
                idleMinSec = 5f,
                idleMaxSec = 16f,
                walkSpeedPxPerSec = 120f,
                reactMoveSpeedPxPerSec = 46f,
                reactDurationSec = 1.15f,
                walkBobAmp = 0.032f,
                idleHeadTurnAmpDeg = 2f,
                reactHeadTurnAmpDeg = 4f
            )
            ParticleWeather.DRIZZLE -> CatBehaviorProfile(
                walkDelayMinMs = 7_000L,
                walkDelayMaxMs = 24_000L,
                idleMinSec = 7f,
                idleMaxSec = 22f,
                walkSpeedPxPerSec = 100f,
                reactMoveSpeedPxPerSec = 40f,
                reactDurationSec = 1.25f,
                walkBobAmp = 0.025f,
                idleHeadTurnAmpDeg = 2.5f,
                reactHeadTurnAmpDeg = 3.5f
            )
            ParticleWeather.SNOW -> CatBehaviorProfile(
                walkDelayMinMs = 10_000L,
                walkDelayMaxMs = 40_000L,
                idleMinSec = 12f,
                idleMaxSec = 36f,
                walkSpeedPxPerSec = 75f,
                reactMoveSpeedPxPerSec = 30f,
                reactDurationSec = 1.5f,
                walkBobAmp = 0.018f,
                idleHeadTurnAmpDeg = 2f,
                reactHeadTurnAmpDeg = 3f
            )
            ParticleWeather.BLIZZARD -> CatBehaviorProfile(
                walkDelayMinMs = 14_000L,
                walkDelayMaxMs = 50_000L,
                idleMinSec = 16f,
                idleMaxSec = 50f,
                walkSpeedPxPerSec = 60f,
                reactMoveSpeedPxPerSec = 24f,
                reactDurationSec = 1.6f,
                walkBobAmp = 0.014f,
                idleHeadTurnAmpDeg = 1.5f,
                reactHeadTurnAmpDeg = 2.5f
            )
            ParticleWeather.HAIL -> CatBehaviorProfile(
                walkDelayMinMs = 4_000L,
                walkDelayMaxMs = 14_000L,
                idleMinSec = 4f,
                idleMaxSec = 14f,
                walkSpeedPxPerSec = 135f,
                reactMoveSpeedPxPerSec = 50f,
                reactDurationSec = 1.1f,
                walkBobAmp = 0.035f,
                idleHeadTurnAmpDeg = 2f,
                reactHeadTurnAmpDeg = 5f
            )
            ParticleWeather.WIND -> CatBehaviorProfile(
                walkDelayMinMs = 5_000L,
                walkDelayMaxMs = 16_000L,
                idleMinSec = 4f,
                idleMaxSec = 14f,
                walkSpeedPxPerSec = 125f,
                reactMoveSpeedPxPerSec = 48f,
                reactDurationSec = 1.1f,
                walkBobAmp = 0.030f,
                idleHeadTurnAmpDeg = 3f,
                reactHeadTurnAmpDeg = 5f
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

    fun setTheme(theme: ThemePreset) {
        synchronized(lock) {
            personality = when (theme) {
                ThemePreset.PLAYFUL -> CatPersonality.ENERGETIC
                ThemePreset.SERENE  -> CatPersonality.CALM
                ThemePreset.NIGHT   -> CatPersonality.NOCTURNAL
                ThemePreset.FOCUS   -> CatPersonality.FOCUSED
                ThemePreset.AUTO    -> CatPersonality.CALM
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
                .intensity(18_000f)
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
            // ── Handle pending external triggers ──────────────────────────
            if (cat.pendingLookAtClock) {
                cat.pendingLookAtClock = false
                if (cat.state == CatState.DOZING) {
                    startStretching(cat)          // Wake a dozing cat before it looks at the clock
                } else {
                    cat.previousState = cat.state
                    cat.state = CatState.REACTING
                    cat.stateElapsedSec = 0f
                    cat.reactingLookAtClock = true
                    cat.targetPx = PointF(timeCenter.x, cat.laneYPx)
                }
            } else if (cat.pendingReact) {
                cat.pendingReact = false
                // Dozing / grooming cats are less responsive to minute-tick reactions
                val ignoreChance = when (cat.state) {
                    CatState.DOZING                             -> 0.75f
                    CatState.GROOMING                           -> 0.50f
                    CatState.WALKING, CatState.PAUSED_WALK      -> 0.30f
                    else                                        -> 0f
                }
                if (Random.nextFloat() >= ignoreChance) {
                    cat.previousState = cat.state
                    cat.state = CatState.REACTING
                    cat.stateElapsedSec = 0f
                    cat.reactingLookAtClock = false
                }
            } else if (cat.pendingWalk) {
                cat.pendingWalk = false
                when (cat.state) {
                    CatState.DOZING                              -> startStretching(cat)  // Must stretch before walking
                    CatState.GROOMING, CatState.STRETCHING,
                    CatState.WALKING, CatState.PAUSED_WALK       -> Unit                 // Ignore; already active
                    else                                         -> startWalking(cat)
                }
            }

            cat.stateElapsedSec += dt
            cat.animTimeSec += dt

            when (cat.state) {
                CatState.IDLE        -> updateIdleState(cat, dt, behavior)
                CatState.DOZING      -> updateDozingState(cat, dt)
                CatState.GROOMING    -> updateGroomingState(cat, dt)
                CatState.STRETCHING  -> updateStretchingState(cat, dt)
                CatState.WALKING     -> updateWalkingState(cat, dt, behavior)
                CatState.PAUSED_WALK -> updatePausedWalkState(cat, dt)
                CatState.REACTING    -> updateReactingState(cat, dt, behavior, timeCenter)
            }

            if (cat.state != CatState.WALKING && cat.state != CatState.PAUSED_WALK) {
                cat.positionPx = PointF(cat.positionPx.x, cat.laneYPx)
            }

            applyAnimation(cat)
            applyTransform(cat)
        }

        // Adaptive framerate: ~24fps active, ~15fps light idle, 10fps deep idle/doze.
        val anyActive = cats.any { it.state == CatState.WALKING || it.state == CatState.REACTING }
        val allDeepIdle = cats.isNotEmpty() && cats.all {
            (it.state == CatState.IDLE && it.idleDurationSec > 8f) ||
            it.state == CatState.DOZING ||
            (it.state == CatState.GROOMING && it.groomingDurationSec > 2f)
        }
        targetFrameIntervalNs.set(
            when {
                anyActive   -> 41_666_666L
                allDeepIdle -> 100_000_000L
                else        -> 66_666_666L
            }
        )
    }

    // ── Per-state update functions ─────────────────────────────────────────────

    private fun updateIdleState(cat: CatActor, dt: Float, behavior: CatBehaviorProfile) {
        cat.idleDurationSec += dt
        cat.groomCooldownSec -= dt
        updateBlink(cat, dt)
        if (cat.idleDurationSec >= cat.idleTargetDurationSec) {
            val groomChance = when (personality) {
                CatPersonality.CALM      -> 0.38f
                CatPersonality.FOCUSED   -> 0.28f
                CatPersonality.NOCTURNAL -> 0.22f
                CatPersonality.ENERGETIC -> 0.10f
            }
            val dozeChance = when (personality) {
                CatPersonality.NOCTURNAL -> 0.28f
                CatPersonality.CALM      -> 0.18f
                CatPersonality.FOCUSED   -> 0.10f
                CatPersonality.ENERGETIC -> 0.04f
            }
            val roll = Random.nextFloat()
            when {
                cat.groomCooldownSec <= 0f && roll < groomChance          -> startGrooming(cat)
                roll < groomChance + dozeChance                           -> startDozing(cat)
                else                                                      -> cat.pendingWalk = true
            }
        }
    }

    private fun updateDozingState(cat: CatActor, dt: Float) {
        cat.dozingDurationSec += dt
        updateBlink(cat, dt)
        if (cat.dozingDurationSec >= cat.dozingTargetSec) {
            startStretching(cat)
        }
    }

    private fun updateGroomingState(cat: CatActor, dt: Float) {
        cat.groomingDurationSec += dt
        if (cat.groomingDurationSec >= cat.groomingTargetSec) {
            transitionToIdle(cat)
        }
    }

    private fun updateStretchingState(cat: CatActor, dt: Float) {
        cat.stretchingDurationSec += dt
        if (cat.stretchingDurationSec >= cat.stretchingTargetSec) {
            transitionToIdle(cat)
            cat.pendingWalk = true   // A good stretch always leads to a walk
        }
    }

    private fun updateWalkingState(cat: CatActor, dt: Float, behavior: CatBehaviorProfile) {
        cat.idleDurationSec = 0f
        if (cat.walkTotalTimeSec <= 0f) { transitionToIdle(cat); return }

        // Advance normalized progress.  walkT ∈ [0,1] drives position via smoothstep,
        // which guarantees the cat ALWAYS arrives at exactly t=1 with zero velocity.
        cat.walkT = (cat.walkT + dt / cat.walkTotalTimeSec).coerceIn(0f, 1f)

        // Check the pre-decided mid-walk pause (triggers exactly once, pauseAtT consumed on use)
        if (cat.pauseAtT in 0f..1f && cat.walkT >= cat.pauseAtT) {
            cat.pausedWalkRemainSec = cat.pauseDurationSec
            cat.pauseAtT = -1f  // consume so this never fires again for this walk
            cat.state = CatState.PAUSED_WALK
            cat.stateElapsedSec = 0f
            // walkT is intentionally NOT reset — position stays exactly where we stopped
            return
        }

        // Smoothstep lerp: 3t²-2t³ gives ease-in/ease-out and exact arrival at t=1
        val eased = smoothstep(cat.walkT)
        cat.positionPx = PointF(
            cat.walkStartPx.x + (cat.targetPx.x - cat.walkStartPx.x) * eased,
            cat.laneYPx
        )

        // Rotate toward the pre-computed walk direction (no per-frame atan2, no instability)
        cat.yawDeg = rotateToward(cat.yawDeg, cat.walkDirYaw, 220f * dt)

        if (cat.walkT >= 1f) transitionToIdle(cat)
    }

    private fun updatePausedWalkState(cat: CatActor, dt: Float) {
        cat.pausedWalkRemainSec -= dt
        if (cat.pausedWalkRemainSec <= 0f) {
            // Resume: walkT is preserved exactly where we stopped — no jerk, no restart
            cat.state = CatState.WALKING
        }
    }

    private fun updateReactingState(cat: CatActor, dt: Float, behavior: CatBehaviorProfile, timeCenter: PointF) {
        cat.idleDurationSec = 0f
        if (cat.reactingLookAtClock) {
            val dx = cat.targetPx.x - cat.positionPx.x
            val dy = cat.targetPx.y - cat.positionPx.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist > 14f) {
                val step = min(behavior.reactMoveSpeedPxPerSec * dt, dist)
                cat.positionPx = PointF(cat.positionPx.x + dx / dist * step, cat.laneYPx)
            }
            val targetYaw = Math.toDegrees(atan2(-dy, dx).toDouble()).toFloat()
            cat.yawDeg = rotateToward(cat.yawDeg, targetYaw, 180f * dt)
        }
        if (cat.stateElapsedSec > behavior.reactDurationSec) {
            if (cat.previousState == CatState.WALKING || cat.previousState == CatState.PAUSED_WALK) {
                startWalking(cat)
            } else {
                transitionToIdle(cat)
            }
            cat.reactingLookAtClock = false
        }
    }

    private fun updateBlink(cat: CatActor, dt: Float) {
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

    private fun generateWalkTarget(timeRect: RectF, current: PointF, laneY: Float): PointF {
        if (heightPx > widthPx) {
            val x = Random.nextFloat() * (widthPx * 0.82f - widthPx * 0.18f) + widthPx * 0.18f
            val y = laneY.coerceIn(heightPx * 0.88f, heightPx * 0.96f)
            return PointF(x, y)
        }
        val targetXRange = (widthPx * 0.06f)..(widthPx * 0.94f)
        val fixedY = laneY.coerceIn(heightPx * 0.94f, heightPx * 0.99f)

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
        val walkBob = if (cat.state == CatState.WALKING) {
            // Step frequency scales with walk speed so the body bob matches foot cadence
            val stepFreqRad = (behavior.walkSpeedPxPerSec / 50f) * 2f * PI.toFloat()  // ~2 steps/s at 100px/s
            behavior.walkBobAmp * sin(cat.animTimeSec * stepFreqRad)
        } else 0f
        // Breathing: idle=subtle, doze=slow deep, grooming=slightly elevated
        val breathe = when (cat.state) {
            CatState.IDLE     -> 1f + (0.012f * sin(cat.animTimeSec * 1.6f))
            CatState.DOZING   -> 1f + (0.025f * sin(cat.animTimeSec * 0.55f))
            CatState.GROOMING -> 1f + (0.008f * sin(cat.animTimeSec * 2.4f))
            else              -> 1f
        }
        // Weather micro-shiver: rain/drizzle = gentle, blizzard/hail = strong
        val shiver = when {
            (cat.state == CatState.IDLE || cat.state == CatState.DOZING) &&
                (weather == ParticleWeather.RAIN || weather == ParticleWeather.DRIZZLE) ->
                0.004f * sin(cat.animTimeSec * 9f)
            (cat.state == CatState.IDLE || cat.state == CatState.DOZING) &&
                (weather == ParticleWeather.BLIZZARD || weather == ParticleWeather.HAIL) ->
                0.007f * sin(cat.animTimeSec * 13f)
            else -> 0f
        }
        val blinkScaleY = if (cat.blinkDurationSec > 0f) {
            val t = (cat.blinkElapsedSec / cat.blinkDurationSec).coerceIn(0f, 1f)
            1f - 0.06f * pulseEase(t)
        } else {
            1f
        }
        // Head sway: idle=gentle, doze=very slow droop, paused_walk=curious look-around
        val idleHeadTurn = when (cat.state) {
            CatState.IDLE   -> behavior.idleHeadTurnAmpDeg * sin(cat.animTimeSec * 0.8f)
            CatState.DOZING -> behavior.idleHeadTurnAmpDeg * 0.3f * sin(cat.animTimeSec * 0.35f)
            else            -> 0f
        }
        val reactHeadTurn = when (cat.state) {
            CatState.REACTING    -> behavior.reactHeadTurnAmpDeg * sin(cat.stateElapsedSec * 5f)
            CatState.PAUSED_WALK -> behavior.idleHeadTurnAmpDeg * 1.5f * sin(cat.animTimeSec * 1.2f)
            else                 -> 0f
        }
        Matrix.translateM(transform, 0, x, y + walkBob + shiver, 0f)
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
            CatState.IDLE, CatState.DOZING -> cat.clips.idle
            CatState.GROOMING              -> cat.clips.react      // Groom reuses react animation, looped
            CatState.STRETCHING            -> cat.clips.react      // Stretch plays react once
            CatState.WALKING               -> cat.clips.walking
            CatState.PAUSED_WALK           -> cat.clips.look       // Look around while paused
            CatState.REACTING              -> if (cat.reactingLookAtClock) cat.clips.look else cat.clips.react
        }.coerceIn(0, animator.animationCount - 1)
        val clip = if ((cat.state == CatState.IDLE || cat.state == CatState.DOZING) && cat.blinkDurationSec > 0f) {
            cat.clips.blink.coerceIn(0, animator.animationCount - 1)
        } else {
            baseClip
        }

        val duration = runCatching { animator.getAnimationDuration(clip) }.getOrElse { 1f }.coerceAtLeast(0.1f)
        val time = when (cat.state) {
            CatState.REACTING -> {
                val raw = (cat.stateElapsedSec / duration).coerceIn(0f, 1f)
                easeInOut(raw) * duration
            }
            CatState.IDLE, CatState.DOZING -> {
                if (cat.blinkDurationSec > 0f) {
                    (cat.blinkElapsedSec / cat.blinkDurationSec).coerceIn(0f, 1f) * duration
                } else {
                    cat.animTimeSec % duration
                }
            }
            CatState.STRETCHING -> {
                // Play react clip once from start to end, then hold last frame
                (cat.stretchingDurationSec / cat.stretchingTargetSec.coerceAtLeast(0.1f)).coerceIn(0f, 1f) * duration
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

    private fun startDozing(cat: CatActor) {
        cat.state = CatState.DOZING
        cat.stateElapsedSec = 0f
        cat.dozingDurationSec = 0f
        cat.dozingTargetSec = when (personality) {
            CatPersonality.NOCTURNAL -> 60f + Random.nextFloat() * 120f
            CatPersonality.CALM      -> 40f + Random.nextFloat() *  80f
            CatPersonality.FOCUSED   -> 25f + Random.nextFloat() *  50f
            CatPersonality.ENERGETIC -> 15f + Random.nextFloat() *  25f
        }
    }

    private fun startGrooming(cat: CatActor) {
        cat.state = CatState.GROOMING
        cat.stateElapsedSec = 0f
        cat.groomingDurationSec = 0f
        cat.groomingTargetSec = 4f + Random.nextFloat() * 8f    // 4–12 second groom
        cat.groomCooldownSec  = 30f + Random.nextFloat() * 50f  // cooldown until next groom
    }

    private fun startStretching(cat: CatActor) {
        cat.state = CatState.STRETCHING
        cat.stateElapsedSec = 0f
        cat.stretchingDurationSec = 0f
        cat.stretchingTargetSec = 1.5f + Random.nextFloat() * 1.5f  // 1.5–3s stretch
        cat.dozingDurationSec = 0f
    }

    private fun startWalking(cat: CatActor) {
        val target = generateWalkTarget(forbiddenRect, cat.positionPx, cat.laneYPx)
        val dx = target.x - cat.positionPx.x
        val dy = target.y - cat.positionPx.y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        val speed = behaviorProfile().walkSpeedPxPerSec.coerceAtLeast(1f)

        cat.previousState = cat.state
        cat.state = CatState.WALKING
        cat.stateElapsedSec = 0f
        cat.idleDurationSec = 0f
        // Capture all walk parameters upfront — motion is computed deterministically from these
        cat.walkStartPx = PointF(cat.positionPx.x, cat.positionPx.y)
        cat.targetPx = target
        cat.walkT = 0f
        cat.walkTotalTimeSec = dist / speed
        // Pre-compute facing angle from the full start→target vector (avoids atan2 instability at destination)
        cat.walkDirYaw = Math.toDegrees(atan2(-dy, dx).toDouble()).toFloat()
        // Decide NOW whether to pause mid-walk (one roll, not per-frame polling)
        val pauseChance = when (personality) {
            CatPersonality.CALM      -> 0.55f
            CatPersonality.NOCTURNAL -> 0.48f
            CatPersonality.FOCUSED   -> 0.18f
            CatPersonality.ENERGETIC -> 0.08f
        }
        if (Random.nextFloat() < pauseChance) {
            cat.pauseAtT = 0.35f + Random.nextFloat() * 0.30f  // pause at 35–65% through walk
            cat.pauseDurationSec = 1.2f + Random.nextFloat() * 2.8f
        } else {
            cat.pauseAtT = -1f
            cat.pauseDurationSec = 0f
        }
    }

    private fun easeInOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return (0.5 - 0.5 * cos(x * PI)).toFloat()
    }

    // Standard cubic smoothstep: guarantees f(0)=0, f(1)=1, f'(0)=f'(1)=0
    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
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
