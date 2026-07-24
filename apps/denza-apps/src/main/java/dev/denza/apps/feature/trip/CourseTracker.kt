package dev.denza.apps.feature.trip

import kotlin.math.exp

/**
 * Honest, fail-closed compass course for the «Рейс» tape.
 *
 * The head unit has no magnetometer (docs/vehicle-data-findings.md), so GNSS
 * bearing is the only absolute heading — and it is only valid while moving.
 * `Location.getBearing()` returns 0.0 when there is no bearing, which the old
 * renderer showed as a real due-north heading; this tracker refuses that.
 *
 * Rules:
 *  - A bearing sample is accepted only when the fix reports a bearing AND ground
 *    speed is above [movingThreshold]. Before the first accepted sample there is
 *    no course at all ([hasCourse] == false) and the renderer shows «курс —».
 *  - While moving, the course glides toward the GNSS bearing with a short
 *    critically-damped-style smoother over the shortest arc (handles 359->0).
 *  - When stopped after a course was seen, the last course is HELD (tape stays
 *    live but [held] dims it). While held the calibrated IMU yaw rate is
 *    integrated in, so parking turns rotate the tape; when movement resumes the
 *    held course blends back to GNSS over ~[reacquireBlendTau] s to avoid a jump.
 *
 * Pure and JVM-testable.
 */
class CourseTracker(
    /** Ground speed (m/s) below which GNSS bearing is not trusted. */
    private val movingThreshold: Double = 1.5,
    /** Smoother time constant while moving, seconds. */
    private val smoothTau: Double = 0.5,
    /** Slower time constant used for ~2 s after re-acquiring, seconds. */
    private val reacquireBlendTau: Double = 2.0,
    /** Integrate yaw into the held course while stopped (parking turns). */
    private val yawHoldEnabled: Boolean = true,
) {
    var hasCourse: Boolean = false
        private set

    /** Displayed course in degrees, 0..360 (0 = N, 90 = E). */
    var courseDeg: Double = 0.0
        private set

    /** True while holding a stale course at a standstill — the tape is dimmed. */
    var held: Boolean = false
        private set

    private var moving: Boolean = false
    private var blendRemaining: Double = 0.0

    /** Feed one GNSS fix. */
    fun onFix(hasBearing: Boolean, bearingDeg: Double, speed: Double, dt: Double) {
        val step = dt.coerceIn(0.0, 5.0)
        moving = speed >= movingThreshold
        val valid = hasBearing && moving
        if (!valid) {
            // No trustworthy bearing this fix: hold the last course if we have one.
            if (hasCourse) held = true
            return
        }
        val target = wrap360(bearingDeg)
        if (!hasCourse) {
            courseDeg = target
            hasCourse = true
            held = false
            blendRemaining = 0.0
            return
        }
        if (held) {
            // Just started moving again after a hold: blend back slowly.
            held = false
            blendRemaining = reacquireBlendTau
        }
        val tau = if (blendRemaining > 0.0) reacquireBlendTau else smoothTau
        courseDeg = smoothToward(courseDeg, target, tau, step)
        blendRemaining = (blendRemaining - step).coerceAtLeast(0.0)
    }

    /** Feed one IMU sample's calibrated yaw rate (rad/s about the vertical axis). */
    fun onYaw(yawRate: Double, dt: Double) {
        if (!yawHoldEnabled || !hasCourse || moving) return
        // A positive yaw about "up" (right-hand rule) turns the nose counter-
        // clockwise, i.e. compass heading DECREASES. Sign needs live-car
        // confirmation, but the magnitude is what makes parking turns visible.
        courseDeg = wrap360(courseDeg - Math.toDegrees(yawRate * dt.coerceIn(0.0, 0.2)))
    }

    private fun smoothToward(current: Double, target: Double, tau: Double, dt: Double): Double {
        if (dt <= 0.0) return current
        val a = 1.0 - exp(-dt / tau)
        return wrap360(current + shortestArc(target - current) * a)
    }

    private companion object {
        fun wrap360(deg: Double): Double {
            var d = deg % 360.0
            if (d < 0) d += 360.0
            return d
        }

        /** Normalize a delta to (-180, 180] for shortest-arc interpolation. */
        fun shortestArc(delta: Double): Double {
            var d = (delta + 180.0) % 360.0
            if (d < 0) d += 360.0
            return d - 180.0
        }
    }
}
