package dev.denza.apps

import dev.denza.apps.core.FeatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulcastCoordinatorTest {
    @Test
    fun `ready status requires permissions and connected accessibility runtime`() {
        val ready = SimulcastCoordinator.evaluate(
            SimulcastEnvironment(
                desired = true,
                overlayAllowed = true,
                accessibilityEnabled = true,
                accessibilityConnected = true,
                active = true,
            ),
        )

        assertEquals(FeatureStatus.ACTIVE, ready.status)
    }

    @Test
    fun `blocking problem stays needs action before setup repair`() {
        val blocked = SimulcastCoordinator.evaluate(
            SimulcastEnvironment(
                desired = true,
                blockingProblem = "Выберите приложения",
                overlayAllowed = false,
                accessibilityEnabled = false,
                accessibilityConnected = false,
                active = false,
            ),
        )

        assertEquals(FeatureStatus.NEEDS_ACTION, blocked.status)
        assertEquals("Выберите приложения", blocked.message)
    }

    @Test
    fun `only repairing event keeps setup progress active`() {
        assertTrue(SimulcastReconcileEvent.Repairing.setupRunning)
        assertFalse(SimulcastReconcileEvent.Refresh.setupRunning)
        assertFalse(SimulcastReconcileEvent.Repaired.setupRunning)
        assertFalse(
            SimulcastReconcileEvent.Blocked(
                message = "disabled during repair",
                selectedAppCount = 0,
            ).setupRunning,
        )
        assertFalse(
            SimulcastReconcileEvent.RepairFailed(
                message = "failed",
                details = null,
            ).setupRunning,
        )
    }
}
