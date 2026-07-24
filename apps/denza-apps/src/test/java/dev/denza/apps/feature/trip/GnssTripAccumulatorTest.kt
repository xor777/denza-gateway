package dev.denza.apps.feature.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssTripAccumulatorTest {

    @Test
    fun haversineMatchesOneDegreeAtEquator() {
        val d = GnssTripAccumulator.haversine(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_195.0, d, 500.0)
    }

    @Test
    fun accumulatesDistanceOnlyWhileMoving() {
        val acc = GnssTripAccumulator()
        acc.onFix(55.0, 37.0, 100.0, true, 10.0, 0.0, 0.0)
        acc.onFix(55.0, 37.001, 100.0, true, 10.0, 1.0, 1.0)
        val moving = acc.distanceMeters
        assertTrue("distance=$moving", moving in 55.0..75.0)

        // A stationary jump (speed below threshold) must not add distance.
        acc.onFix(55.0, 37.010, 100.0, true, 0.0, 2.0, 1.0)
        assertEquals(moving, acc.distanceMeters, 1e-6)
    }

    @Test
    fun accumulatesPositiveClimbAndSmoothsAltitude() {
        val acc = GnssTripAccumulator()
        acc.onFix(55.0, 37.0, 100.0, true, 10.0, 0.0, 0.0)
        var prev = acc.smoothedAltitude
        repeat(30) { i ->
            acc.onFix(55.0, 37.0 + i * 0.0001, 100.0 + (i + 1) * 10.0, true, 10.0, (i + 1).toDouble(), 1.0)
            assertTrue(acc.smoothedAltitude >= prev)
            prev = acc.smoothedAltitude
        }
        assertTrue("climb=${acc.tripClimbMeters}", acc.tripClimbMeters > 0.0)
        assertTrue("vario=${acc.variometer}", acc.variometer > 0.0)
    }

    @Test
    fun variometerGoesNegativeOnDescent() {
        val acc = GnssTripAccumulator()
        acc.onFix(55.0, 37.0, 500.0, true, 10.0, 0.0, 0.0)
        repeat(20) { i ->
            acc.onFix(55.0, 37.0, 500.0 - (i + 1) * 8.0, true, 10.0, (i + 1).toDouble(), 1.0)
        }
        assertTrue("vario=${acc.variometer}", acc.variometer < 0.0)
    }

    @Test
    fun stopLongerThanFifteenMinutesCrossesExactlyOnce() {
        val acc = GnssTripAccumulator()
        var crossings = 0
        var elapsed = 0.0
        // Feed ~16 minutes of stationary fixes in 5 s steps.
        repeat(200) {
            elapsed += 5.0
            if (acc.onFix(55.0, 37.0, 100.0, true, 0.0, elapsed, 5.0)) crossings++
        }
        assertEquals(1, crossings)
        assertTrue(acc.currentStopSeconds >= GnssTripAccumulator.STOP_THRESHOLD_SECONDS)
    }
}
