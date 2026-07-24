package dev.denza.apps.feature.trip

import kotlin.math.sqrt

/**
 * Aggregates a continuous agitation signal into fixed-duration RMS buckets for
 * the body-motion ribbon (mode 2).
 *
 * The ribbon must advance ONE bucket at a time (never per-frame shifting) — this
 * is what removes the visual jitter the earlier per-sample version had. The
 * renderer therefore draws [buckets] plus the single in-progress [partialRms]
 * head, and only when a bucket closes does the strip step forward.
 *
 * Pure and JVM-testable.
 */
class RmsBucketAggregator(
    /** Bucket window in seconds (0.4-0.5 s per the spec). */
    private val bucketSeconds: Double = 0.45,
    /** How many closed buckets to retain (~last minute-plus of motion). */
    private val capacity: Int = 150,
) {
    private val values = ArrayDeque<Float>()
    private var sumSq = 0.0
    private var weight = 0.0

    /** Feed one agitation magnitude sampled over [dt] seconds. */
    fun add(agitation: Double, dt: Double) {
        val step = dt.coerceIn(0.0, 0.2)
        if (step <= 0.0) return
        sumSq += agitation * agitation * step
        weight += step
        while (weight >= bucketSeconds) {
            val rms = if (weight > 0) sqrt(sumSq / weight) else 0.0
            values.addLast(rms.toFloat())
            while (values.size > capacity) values.removeFirst()
            sumSq = 0.0
            weight = 0.0
        }
    }

    /** RMS of the still-open bucket (the advancing head), or 0 when empty. */
    val partialRms: Double
        get() = if (weight > 0) sqrt(sumSq / weight) else 0.0

    val count: Int get() = values.size

    /** Snapshot of closed buckets, oldest first. */
    fun snapshot(): FloatArray = values.toFloatArray()

    /** Copy closed buckets into [out]; returns the number written. No allocation. */
    fun copyInto(out: FloatArray): Int {
        var i = 0
        for (v in values) {
            if (i >= out.size) break
            out[i++] = v
        }
        return i
    }
}
