package dev.denza.apps.feature.mirrors

import org.junit.Assert.assertEquals
import org.junit.Test

class AvcInitializationGateTest {
    @Test
    fun rejectedDisplayDoesNotSetViewpointOrReportReady() {
        val events = mutableListOf<String>()

        AvcInitializationGate.run(object : AvcInitializationGate.Attempt {
            override fun initDisplay(): Boolean {
                events += "init"
                return false
            }

            override fun setViewpoint() {
                events += "viewpoint"
            }

            override fun onReady() {
                events += "ready"
            }

            override fun onRejected() {
                events += "rejected"
            }
        })

        assertEquals(listOf("init", "rejected"), events)
    }

    @Test
    fun acceptedDisplaySetsViewpointBeforeReportingReady() {
        val events = mutableListOf<String>()

        AvcInitializationGate.run(object : AvcInitializationGate.Attempt {
            override fun initDisplay(): Boolean {
                events += "init"
                return true
            }

            override fun setViewpoint() {
                events += "viewpoint"
            }

            override fun onReady() {
                events += "ready"
            }

            override fun onRejected() {
                events += "rejected"
            }
        })

        assertEquals(listOf("init", "viewpoint", "ready"), events)
    }
}
