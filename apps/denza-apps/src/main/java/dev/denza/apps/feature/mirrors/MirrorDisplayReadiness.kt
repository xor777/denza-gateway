package dev.denza.apps.feature.mirrors

import dev.denza.apps.core.FeatureId
import dev.denza.apps.core.FeatureReducer
import dev.denza.apps.core.FeatureResolution
import dev.denza.apps.core.FeatureSnapshot
import dev.denza.apps.feature.cluster.ClusterDisplaySelection

/**
 * Mirrors render on the dedicated camera-overlay display, not on the base
 * instrument display used by navigation.
 */
object MirrorDisplayReadiness {
    fun snapshot(
        selection: ClusterDisplaySelection,
        active: Boolean,
    ): FeatureSnapshot = when (selection) {
        is ClusterDisplaySelection.Selected -> FeatureReducer.ready(
            FeatureId.MIRRORS,
            active = active,
        )
        is ClusterDisplaySelection.NeedsVerification -> FeatureReducer.needsAction(
            FeatureReducer.starting(FeatureId.MIRRORS),
            message = "Повторите поиск экрана камер",
            details = "camera overlay display is ambiguous",
            resolution = FeatureResolution.RETRY,
        )
        ClusterDisplaySelection.Missing -> FeatureReducer.needsAction(
            FeatureReducer.starting(FeatureId.MIRRORS),
            message = "Повторите поиск экрана камер",
            details = "camera overlay display not found",
            resolution = FeatureResolution.RETRY,
        )
    }
}
