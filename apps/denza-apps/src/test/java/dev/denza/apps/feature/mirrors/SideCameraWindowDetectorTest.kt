package dev.denza.apps.feature.mirrors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SideCameraWindowDetectorTest {
    @Test
    fun leftCameraUsesTheResolvedClusterId() {
        val windows = """
              Window #1 Window{abc com.byd.avc/com.byd.avc.PIP2MeterActivity}
                mDisplayId=7 package=com.byd.avc
        """.trimIndent()
        assertTrue(SideCameraWindowDetector.isLeftVisible(windows, 7))
        assertFalse(SideCameraWindowDetector.isLeftVisible(windows, 4))
    }

    @Test
    fun rightCameraKeepsTheVerifiedCompactAlertSignature() {
        val windows = """
              Window #2 Window{def com.byd.avc/Alert}
                mDisplayId=0 package=com.byd.avc ty=SYSTEM_ALERT frame=(720x450)
        """.trimIndent()
        assertTrue(SideCameraWindowDetector.isRightVisible(windows))
    }
}
