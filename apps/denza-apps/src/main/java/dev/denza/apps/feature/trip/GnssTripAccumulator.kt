package dev.denza.apps.feature.trip

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Derives journey geometry from ~1 Hz standard-Android GNSS fixes.
 *
 * Nothing here is persisted. Ground speed is used internally only (distance
 * gating, stop detection) and is deliberately never exposed for display — the
 * cluster/HUD/navigator already show speed.
 *
 * Pure and JVM-testable.
 */
class GnssTripAccumulator(
    /** Altitude low-pass time constant, seconds. */
    private val altitudeTau: Double = 4.0,
    /** Variometer low-pass time constant, seconds. */
    private val varioTau: Double = 3.0,
    /** Below this ground speed (m/s) the car is treated as stopped. */
    private val stopSpeed: Double = 0.5,
    /** Rolling window kept for the mode-1 elevation thread, seconds. */
    private val elevationWindowSeconds: Double = 100.0,
    /** Minimum spacing between retained thread points (meters or seconds). */
    private val routeMinMeters: Double = 25.0,
    private val routeMinSeconds: Double = 4.0,
    private val routeCapacity: Int = 2000,
) {
    private var hasPrev = false
    private var prevLat = 0.0
    private var prevLon = 0.0

    var distanceMeters: Double = 0.0
        private set
    var tripClimbMeters: Double = 0.0
        private set

    private var altitudeSeeded = false
    var smoothedAltitude: Double = 0.0
        private set
    var hasAltitude: Boolean = false
        private set
    var variometer: Double = 0.0
        private set

    /** Seconds continuously stopped; never ends the trip, only labels it. */
    var currentStopSeconds: Double = 0.0
        private set
    private var stopLabelArmed = true

    /** Positive climb over the current elevation window (drives "набор +N м"). */
    var windowClimbMeters: Double = 0.0
        private set

    private val elevation = ArrayDeque<ElevationSample>()
    private val routePoints = ArrayList<RoutePoint>()
    private var lastRouteElapsed = -1.0e9
    private var lastRouteLat = 0.0
    private var lastRouteLon = 0.0
    private var altMinWindow = Double.MAX_VALUE
    private var altMaxWindow = -Double.MAX_VALUE

    /**
     * Feed one fix.
     *
     * @return true when a new 15-minute stop threshold was crossed this update.
     */
    fun onFix(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        hasAltitudeFix: Boolean,
        speed: Double,
        elapsedSeconds: Double,
        dt: Double,
    ): Boolean {
        val step = dt.coerceIn(0.0, 5.0)

        // Distance: accumulate only while moving so parked GPS jitter does not drift.
        if (hasPrev && speed >= stopSpeed) {
            val d = haversine(prevLat, prevLon, latitude, longitude)
            if (d < 400.0) distanceMeters += d // reject single-fix GPS glitches
        }
        prevLat = latitude
        prevLon = longitude
        hasPrev = true

        // Altitude smoothing + climb + variometer.
        if (hasAltitudeFix) {
            hasAltitude = true
            if (!altitudeSeeded) {
                smoothedAltitude = altitude
                altitudeSeeded = true
            } else {
                val prev = smoothedAltitude
                val a = 1.0 - exp(-step / altitudeTau)
                smoothedAltitude += (altitude - smoothedAltitude) * a
                val delta = smoothedAltitude - prev
                if (delta > 0) tripClimbMeters += delta
                val rate = if (step > 0) delta / step else 0.0
                val av = 1.0 - exp(-step / varioTau)
                variometer += (rate - variometer) * av
                pushWindowClimb(elapsedSeconds, max(0.0, delta))
            }
            pushElevation(elapsedSeconds, smoothedAltitude)
        }

        // Heading/course now lives in the engine's CourseTracker (fed the bearing
        // + speed + IMU yaw), so this accumulator stays position/elevation-only.

        // Stop detection: label crossings at 15 minutes, re-arm on movement.
        val crossed: Boolean
        if (speed < stopSpeed) {
            val before = currentStopSeconds
            currentStopSeconds += step
            crossed = stopLabelArmed && before < STOP_THRESHOLD_SECONDS &&
                currentStopSeconds >= STOP_THRESHOLD_SECONDS
            if (crossed) stopLabelArmed = false
        } else {
            currentStopSeconds = 0.0
            stopLabelArmed = true
            crossed = false
        }

        return crossed
    }

    /** Append a decimated route point (called by the engine with fused colour/energy). */
    fun maybeAddRoutePoint(
        elapsedSeconds: Double,
        latitude: Double,
        longitude: Double,
        timeColor: Double,
        energy: Double,
    ) {
        val movedEnough = !hasRoutePoints() ||
            haversine(lastRouteLat, lastRouteLon, latitude, longitude) >= routeMinMeters ||
            elapsedSeconds - lastRouteElapsed >= routeMinSeconds
        if (!movedEnough) return
        val shape = normalizedShape()
        routePoints.add(RoutePoint(elapsedSeconds, shape, timeColor, energy))
        while (routePoints.size > routeCapacity) routePoints.removeAt(0)
        lastRouteElapsed = elapsedSeconds
        lastRouteLat = latitude
        lastRouteLon = longitude
    }

    private fun hasRoutePoints(): Boolean = routePoints.isNotEmpty()

    /** Bounded 0..1 shape from where the smoothed altitude sits in its window band. */
    private fun normalizedShape(): Double {
        if (!hasAltitude || altMaxWindow - altMinWindow < 1e-3) return 0.5
        return ((smoothedAltitude - altMinWindow) / (altMaxWindow - altMinWindow)).coerceIn(0.0, 1.0)
    }

    private fun pushElevation(elapsedSeconds: Double, altitudeMeters: Double) {
        elevation.addLast(ElevationSample(elapsedSeconds, altitudeMeters))
        while (elevation.isNotEmpty() &&
            elapsedSeconds - elevation.first().elapsedSeconds > elevationWindowSeconds
        ) {
            elevation.removeFirst()
        }
        altMinWindow = Double.MAX_VALUE
        altMaxWindow = -Double.MAX_VALUE
        for (s in elevation) {
            altMinWindow = min(altMinWindow, s.altitudeMeters)
            altMaxWindow = max(altMaxWindow, s.altitudeMeters)
        }
    }

    private val climbWindow = ArrayDeque<ElevationSample>()

    private fun pushWindowClimb(elapsedSeconds: Double, positiveDelta: Double) {
        if (positiveDelta > 0) climbWindow.addLast(ElevationSample(elapsedSeconds, positiveDelta))
        while (climbWindow.isNotEmpty() &&
            elapsedSeconds - climbWindow.first().elapsedSeconds > CLIMB_WINDOW_SECONDS
        ) {
            climbWindow.removeFirst()
        }
        var sum = 0.0
        for (s in climbWindow) sum += s.altitudeMeters
        windowClimbMeters = sum
    }

    /** Reset the rolling window climb once its event has fired. */
    fun consumeWindowClimb() {
        climbWindow.clear()
        windowClimbMeters = 0.0
    }

    fun elevationSamples(): List<ElevationSample> = elevation.toList()
    fun elevationCount(): Int = elevation.size
    fun routePointCount(): Int = routePoints.size
    fun routePoints(): List<RoutePoint> = routePoints.toList()

    /**
     * Copy the retained route points into caller-owned buffers (no allocation in
     * the draw path). Returns the number written. Values are shape (0..1),
     * time-of-day colour key (0..1) and IMU energy (0..1).
     */
    fun copyRouteInto(shapeOut: FloatArray, colorOut: FloatArray, energyOut: FloatArray): Int {
        val n = minOf(routePoints.size, shapeOut.size, colorOut.size, energyOut.size)
        val start = routePoints.size - n
        for (i in 0 until n) {
            val p = routePoints[start + i]
            shapeOut[i] = p.shape.toFloat()
            colorOut[i] = p.timeColor.toFloat()
            energyOut[i] = p.energy.toFloat()
        }
        return n
    }

    /** Copy elevation altitudes into [out], newest last; returns count written. */
    fun copyElevationInto(out: FloatArray): Int {
        var i = 0
        for (s in elevation) {
            if (i >= out.size) break
            out[i++] = s.altitudeMeters.toFloat()
        }
        return i
    }

    companion object {
        const val STOP_THRESHOLD_SECONDS = 15.0 * 60.0
        const val CLIMB_WINDOW_SECONDS = 40.0
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val dPhi = Math.toRadians(lat2 - lat1)
            val dLambda = Math.toRadians(lon2 - lon1)
            val a = sin(dPhi / 2) * sin(dPhi / 2) +
                cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_M * c
        }
    }
}
