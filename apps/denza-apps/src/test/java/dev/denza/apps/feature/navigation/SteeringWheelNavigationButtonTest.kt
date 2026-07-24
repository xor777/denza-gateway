package dev.denza.apps.feature.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringWheelNavigationButtonTest {
    @Test
    fun `short custom-key down triggers once when enabled`() {
        assertTrue(
            SteeringWheelNavigationButton.shouldTrigger(
                enabled = true,
                keyCode = SteeringWheelNavigationButton.KEY_CODE,
                action = 0,
                repeatCount = 0,
            ),
        )
        assertFalse(
            SteeringWheelNavigationButton.shouldTrigger(
                enabled = true,
                keyCode = SteeringWheelNavigationButton.KEY_CODE,
                action = 1,
                repeatCount = 0,
            ),
        )
        assertFalse(
            SteeringWheelNavigationButton.shouldTrigger(
                enabled = true,
                keyCode = SteeringWheelNavigationButton.KEY_CODE,
                action = 0,
                repeatCount = 1,
            ),
        )
    }

    @Test
    fun `disabled option leaves the stock short key untouched`() {
        assertFalse(
            SteeringWheelNavigationButton.shouldConsume(
                enabled = false,
                keyCode = SteeringWheelNavigationButton.KEY_CODE,
            ),
        )
    }

    @Test
    fun `long press and unrelated keys are never consumed`() {
        assertFalse(
            SteeringWheelNavigationButton.shouldConsume(
                enabled = true,
                keyCode = 322,
            ),
        )
        assertFalse(
            SteeringWheelNavigationButton.shouldConsume(
                enabled = true,
                keyCode = 24,
            ),
        )
    }
}
