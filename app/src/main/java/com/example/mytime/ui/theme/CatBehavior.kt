package com.example.mytime.ui.theme

import android.graphics.PointF
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.FilamentAsset
import kotlin.random.Random

// Personality driven by theme preset — controls idle/groom/doze probabilities and mid-walk pause chance.
internal enum class CatPersonality { CALM, ENERGETIC, NOCTURNAL, FOCUSED }

internal enum class CatState { IDLE, DOZING, GROOMING, STRETCHING, WALKING, PAUSED_WALK, REACTING }

internal data class CatBehaviorProfile(
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

internal data class ClipSet(
    val idle: Int,
    val walking: Int,
    val react: Int,
    val look: Int,
    val blink: Int
)

internal data class CatActor(
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
    var reactingLookAtClock: Boolean = false,
    // Doze / groom / stretch state
    var dozingDurationSec: Float = 0f,
    var dozingTargetSec: Float = 0f,
    var groomingDurationSec: Float = 0f,
    var groomingTargetSec: Float = 0f,
    var stretchingDurationSec: Float = 0f,
    var stretchingTargetSec: Float = 0f,
    var groomCooldownSec: Float = Random.nextFloat() * 30f + 20f,
    // Mid-walk pause
    var pausedWalkRemainSec: Float = 0f,
    // Walk motion — all walk params computed once at walk start so motion is deterministic
    var walkStartPx: PointF = PointF(),      // world position when this walk began
    var walkT: Float = 0f,                   // normalized walk progress [0, 1] — drives position
    var walkTotalTimeSec: Float = 0f,        // total walk duration = dist / speed
    var walkDirYaw: Float = 0f,              // facing angle (deg) pre-computed from start→target
    var pauseAtT: Float = -1f,              // walkT threshold at which to pause (−1 = no pause)
    var pauseDurationSec: Float = 0f         // how long to stay paused
)

