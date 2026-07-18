package dev.denza.apps.feature.simulcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTargetTest {
    @Test
    fun exposesOnlyVisibleAndRuntimeAvailableReceivers() {
        val targets = ScreenTarget.availableTargets(
            setOf("ar_hud_screen", "overhead_screen", "right_rse_screen"),
            setOf("screen_hud", "screen_overhead", "screen_rse_l"),
        )

        assertEquals(setOf("screen_hud", "screen_overhead"), ScreenTarget.receiverIds(targets))
    }

    @Test
    fun neverTreatsIviAsReceiver() {
        val targets = ScreenTarget.availableTargets(
            setOf("central_screen", "ar_hud_screen"),
            setOf("screen_ivi", "screen_hud"),
        )

        assertTrue(ScreenTarget.receiverIds(targets).contains("screen_hud"))
        assertFalse(ScreenTarget.receiverIds(targets).contains("screen_ivi"))
    }

    @Test
    fun includesBothRearAndOverheadLayoutFamilies() {
        val receiverIds = ScreenTarget.SUPPORTED.map { it.receiverId }.toSet()

        assertTrue(receiverIds.contains("screen_rse_l"))
        assertTrue(receiverIds.contains("screen_rse_r"))
        assertTrue(receiverIds.contains("screen_overhead"))
    }
}
