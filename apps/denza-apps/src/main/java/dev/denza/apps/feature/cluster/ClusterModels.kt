package dev.denza.apps.feature.cluster

data class ClusterDisplayDescriptor(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val type: Int,
    val flags: Int,
    val isOwnVirtualDisplay: Boolean = false,
)

sealed interface ClusterDisplaySelection {
    data class Selected(val display: ClusterDisplayDescriptor) : ClusterDisplaySelection
    data class NeedsVerification(val candidates: List<ClusterDisplayDescriptor>) : ClusterDisplaySelection
    data object Missing : ClusterDisplaySelection
}

enum class ClusterCameraPosition {
    LEFT,
    RIGHT,
    CENTER,
}

data class ClusterBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class ClusterLayout(
    val displayWidth: Int,
    val displayHeight: Int,
    val cameraPosition: ClusterCameraPosition,
    val centerExtendPercent: Int = 20,
) {
    val mapBounds: ClusterBounds = ClusterBounds(0, 0, displayWidth, displayHeight)
    val baseSlotWidth: Int = (displayWidth / 3).coerceAtLeast(1)
    val cameraWidth: Int = (
        baseSlotWidth + (baseSlotWidth * centerExtendPercent.coerceIn(0, 100) / 100f).toInt()
    ).coerceAtMost(displayWidth)
    val cameraLeft: Int = when (cameraPosition) {
        ClusterCameraPosition.LEFT -> 0
        ClusterCameraPosition.RIGHT -> displayWidth - cameraWidth
        ClusterCameraPosition.CENTER -> (displayWidth - cameraWidth) / 2
    }
    val cameraBounds: ClusterBounds = ClusterBounds(
        cameraLeft,
        0,
        cameraLeft + cameraWidth,
        displayHeight,
    )
}

data class ClusterSceneState(
    val display: ClusterDisplayDescriptor? = null,
    val layout: ClusterLayout? = null,
    val mapVisible: Boolean = false,
    val cameraVisible: Boolean = false,
    val needsDisplayVerification: Boolean = false,
    val details: String? = null,
)
