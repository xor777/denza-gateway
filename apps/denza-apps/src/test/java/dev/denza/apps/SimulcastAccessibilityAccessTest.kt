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
    fun `recognizes guard component separately from the simulcast one`() {
        val guardOnly = "system/service:${SimulcastAccessibilityAccess.GUARD_COMPONENT}"
        assertTrue(SimulcastAccessibilityAccess.isGuardEnabled(guardOnly))
        assertFalse(SimulcastAccessibilityAccess.isEnabled(guardOnly))
        assertTrue(
            SimulcastAccessibilityAccess.isGuardEnabled(
                "dev.denza.apps/.feature.mirrors.MirrorGuardAccessibilityService",
            ),
        )
        assertFalse(SimulcastAccessibilityAccess.isGuardEnabled("system/service:voice/service"))
    }

    @Test
    fun `rebind removes only owned services and restores both once`() {
        val original = "system/service:${SimulcastAccessibilityAccess.COMPONENT}" +
            ":${SimulcastAccessibilityAccess.GUARD_COMPONENT}:voice/service"

        val disabled = SimulcastAccessibilityAccess.withoutService(original)
        val enabled = SimulcastAccessibilityAccess.withService(disabled)

        assertEquals("system/service:voice/service", disabled)
        assertEquals(
            "system/service:voice/service:${SimulcastAccessibilityAccess.COMPONENT}" +
                ":${SimulcastAccessibilityAccess.GUARD_COMPONENT}",
            enabled,
        )
    }

    @Test
    fun `empty Android setting enables both owned services`() {
        assertEquals(
            "${SimulcastAccessibilityAccess.COMPONENT}" +
                ":${SimulcastAccessibilityAccess.GUARD_COMPONENT}",
            SimulcastAccessibilityAccess.withService("null"),
        )
    }
}
