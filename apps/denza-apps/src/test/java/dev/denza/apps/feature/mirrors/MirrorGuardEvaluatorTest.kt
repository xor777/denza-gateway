package dev.denza.apps.feature.mirrors

import dev.denza.apps.feature.cluster.CameraRuntimePhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorGuardEvaluatorTest {
    @Test
    fun triggersOnStockCameraEventWhileSessionIsLive() {
        assertTrue(
            MirrorGuardEvaluator.shouldTrigger(
                "com.byd.avc",
                CameraRuntimePhase.READY,
                guardEnabled = true,
            ),
        )
        assertTrue(
            MirrorGuardEvaluator.shouldTrigger(
                "com.byd.avc",
                CameraRuntimePhase.STARTING,
                guardEnabled = true,
            ),
        )
    }

    @Test
    fun ignoresForeignPackagesAndMissingPackage() {
        assertFalse(
            MirrorGuardEvaluator.shouldTrigger(
                "com.byd.dishare",
                CameraRuntimePhase.READY,
                guardEnabled = true,
            ),
        )
        assertFalse(
            MirrorGuardEvaluator.shouldTrigger(null, CameraRuntimePhase.READY, guardEnabled = true),
        )
    }

    @Test
    fun disarmedWhenNoLiveSession() {
        for (phase in listOf(
            CameraRuntimePhase.IDLE,
            CameraRuntimePhase.STOPPING,
            CameraRuntimePhase.FAILED,
        )) {
            assertFalse(
                MirrorGuardEvaluator.shouldTrigger("com.byd.avc", phase, guardEnabled = true),
            )
        }
    }

    @Test
    fun disarmedByFlag() {
        assertFalse(
            MirrorGuardEvaluator.shouldTrigger(
                "com.byd.avc",
                CameraRuntimePhase.READY,
                guardEnabled = false,
            ),
        )
    }
}
