package dev.denza.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulcastAccessibilityAccessTest {
    @Test
    fun `recognizes canonical and shorthand components`() {
        assertTrue(
            SimulcastAccessibilityAccess.isEnabled(
                "system/service:${SimulcastAccessibilityAccess.COMPONENT}:voice/service",
            ),
        )
        assertTrue(
            SimulcastAccessibilityAccess.isEnabled(
                "system/service:dev.denza.apps/.SimulcastAccessibilityService",
            ),
        )
        assertFalse(SimulcastAccessibilityAccess.isEnabled("system/service:voice/service"))
    }

    @Test
    fun `rebind removes only simulcast service and restores it once`() {
        val original = "system/service:${SimulcastAccessibilityAccess.COMPONENT}:voice/service"

        val disabled = SimulcastAccessibilityAccess.withoutService(original)
        val enabled = SimulcastAccessibilityAccess.withService(disabled)

        assertEquals("system/service:voice/service", disabled)
        assertEquals(
            "system/service:voice/service:${SimulcastAccessibilityAccess.COMPONENT}",
            enabled,
        )
    }

    @Test
    fun `empty Android setting enables only simulcast service`() {
        assertEquals(
            SimulcastAccessibilityAccess.COMPONENT,
            SimulcastAccessibilityAccess.withService("null"),
        )
    }
}
