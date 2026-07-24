package dev.denza.apps.feature.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTrackerTest {

    @Test
    fun noCourseBeforeAValidMovingBearing() {
        val c = CourseTracker()
        // Bearing present but the car is basically stopped -> not trusted.
        c.onFix(hasBearing = true, bearingDeg = 90.0, speed = 0.4, dt = 1.0)
        assertFalse(c.hasCourse)
        // Moving but the fix has no bearing (Location.getBearing() == 0.0 sentinel).
        c.onFix(hasBearing = false, bearingDeg = 0.0, speed = 12.0, dt = 1.0)
        assertFalse(c.hasCourse)
    }

    @Test
    fun acquiresCourseFromFirstValidMovingBearing() {
        val c = CourseTracker()
        c.onFix(hasBearing = true, bearingDeg = 137.0, speed = 10.0, dt = 1.0)
        assertTrue(c.hasCourse)
        assertEquals(137.0, c.courseDeg, 1e-9)
        assertFalse(c.held)
    }

    @Test
    fun holdsLastCourseWhenStopped() {
        val c = CourseTracker()
        c.onFix(hasBearing = true, bearingDeg = 137.0, speed = 10.0, dt = 1.0)
        // Stopped: even a provided bearing is ignored; last course is held & dimmed.
        c.onFix(hasBearing = true, bearingDeg = 200.0, speed = 0.2, dt = 1.0)
        assertTrue(c.hasCourse)
        assertTrue(c.held)
        assertEquals(137.0, c.courseDeg, 1e-9)
    }

    @Test
    fun smoothingTakesTheShortArcAcrossNorth() {
        val c = CourseTracker()
        c.onFix(hasBearing = true, bearingDeg = 350.0, speed = 10.0, dt = 1.0) // snap to 350
        c.onFix(hasBearing = true, bearingDeg = 10.0, speed = 10.0, dt = 1.0) // glide toward 10
        // Must go the short way through 0, never toward 180, and stay in [0,360).
        assertTrue("course=${c.courseDeg}", c.courseDeg in 0.0..360.0)
        assertTrue("course=${c.courseDeg}", c.courseDeg > 350.0 || c.courseDeg < 20.0)
        // Converges toward 10 over more steps.
        repeat(6) { c.onFix(hasBearing = true, bearingDeg = 10.0, speed = 10.0, dt = 1.0) }
        assertEquals(10.0, c.courseDeg, 1.0)
    }

    @Test
    fun yawRotatesHeldCourseAndDoesNotDriftWithoutYaw() {
        val c = CourseTracker()
        c.onFix(hasBearing = true, bearingDeg = 100.0, speed = 10.0, dt = 1.0)
        // While moving, yaw is ignored (GNSS is authoritative).
        c.onYaw(yawRate = 0.5, dt = 0.1)
        assertEquals(100.0, c.courseDeg, 1e-9)

        // Stop, then a steady yaw rotates the held course by |yaw|*t (degrees).
        c.onFix(hasBearing = false, bearingDeg = 0.0, speed = 0.0, dt = 1.0)
        assertTrue(c.held)
        repeat(10) { c.onYaw(yawRate = 0.5, dt = 0.1) }
        // -toDegrees(0.5 * 0.1) per step * 10 = -28.648 deg.
        assertEquals(71.352, c.courseDeg, 0.05)

        // No yaw -> no drift (bounded), stays put over many samples.
        val settled = c.courseDeg
        repeat(200) { c.onYaw(yawRate = 0.0, dt = 0.1) }
        assertEquals(settled, c.courseDeg, 1e-9)
    }

    @Test
    fun yawIsIgnoredBeforeAnyCourse() {
        val c = CourseTracker()
        c.onYaw(yawRate = 0.8, dt = 0.1)
        assertFalse(c.hasCourse)
        assertEquals(0.0, c.courseDeg, 1e-9)
    }
}
