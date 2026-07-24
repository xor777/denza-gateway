package dev.denza.apps.feature.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgitationTrackerTest {

    @Test
    fun smoothRideScoresHigh() {
        val tracker = AgitationTracker()
        repeat(400) { tracker.update(agitation = 0.1, verticalAbs = 0.1, dt = 0.1) }
        assertTrue("score=${tracker.smoothnessScore}", tracker.smoothnessScore >= 90)
    }

    @Test
    fun sustainedAgitationLowersScoreDeterministically() {
        val tracker = AgitationTracker()
        // agitation 1.0 m/s^2 sustained => score ~= 100 - 42 = 58.
        repeat(400) { tracker.update(agitation = 1.0, verticalAbs = 0.2, dt = 0.1) }
        assertEquals(58.0, tracker.smoothnessScore.toDouble(), 2.0)
    }

    @Test
    fun calmTimerGrowsWhileSmoothAndResetsOnImpulse() {
        val tracker = AgitationTracker()
        repeat(50) { tracker.update(agitation = 0.3, verticalAbs = 0.1, dt = 0.1) }
        assertTrue(tracker.calmSeconds > 4.0)
        // A splash above the 1.6 m/s^2 impulse threshold resets it.
        tracker.update(agitation = 3.0, verticalAbs = 1.0, dt = 0.1)
        assertEquals(0.0, tracker.calmSeconds, 1e-9)
    }
}
