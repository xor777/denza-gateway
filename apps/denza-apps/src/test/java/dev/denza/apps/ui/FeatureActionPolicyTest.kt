package dev.denza.apps.ui

import dev.denza.apps.core.FeatureId
import dev.denza.apps.core.FeatureResolution
import dev.denza.apps.core.FeatureSnapshot
import dev.denza.apps.core.FeatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureActionPolicyTest {
    @Test
    fun `simulcast selection emphasizes its existing chooser`() {
        val policy = FeatureActionPolicy.simulcast(
            snapshot(
                FeatureId.SIMULCAST,
                FeatureResolution.SELECT_APPS,
                message = "a message that the policy must not inspect",
            ),
        )

        assertTrue(policy.chooserEmphasized)
        assertEquals("Запустить", policy.primaryLabel)
        assertFalse(policy.primaryEnabled)
        assertEquals(FeatureActionTarget.NONE, policy.primaryTarget)
    }

    @Test
    fun `unavailable simulcast does not offer a fake repair`() {
        val policy = FeatureActionPolicy.simulcast(
            snapshot(
                FeatureId.SIMULCAST,
                resolution = null,
                status = FeatureStatus.UNAVAILABLE,
            ),
        )

        assertEquals("Недоступно", policy.primaryLabel)
        assertFalse(policy.primaryEnabled)
        assertEquals(FeatureActionTarget.NONE, policy.primaryTarget)
    }

    @Test
    fun `external confirmation reuses the simulcast primary control`() {
        val policy = FeatureActionPolicy.simulcast(
            snapshot(FeatureId.SIMULCAST, FeatureResolution.CONFIRM_ON_CAR),
        )

        assertEquals("Проверить", policy.primaryLabel)
        assertTrue(policy.primaryEnabled)
        assertEquals(FeatureActionTarget.RETRY, policy.primaryTarget)
    }

    @Test
    fun `navigation selection emphasizes its existing chooser`() {
        val policy = FeatureActionPolicy.navigation(
            snapshot(FeatureId.NAVIGATION, FeatureResolution.SELECT_NAVIGATION_APP),
            defaultLabel = "Открыть",
        )

        assertTrue(policy.chooserEmphasized)
        assertEquals("Открыть", policy.primaryLabel)
        assertFalse(policy.primaryEnabled)
    }

    @Test
    fun `navigation display resolution reuses its primary control`() {
        val policy = FeatureActionPolicy.navigation(
            snapshot(FeatureId.NAVIGATION, FeatureResolution.SELECT_CLUSTER_DISPLAY),
            defaultLabel = "На приборку",
        )

        assertEquals("Выбрать экран", policy.primaryLabel)
        assertEquals(FeatureActionTarget.SELECT_CLUSTER_DISPLAY, policy.primaryTarget)
    }

    @Test
    fun `mirror retry keeps the existing full width action`() {
        val policy = FeatureActionPolicy.mirrors(
            snapshot(
                FeatureId.MIRRORS,
                FeatureResolution.RETRY,
                status = FeatureStatus.UNAVAILABLE,
            ),
        )

        assertEquals("Повторить поиск", policy.label)
        assertEquals(FeatureActionTarget.RETRY, policy.target)
        assertTrue(policy.enabled)
    }

    @Test
    fun `normal controls retain their current labels and targets`() {
        val normal = FeatureSnapshot(
            id = FeatureId.NAVIGATION,
            desiredEnabled = false,
            status = FeatureStatus.READY,
        )

        val navigation = FeatureActionPolicy.navigation(normal, "Открыть")
        val mirrors = FeatureActionPolicy.mirrors(normal.copy(id = FeatureId.MIRRORS))
        val simulcast = FeatureActionPolicy.simulcast(normal.copy(id = FeatureId.SIMULCAST))

        assertEquals("Открыть", navigation.primaryLabel)
        assertEquals(FeatureActionTarget.DEFAULT, navigation.primaryTarget)
        assertEquals("Проверить камеры", mirrors.label)
        assertEquals(FeatureActionTarget.DEFAULT, mirrors.target)
        assertEquals("Запустить", simulcast.primaryLabel)
        assertEquals(FeatureActionTarget.DEFAULT, simulcast.primaryTarget)
    }

    @Test
    fun `compact retry label is temporary and typed`() {
        assertEquals(
            "Повторить",
            FeatureActionPolicy.compactRetryLabel(
                snapshot(FeatureId.HUD_GUIDANCE, FeatureResolution.RETRY),
            ),
        )
        assertEquals(
            "Проверить",
            FeatureActionPolicy.compactRetryLabel(
                snapshot(FeatureId.HUD_GUIDANCE, FeatureResolution.CONFIRM_ON_CAR),
            ),
        )
        assertNull(
            FeatureActionPolicy.compactRetryLabel(
                snapshot(FeatureId.HUD_GUIDANCE, FeatureResolution.SELECT_APPS),
            ),
        )
    }

    private fun snapshot(
        id: FeatureId,
        resolution: FeatureResolution?,
        message: String = "",
        status: FeatureStatus = FeatureStatus.NEEDS_ACTION,
    ): FeatureSnapshot = FeatureSnapshot(
        id = id,
        desiredEnabled = true,
        status = status,
        message = message,
        resolution = resolution,
    )
}
