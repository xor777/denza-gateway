package dev.denza.apps.feature.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AxisCalibratorTest {

    private val g = 9.81

    @Test
    fun extractsVerticalAccelerationAlongGravity() {
        val axis = AxisCalibrator()
        // Gravity points down -z (device flat): up = +z. Add +1 m/s^2 upward.
        val r = axis.update(
            ax = 0.0, ay = 0.0, az = g + 1.0,
            gravX = 0.0, gravY = 0.0, gravZ = g,
            gyroX = 0.0, gyroY = 0.0, gyroZ = 0.0,
            speed = 0.0, dt = 1.0 / 30.0,
        )
        assertEquals(1.0, r.vertical, 0.05)
        assertEquals(0.0, r.horizontalMagnitude, 0.05)
    }

    @Test
    fun extractsHorizontalMagnitudeRegardlessOfDirection() {
        val axis = AxisCalibrator()
        val r = axis.update(
            ax = 2.0, ay = 0.0, az = g,
            gravX = 0.0, gravY = 0.0, gravZ = g,
            gyroX = 0.0, gyroY = 0.0, gyroZ = 0.0,
            speed = 0.0, dt = 1.0 / 30.0,
        )
        assertEquals(0.0, r.vertical, 0.05)
        assertEquals(2.0, r.horizontalMagnitude, 0.05)
    }

    @Test
    fun doesNotClaimLateralCalibrationWhenStationary() {
        val axis = AxisCalibrator()
        repeat(500) {
            val r = axis.update(
                ax = 2.0, ay = 0.0, az = g,
                gravX = 0.0, gravY = 0.0, gravZ = g,
                gyroX = 0.0, gyroY = 0.0, gyroZ = 0.5,
                speed = 0.0, dt = 1.0 / 30.0,
            )
            assertFalse(r.calibrated)
        }
    }

    @Test
    fun convergesLateralDirectionFromCorneringWhileMoving() {
        val axis = AxisCalibrator()
        val dt = 1.0 / 30.0
        // Real lateral axis is +x. Lateral acceleration correlates with yaw.
        var last = AxisReading(0.0, 0.0, 0.0, 0.0, 0.0, false)
        var yaw = 0.0
        for (i in 0 until 4000) {
            yaw = 1.5 * sin(i * 0.05)
            val aLat = 3.0 * yaw // pure +x horizontal acceleration
            last = axis.update(
                ax = aLat, ay = 0.0, az = g,
                gravX = 0.0, gravY = 0.0, gravZ = g,
                gyroX = 0.0, gyroY = 0.0, gyroZ = yaw,
                speed = 10.0, dt = dt,
            )
        }
        assertTrue(axis.isCalibrated)
        val expectedLateral = 3.0 * yaw
        assertEquals(expectedLateral, last.lateral, 0.4)
        assertTrue("longitudinal=${last.longitudinal}", abs(last.longitudinal) < 0.4)
    }
}
