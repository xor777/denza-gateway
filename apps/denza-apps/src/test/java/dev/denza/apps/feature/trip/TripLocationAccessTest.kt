package dev.denza.apps.feature.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripLocationAccessTest {
    @Test
    fun alreadyGrantedAccessSkipsGrant() {
        var grantCalls = 0
        val repair = TripLocationAccessRepair(
            isGranted = { true },
            grant = { grantCalls += 1 },
        )

        assertEquals(TripLocationAccessResult.ALREADY_GRANTED, repair.ensure())
        assertEquals(0, grantCalls)
    }

    @Test
    fun missingAccessIsGrantedAndVerified() {
        var granted = false
        val repair = TripLocationAccessRepair(
            isGranted = { granted },
            grant = { granted = true },
        )

        assertEquals(TripLocationAccessResult.GRANTED, repair.ensure())
        assertTrue(granted)
    }

    @Test(expected = IllegalStateException::class)
    fun grantThatDoesNotChangeAccessFailsClosed() {
        TripLocationAccessRepair(
            isGranted = { false },
            grant = {},
        ).ensure()
    }

    @Test
    fun grantCommandsAreFineThenCoarseForThePackage() {
        assertEquals(
            listOf(
                "pm grant dev.denza.apps android.permission.ACCESS_FINE_LOCATION",
                "pm grant dev.denza.apps android.permission.ACCESS_COARSE_LOCATION",
            ),
            TripLocationAccessPolicy.grantCommands("dev.denza.apps"),
        )
    }
}
