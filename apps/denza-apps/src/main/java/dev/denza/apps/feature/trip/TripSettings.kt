package dev.denza.apps.feature.trip

import android.annotation.SuppressLint
import android.content.Context

/**
 * Compile-time on/off switch for the whole trip panel.
 *
 * This is intentionally NOT a runtime preference. Flip [ENABLED] to `false` and
 * rebuild to cleanly remove the panel and everything it starts: the renderers,
 * the IMU/GNSS sensor hub, the location self-heal, and any permission requests.
 * When `false`, the free space below the feature cards is simply empty and
 * nothing is scheduled. There is deliberately no in-app toggle.
 */
object TripPanelFlag {
    const val ENABLED = true
}

/**
 * The only thing the trip panel persists is the last selected mode — never any
 * trip data, and no enable/disable flag (that is the compile-time [TripPanelFlag]).
 */
object TripSettings {
    private const val PREFS = "denza_trip_panel"
    private const val MODE = "mode"

    fun mode(context: Context): TripMode =
        TripMode.fromStorage(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(MODE, TripMode.FLIGHT.storageValue),
        )

    // Keep the persistent write boundary explicit, matching the other features.
    @SuppressLint("UseKtx")
    fun setMode(context: Context, mode: TripMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(MODE, mode.storageValue)
            .apply()
    }
}
