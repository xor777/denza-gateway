package dev.denza.apps.feature.trip

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Projects raw head-unit IMU samples onto vehicle axes.
 *
 * docs/vehicle-data-findings.md: the head unit is fixed-mounted but its sensor
 * axes are NOT the vehicle axes, so we cannot assume x=lateral / y=longitudinal /
 * z=vertical. What is honestly calibrated here vs. approximated:
 *
 * - VERTICAL axis: calibrated. Taken from a slowly smoothed gravity vector; the
 *   linear-acceleration component along it is trustworthy once gravity settles
 *   (a few seconds). Sign is "up" = opposite gravity.
 * - HORIZONTAL magnitude: calibrated. The residual horizontal acceleration
 *   magnitude is orientation-independent.
 * - LATERAL vs LONGITUDINAL split: approximated, and only after convergence.
 *   Lateral (cornering) acceleration correlates with yaw rate while moving
 *   (a_lat ~ v * yawRate). We regress the two horizontal components against yaw
 *   rate over a moving window; the regression direction is "lateral", the
 *   perpendicular is "longitudinal". Until enough turning-while-moving evidence
 *   accumulates we report calibrated=false and callers must fall back to the
 *   magnitude-only agitation signal.
 *
 * This class is pure: no Android sensor types, fully JVM-testable.
 */
class AxisCalibrator(
    /** Time constant of the gravity low-pass, seconds. */
    private val gravityTau: Double = 1.2,
    /** Yaw-energy that must accumulate in the window before the split is trusted. */
    private val convergenceYawEnergy: Double = 6.0,
    /** Forget factor per second for the regression accumulators (moving window). */
    private val windowHalfLifeSeconds: Double = 12.0,
) {
    // Smoothed gravity (up axis, before normalization). Seeded on first sample.
    private var gx = 0.0
    private var gy = 0.0
    private var gz = 0.0
    private var seeded = false

    // Regression accumulators of horizontal components (e1,e2 basis) vs yaw rate.
    private var sumYawSq = 0.0
    private var sumH1Yaw = 0.0
    private var sumH2Yaw = 0.0
    private var yawEnergy = 0.0

    /** Current best lateral direction in the horizontal (e1,e2) basis. */
    private var latC1 = 1.0
    private var latC2 = 0.0
    private var converged = false

    /**
     * Feed one sample.
     *
     * @param ax raw accelerometer (includes gravity), m/s^2
     * @param gravX smoothed gravity vector from TYPE_GRAVITY, m/s^2
     * @param gyroX gyroscope, rad/s
     * @param speed GNSS ground speed, m/s (used to gate the split learning)
     * @param dt seconds since the previous sample
     */
    fun update(
        ax: Double, ay: Double, az: Double,
        gravX: Double, gravY: Double, gravZ: Double,
        gyroX: Double, gyroY: Double, gyroZ: Double,
        speed: Double,
        dt: Double,
    ): AxisReading {
        val step = dt.coerceIn(0.0, 0.2)
        if (!seeded) {
            gx = gravX; gy = gravY; gz = gravZ; seeded = true
        } else {
            val a = 1.0 - Math.exp(-step / gravityTau)
            gx += (gravX - gx) * a
            gy += (gravY - gy) * a
            gz += (gravZ - gz) * a
        }
        val gMag = sqrt(gx * gx + gy * gy + gz * gz)
        if (gMag < 1e-3) {
            return AxisReading(0.0, 0.0, 0.0, 0.0, 0.0, converged)
        }
        // Unit up axis.
        val ux = gx / gMag; val uy = gy / gMag; val uz = gz / gMag

        // Linear acceleration = raw accel - gravity.
        val lx = ax - gx; val ly = ay - gy; val lz = az - gz

        // Vertical component (positive = up).
        val vertical = lx * ux + ly * uy + lz * uz

        // Horizontal residual.
        val hx = lx - vertical * ux
        val hy = ly - vertical * uy
        val hz = lz - vertical * uz
        val horizontalMagnitude = sqrt(hx * hx + hy * hy + hz * hz)

        // Yaw rate = angular velocity about the up axis.
        val yawRate = gyroX * ux + gyroY * uy + gyroZ * uz

        // Build a stable horizontal basis (e1, e2) spanning the plane _|_ up.
        var rx = 1.0; var ry = 0.0; var rz = 0.0
        if (kotlin.math.abs(ux) > 0.9) { rx = 0.0; ry = 1.0; rz = 0.0 }
        var e1x = rx - (rx * ux + ry * uy + rz * uz) * ux
        var e1y = ry - (rx * ux + ry * uy + rz * uz) * uy
        var e1z = rz - (rx * ux + ry * uy + rz * uz) * uz
        val e1n = sqrt(e1x * e1x + e1y * e1y + e1z * e1z)
        if (e1n < 1e-6) {
            return AxisReading(vertical, horizontalMagnitude, 0.0, 0.0, yawRate, converged)
        }
        e1x /= e1n; e1y /= e1n; e1z /= e1n
        // e2 = up x e1
        val e2x = uy * e1z - uz * e1y
        val e2y = uz * e1x - ux * e1z
        val e2z = ux * e1y - uy * e1x

        val h1 = hx * e1x + hy * e1y + hz * e1z
        val h2 = hx * e2x + hy * e2y + hz * e2z

        // Learn the lateral direction only while genuinely moving.
        if (speed > 3.0) {
            val decay = Math.exp(-step / windowHalfLifeSeconds)
            sumYawSq = sumYawSq * decay + yawRate * yawRate * step
            sumH1Yaw = sumH1Yaw * decay + h1 * yawRate * step
            sumH2Yaw = sumH2Yaw * decay + h2 * yawRate * step
            yawEnergy = yawEnergy * decay + yawRate * yawRate * step
            if (sumYawSq > 1e-4) {
                val c1 = sumH1Yaw / sumYawSq
                val c2 = sumH2Yaw / sumYawSq
                val cn = hypot(c1, c2)
                if (cn > 1e-4) {
                    latC1 = c1 / cn
                    latC2 = c2 / cn
                }
            }
            converged = yawEnergy >= convergenceYawEnergy
        }

        // Lateral = projection onto learnt direction; longitudinal = perpendicular.
        val lateral = h1 * latC1 + h2 * latC2
        val longitudinal = -h1 * latC2 + h2 * latC1

        return AxisReading(
            vertical = vertical,
            horizontalMagnitude = horizontalMagnitude,
            lateral = lateral,
            longitudinal = longitudinal,
            yawRate = yawRate,
            calibrated = converged,
        )
    }

    val isCalibrated: Boolean get() = converged
}
