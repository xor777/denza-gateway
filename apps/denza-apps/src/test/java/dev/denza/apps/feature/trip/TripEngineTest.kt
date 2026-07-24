package dev.denza.apps.feature.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEngineTest {

    @Test
    fun elapsedTracksMonotonicStart() {
        val engine = TripEngine(startElapsedMs = 1_000L)
        engine.onTick(6_000L)
        assertEquals(5.0, engine.elapsedSeconds, 1e-6)
    }

    @Test
    fun guidanceIsFailClosed() {
        val engine = TripEngine(startElapsedMs = 0L)
        // No valid guidance -> null (row falls back to "В пути").
        engine.onGuidance(distanceMeters = null, timeSeconds = null, valid = false, nowElapsedMs = 100L)
        assertNull(engine.guidance())

        // Valid guidance -> exposed.
        engine.onGuidance(distanceMeters = 4200, timeSeconds = 600, valid = true, nowElapsedMs = 200L)
        val g = engine.guidance()
        assertNotNull(g)
        assertEquals(4200, g!!.distanceMeters)
        assertEquals(600, g.timeSeconds)

        // Guidance goes away again -> dropped immediately.
        engine.onGuidance(distanceMeters = null, timeSeconds = null, valid = false, nowElapsedMs = 300L)
        assertNull(engine.guidance())
    }

    @Test
    fun distanceGrowsFromMovingFixes() {
        val engine = TripEngine(startElapsedMs = 0L)
        engine.onLocation(0L, 0L, 180, 55.0, 37.0, 100.0, true, 0.0, false, 10.0, 5.0)
        engine.onLocation(1_000L, 1_000L, 180, 55.0, 37.002, 100.0, true, 0.0, false, 10.0, 5.0)
        assertTrue("distance=${engine.distanceMeters()}", engine.distanceMeters() > 100.0)
    }

    @Test
    fun climbEventFiresPastFortyMeterWindow() {
        val engine = TripEngine(startElapsedMs = 0L)
        // Rising altitude quickly so the smoothed climb over the window exceeds 40 m.
        var t = 0L
        var alt = 100.0
        var elapsed = 0.0
        engine.onLocation(t, t, 180, 55.0, 37.0, alt, true, 0.0, false, 8.0, 0.0)
        repeat(30) {
            t += 1_000L
            elapsed += 1.0
            alt += 25.0
            engine.onLocation(t, t, 180, 55.0, 37.0 + elapsed * 0.0002, alt, true, 0.0, false, 8.0, 1.0)
        }
        val fired = (0 until engine.eventCount()).any { engine.eventAt(it).kind == TripEventKind.CLIMB }
        assertTrue("events=${engine.events().map { it.kind }}", fired)
    }
}
