package dev.denza.apps.feature.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RmsBucketAggregatorTest {

    @Test
    fun closesBucketsAtTheConfiguredWindow() {
        val agg = RmsBucketAggregator(bucketSeconds = 0.45, capacity = 150)
        // Feed 1.0 s of constant agitation 2.0 in 0.1 s steps.
        repeat(10) { agg.add(2.0, 0.1) }
        // 1.0 s / 0.45 s => 2 closed buckets, remainder still open.
        assertEquals(2, agg.count)
        val snapshot = agg.snapshot()
        for (v in snapshot) assertEquals(2.0f, v, 0.001f)
    }

    @Test
    fun rmsFavoursSpikyOverSteadyForEqualMean() {
        val steady = RmsBucketAggregator(bucketSeconds = 0.45)
        val spiky = RmsBucketAggregator(bucketSeconds = 0.45)
        repeat(20) { steady.add(1.0, 0.1) }
        repeat(20) { i -> spiky.add(if (i % 2 == 0) 0.0 else 2.0, 0.1) }
        // RMS of {0,2,0,2,...} = sqrt(2) > 1.0, though both average 1.0.
        assertTrue(spiky.partialRmsOrLast() > steady.partialRmsOrLast())
    }

    @Test
    fun respectsCapacity() {
        val agg = RmsBucketAggregator(bucketSeconds = 0.1, capacity = 5)
        repeat(100) { agg.add(1.0, 0.1) }
        assertTrue(agg.count <= 5)
    }

    private fun RmsBucketAggregator.partialRmsOrLast(): Double {
        val snap = snapshot()
        return if (snap.isNotEmpty()) snap.last().toDouble() else partialRms
    }
}
