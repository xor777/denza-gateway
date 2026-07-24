package dev.denza.apps.ui

import dev.denza.apps.core.FeatureResolution
import dev.denza.apps.core.FeatureSnapshot
import dev.denza.apps.core.FeatureStatus

enum class FeatureActionTarget {
    DEFAULT,
    SELECT_CLUSTER_DISPLAY,
    RETRY,
    NONE,
}

data class DualActionPolicy(
    val chooserEmphasized: Boolean = false,
    val primaryLabel: String,
    val primaryEnabled: Boolean = true,
    val primaryTarget: FeatureActionTarget = FeatureActionTarget.DEFAULT,
)

data class SingleActionPolicy(
    val label: String,
    val enabled: Boolean = true,
    val target: FeatureActionTarget = FeatureActionTarget.DEFAULT,
)

object FeatureActionPolicy {
    fun simulcast(snapshot: FeatureSnapshot): DualActionPolicy {
        if (snapshot.status == FeatureStatus.UNAVAILABLE) {
            return DualActionPolicy(
                primaryLabel = "Недоступно",
                primaryEnabled = false,
                primaryTarget = FeatureActionTarget.NONE,
            )
        }
        return when (snapshot.resolution) {
            FeatureResolution.SELECT_APPS -> DualActionPolicy(
                chooserEmphasized = true,
                primaryLabel = "Запустить",
                primaryEnabled = false,
                primaryTarget = FeatureActionTarget.NONE,
            )
            FeatureResolution.CONFIRM_ON_CAR,
            FeatureResolution.ENABLE_CAR_DEBUGGING,
            -> DualActionPolicy(
                primaryLabel = "Проверить",
                primaryTarget = FeatureActionTarget.RETRY,
            )
            FeatureResolution.RETRY -> DualActionPolicy(
                primaryLabel = "Повторить",
                primaryTarget = FeatureActionTarget.RETRY,
            )
            FeatureResolution.SELECT_CLUSTER_DISPLAY,
            FeatureResolution.SELECT_NAVIGATION_APP,
            null,
            -> DualActionPolicy(primaryLabel = "Запустить")
        }
    }

    fun navigation(
        snapshot: FeatureSnapshot,
        defaultLabel: String,
    ): DualActionPolicy = when (snapshot.resolution) {
        FeatureResolution.SELECT_NAVIGATION_APP -> DualActionPolicy(
            chooserEmphasized = true,
            primaryLabel = defaultLabel,
            primaryEnabled = false,
            primaryTarget = FeatureActionTarget.NONE,
        )
        FeatureResolution.SELECT_CLUSTER_DISPLAY -> DualActionPolicy(
            primaryLabel = "Выбрать экран",
            primaryTarget = FeatureActionTarget.SELECT_CLUSTER_DISPLAY,
        )
        FeatureResolution.CONFIRM_ON_CAR,
        FeatureResolution.ENABLE_CAR_DEBUGGING,
        -> DualActionPolicy(
            primaryLabel = "Проверить",
            primaryTarget = FeatureActionTarget.RETRY,
        )
        FeatureResolution.RETRY -> DualActionPolicy(
            primaryLabel = "Повторить",
            primaryTarget = FeatureActionTarget.RETRY,
        )
        FeatureResolution.SELECT_APPS,
        null,
        -> DualActionPolicy(primaryLabel = defaultLabel)
    }

    fun mirrors(snapshot: FeatureSnapshot): SingleActionPolicy = when (snapshot.resolution) {
        FeatureResolution.SELECT_CLUSTER_DISPLAY -> SingleActionPolicy(
            label = "Повторить поиск",
            target = FeatureActionTarget.RETRY,
        )
        FeatureResolution.CONFIRM_ON_CAR,
        FeatureResolution.ENABLE_CAR_DEBUGGING,
        -> SingleActionPolicy(
            label = "Проверить",
            target = FeatureActionTarget.RETRY,
        )
        FeatureResolution.RETRY -> SingleActionPolicy(
            label = "Повторить поиск",
            target = FeatureActionTarget.RETRY,
        )
        FeatureResolution.SELECT_APPS,
        FeatureResolution.SELECT_NAVIGATION_APP,
        null,
        -> SingleActionPolicy(label = "Проверить камеры")
    }

    fun compactRetryLabel(snapshot: FeatureSnapshot): String? = when (snapshot.resolution) {
        FeatureResolution.CONFIRM_ON_CAR,
        FeatureResolution.ENABLE_CAR_DEBUGGING,
        -> "Проверить"
        FeatureResolution.RETRY -> "Повторить"
        FeatureResolution.SELECT_APPS,
        FeatureResolution.SELECT_CLUSTER_DISPLAY,
        FeatureResolution.SELECT_NAVIGATION_APP,
        null,
        -> null
    }
}
