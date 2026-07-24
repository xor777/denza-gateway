package dev.denza.apps.feature.navigation

/**
 * Policy for the Denza configurable steering-wheel key.
 *
 * The DiLink key layout maps Linux input code 300 (AUTO_CUSTOM_KEY) to the
 * vendor Android key code 321. Long press is a different key code (322), so it
 * stays available to the stock custom-key settings flow.
 */
object SteeringWheelNavigationButton {
    const val KEY_CODE = 321
    private const val ACTION_DOWN = 0

    @JvmStatic
    fun shouldConsume(enabled: Boolean, keyCode: Int): Boolean =
        enabled && keyCode == KEY_CODE

    @JvmStatic
    fun shouldTrigger(
        enabled: Boolean,
        keyCode: Int,
        action: Int,
        repeatCount: Int,
    ): Boolean =
        shouldConsume(enabled, keyCode) &&
            action == ACTION_DOWN &&
            repeatCount == 0
}
