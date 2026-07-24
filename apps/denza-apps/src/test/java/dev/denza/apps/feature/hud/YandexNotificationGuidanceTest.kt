package dev.denza.apps.feature.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YandexNotificationGuidanceTest {
    @Test
    fun parsesRichNotificationIntoBackgroundGuidance() {
        val patch = YandexNotificationGuidanceParser.parse(
            YandexNotificationGuidanceFields(
                maneuverResourceName = "notification_right_sdl",
                title = "250 м",
                description = "Профсоюзная улица",
                remainingDistance = "12 км",
                remainingTime = "18 мин",
                arrivalTime = "19:42",
            ),
        )

        val guidance = requireNotNull(patch).mergeWith(visibleGuidance())

        assertEquals(HudManeuver.RIGHT, guidance.maneuver)
        assertEquals(250, guidance.maneuverDistanceMeters)
        assertEquals("Профсоюзная улица", guidance.nextRoadName)
        assertEquals(12_000, guidance.remainingDistanceMeters)
        assertEquals(1_080, guidance.remainingTimeSeconds)
        assertEquals("19:42", guidance.eta)
    }

    @Test
    fun notificationManeuverCanReplaceTheLastVisibleManeuver() {
        val patch = requireNotNull(
            YandexNotificationGuidanceParser.parse(
                YandexNotificationGuidanceFields(
                    maneuverResourceName = "notification_left_sdl",
                    title = "80 м",
                    description = "Ленинский проспект",
                ),
            ),
        )

        val guidance = patch.mergeWith(visibleGuidance())

        assertEquals(HudManeuver.LEFT, guidance.maneuver)
        assertEquals("Поверните налево", guidance.instruction)
        assertEquals(80, guidance.maneuverDistanceMeters)
    }

    @Test
    fun idleNotificationIsNotTreatedAsAnActiveRoute() {
        assertNull(
            YandexNotificationGuidanceParser.parse(
                YandexNotificationGuidanceFields(
                    title = "Навигатор запущен",
                ),
            ),
        )
    }

    @Test
    fun backgroundGuidanceExpiresAndClearsExplicitly() {
        val store = HudNotificationGuidanceStore(maxAgeMs = 90_000L)
        val patch = requireNotNull(
            YandexNotificationGuidanceParser.parse(
                YandexNotificationGuidanceFields(
                    maneuverResourceName = "notification_right_sdl",
                    title = "120 м",
                ),
            ),
        )
        store.update(patch, capturedAtMs = 10_000L)

        assertEquals(
            120,
            store.resolve(visibleGuidance(), nowMs = 99_999L)?.maneuverDistanceMeters,
        )
        assertNull(store.resolve(visibleGuidance(), nowMs = 100_001L))

        store.update(patch, capturedAtMs = 110_000L)
        store.clear()
        assertNull(store.resolve(visibleGuidance(), nowMs = 110_001L))
    }

    private fun visibleGuidance() = HudGuidance(
        maneuver = HudManeuver.RIGHT,
        roundaboutExitNumber = null,
        instruction = "Поверните направо",
        nextRoadName = "Старая улица",
        maneuverDistanceMeters = 400,
        remainingDistanceMeters = 20_000,
        remainingTimeSeconds = 1_800,
        remainingTimeText = "30 мин",
        eta = "19:54",
    )
}
