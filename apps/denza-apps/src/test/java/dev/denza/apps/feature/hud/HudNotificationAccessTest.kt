package dev.denza.apps.feature.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudNotificationAccessTest {
    @Test
    fun alreadyEnabledAccessSkipsGrant() {
        var grantCalls = 0
        val repair = HudNotificationAccessRepair(
            isEnabled = { true },
            grant = { grantCalls += 1 },
        )

        assertEquals(HudNotificationAccessRepairResult.ALREADY_ENABLED, repair.ensure())
        assertEquals(0, grantCalls)
    }

    @Test
    fun missingAccessIsGrantedAndVerified() {
        var enabled = false
        val repair = HudNotificationAccessRepair(
            isEnabled = { enabled },
            grant = { enabled = true },
        )

        assertEquals(HudNotificationAccessRepairResult.GRANTED, repair.ensure())
        assertTrue(enabled)
    }

    @Test(expected = IllegalStateException::class)
    fun grantThatDoesNotChangeAccessFailsClosed() {
        HudNotificationAccessRepair(
            isEnabled = { false },
            grant = {},
        ).ensure()
    }

    @Test
    fun listenerSettingAcceptsFullAndShortClassNames() {
        val packageName = "dev.denza.apps"
        val className = "dev.denza.apps.feature.hud.YandexNotificationArtworkListener"

        assertTrue(
            HudNotificationAccessPolicy.isEnabled(
                "other.pkg/other.Listener:$packageName/$className",
                packageName,
                className,
            ),
        )
        assertTrue(
            HudNotificationAccessPolicy.isEnabled(
                "$packageName/.feature.hud.YandexNotificationArtworkListener",
                packageName,
                className,
            ),
        )
        assertFalse(
            HudNotificationAccessPolicy.isEnabled(
                "$packageName/.feature.hud.OtherListener",
                packageName,
                className,
            ),
        )
    }

    @Test
    fun allowCommandQuotesTheExactListenerComponent() {
        assertEquals(
            "cmd notification allow_listener " +
                "'dev.denza.apps/dev.denza.apps.feature.hud.YandexNotificationArtworkListener'",
            HudNotificationAccessPolicy.allowCommand(
                "dev.denza.apps/" +
                    "dev.denza.apps.feature.hud.YandexNotificationArtworkListener",
            ),
        )
    }
}
