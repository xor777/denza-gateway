package dev.denza.apps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureModelsTest {
    @Test
    fun recoverableFailureKeepsDesiredStateEnabled() {
        val ready = FeatureReducer.ready(FeatureId.SIMULCAST)

        val recovering = FeatureReducer.recovering(ready, "Восстанавливаю", "ADB timeout")

        assertTrue(recovering.desiredEnabled)
        assertEquals(FeatureStatus.RECOVERING, recovering.status)
        assertEquals("ADB timeout", recovering.details)
    }

    @Test
    fun disabledFeatureCannotEnterRecovery() {
        val disabled = FeatureReducer.disabled(FeatureId.MIRRORS)

        val result = FeatureReducer.recovering(disabled, "ignored")

        assertFalse(result.desiredEnabled)
        assertEquals(FeatureStatus.OFF, result.status)
    }

    @Test
    fun readyAndActiveAreWorkingStates() {
        assertTrue(FeatureReducer.ready(FeatureId.NAVIGATION).isWorking)
        assertTrue(FeatureReducer.ready(FeatureId.NAVIGATION, active = true).isWorking)
        assertFalse(FeatureReducer.starting(FeatureId.NAVIGATION).isWorking)
    }

    @Test
    fun userActionDoesNotResetDesiredState() {
        val starting = FeatureReducer.starting(FeatureId.SIMULCAST)

        val result = FeatureReducer.needsAction(starting, "Нужно действие")

        assertTrue(result.desiredEnabled)
        assertEquals(FeatureStatus.NEEDS_ACTION, result.status)
    }
}
