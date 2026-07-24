package dev.denza.apps.feature.trip

/**
 * Pure, Android-free data model for the trip panel.
 *
 * The whole feature is fed exclusively by product-usable inputs (standard Android
 * GNSS, standard IMU sensors, offline sun math, and the app's existing validated
 * Yandex accessibility guidance). See docs/vehicle-data-findings.md for the
 * boundary these types deliberately stay inside.
 */

/** The three panel concepts the user cycles through by tapping the panel. */
enum class TripMode(val storageValue: Int) {
    FLIGHT(0),
    GLASS(1),
    THREAD(2),
    ;

    fun next(): TripMode = entries[(ordinal + 1) % entries.size]

    fun previous(): TripMode = entries[(ordinal - 1 + entries.size) % entries.size]

    companion object {
        fun fromStorage(value: Int): TripMode = entries.firstOrNull { it.storageValue == value } ?: FLIGHT
    }
}

/**
 * Result of projecting one raw IMU sample onto vehicle axes.
 *
 * `vertical`/`horizontalMagnitude` are calibrated from the smoothed gravity
 * vector and are trustworthy as soon as gravity settles. `lateral`/`longitudinal`
 * are only meaningful once [calibrated] is true; before convergence the engine
 * falls back to [horizontalMagnitude] (magnitude-only agitation).
 */
data class AxisReading(
    /** Linear (gravity-removed) acceleration along the smoothed up axis, m/s^2. Positive = up. */
    val vertical: Double,
    /** Magnitude of the horizontal linear acceleration, m/s^2. */
    val horizontalMagnitude: Double,
    /** Lateral (cornering) acceleration, m/s^2. Only meaningful when [calibrated]. */
    val lateral: Double,
    /** Longitudinal (accel/brake) acceleration, m/s^2. Only meaningful when [calibrated]. */
    val longitudinal: Double,
    /** Yaw rate about the vertical axis, rad/s. */
    val yawRate: Double,
    /** True once the lateral/longitudinal split has converged from yaw correlation. */
    val calibrated: Boolean,
)

/** A single smoothed elevation sample kept in the rolling ~100 s window (mode 1). */
data class ElevationSample(val elapsedSeconds: Double, val altitudeMeters: Double)

/**
 * One decimated point of the whole-trip thread (mode 3). Nothing here is
 * persisted; the list lives and dies with the session.
 */
data class RoutePoint(
    val elapsedSeconds: Double,
    /** Bounded, aesthetic vertical shape value in 0..1 (from altitude variation). */
    val shape: Double,
    /** Time-of-day colour key in 0..1 (dawn 0 -> day -> golden -> evening -> night 1). */
    val timeColor: Double,
    /** IMU energy at that moment in 0..1, modulating thickness/glow. */
    val energy: Double,
)

/**
 * A drifting/positioned event caption with a deterministic trigger. The renderer
 * turns [kind] + [value] into mode-appropriate Russian text (a CLIMB is "набор"
 * in mode 1 and "подъём" in mode 3), so the model stays presentation-free.
 */
data class TripEvent(
    val kind: TripEventKind,
    /** Meters for CLIMB/DESCENT/CREST; minutes for STOP; unused otherwise. */
    val value: Double,
    /** Trip time (seconds) at which the event fired; anchors both layouts. */
    val bornElapsedSeconds: Double,
    /** 0/1 lane to avoid overlap in the drifting/thread layout. */
    val lane: Int,
)

enum class TripEventKind { CLIMB, DESCENT, TURN, CALM, CREST, STOP, SERPENTINE }

/** Remaining-route figures sourced only from validated Yandex guidance. */
data class GuidanceRemaining(val distanceMeters: Int?, val timeSeconds: Int?)

/** Offline sun facts for the current position. */
data class SunInfo(
    val hasPosition: Boolean,
    val azimuthDeg: Double,
    val elevationDeg: Double,
    /** True when the next daylight boundary is a sunset (daytime), false before dawn. */
    val nextIsSunset: Boolean,
    /** Local clock label of the next boundary, e.g. "19:48". Empty when unknown. */
    val nextEventLabel: String,
    /** Seconds until the next boundary, or -1 when unknown / polar day-night. */
    val countdownSeconds: Long,
)
