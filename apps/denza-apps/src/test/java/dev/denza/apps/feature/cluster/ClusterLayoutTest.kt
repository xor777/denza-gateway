package dev.denza.apps.feature.cluster

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterLayoutTest {
    @Test
    fun mapAlwaysOccupiesTheWholeDisplay() {
        val layout = ClusterLayout(1920, 720, ClusterCameraPosition.CENTER)
        assertEquals(ClusterBounds(0, 0, 1920, 720), layout.mapBounds)
    }

    @Test
    fun cameraUsesOneThirdPlusTwentyPercentAtEachPosition() {
        val left = ClusterLayout(1920, 720, ClusterCameraPosition.LEFT)
        val center = ClusterLayout(1920, 720, ClusterCameraPosition.CENTER)
        val right = ClusterLayout(1920, 720, ClusterCameraPosition.RIGHT)

        assertEquals(768, left.cameraWidth)
        assertEquals(ClusterBounds(0, 0, 768, 720), left.cameraBounds)
        assertEquals(ClusterBounds(576, 0, 1344, 720), center.cameraBounds)
        assertEquals(ClusterBounds(1152, 0, 1920, 720), right.cameraBounds)
    }
}
