package dev.denza.apps.feature.mirrors

import dev.denza.apps.feature.cluster.CameraRuntimePhase

/**
 * Decides when a window-state accessibility event must trigger the emergency
 * camera release. The guard is armed only while our AVC session is live
 * (STARTING or READY): a stock camera window changing state at that moment is
 * the fast-switch signature that crashes stock `com.byd.avc` when its mode
 * transition finds our surface attached (see dishare-api-notes, 2026-07-24).
 *
 * The shown side's own window events fire ~30-71 ms after its window is
 * added, while our session only reaches STARTING 300+ ms later via polling,
 * so by the time the guard is armed those events have already passed. A rare
 * straggler costs one camera blink through quarantine - accepted, because a
 * missed real flip costs a vendor process crash.
 */
object MirrorGuardEvaluator {
    const val STOCK_CAMERA_PACKAGE = "com.byd.avc"

    fun shouldTrigger(
        eventPackage: CharSequence?,
        runtimePhase: CameraRuntimePhase,
        guardEnabled: Boolean,
    ): Boolean = guardEnabled &&
        eventPackage != null &&
        STOCK_CAMERA_PACKAGE.contentEquals(eventPackage) &&
        (runtimePhase == CameraRuntimePhase.STARTING || runtimePhase == CameraRuntimePhase.READY)
}
