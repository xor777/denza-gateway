package dev.denza.apps.feature.mirrors

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.denza.apps.feature.cluster.ClusterSceneService

/**
 * Fast-switch guard. Subscribes only to window-state events with zero
 * notification timeout (see mirror_guard_a11y_service.xml) and, when a stock
 * camera window changes while our AVC session is live, synchronously frees
 * the vendor display via [ClusterSceneService.emergencyReleaseCamera] on this
 * same main-thread callback. Measured trigger latency is +30-71 ms after the
 * stock window add against the ~95-174 ms crash budget.
 *
 * Separate from SimulcastAccessibilityService so the Simulcast overlay keeps
 * its 100 ms event batching. Kill switch without reinstall: remove this
 * component from `enabled_accessibility_services`.
 */
class MirrorGuardAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.i(TAG, "guard connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val runtime = ClusterSceneService.cameraRuntimeSnapshot()
        if (
            !MirrorGuardEvaluator.shouldTrigger(
                event.packageName,
                runtime.phase,
                MirrorsSettings.fastSwitchGuardEnabled(this),
            )
        ) {
            return
        }
        val eventAgeMs = SystemClock.uptimeMillis() - event.eventTime
        val reason = "guard window event age=${eventAgeMs}ms session=${runtime.side}"
        Log.i(TAG, "trigger: $reason")
        ClusterSceneService.emergencyReleaseCamera(reason)
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "guard disconnected")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "DenzaMirrorGuard"
    }
}
