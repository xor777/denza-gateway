package dev.denza.apps.feature.trip

import android.annotation.SuppressLint
import android.content.Context

/**
 * Persistent state for the trip panel. The ONLY things this feature persists are
 * the runtime feature flag and the last selected mode — never any trip data.
 *
 * The flag defaults ON. To turn the panel off quickly without rebuilding, the
 * user long-presses the panel and confirms; that flips [ENABLED] to false and the
 * space simply goes empty (no sensors/location run). To re-enable without a
 * rebuild, clear the value from this preference file, e.g.:
 *
 *   adb shell run-as dev.denza.apps \
 *     rm shared_prefs/denza_trip_panel.xml
 *
 * (or reinstall / clear app data — the default is ON), then relaunch the app.
 */
object TripSettings {
    private const val PREFS = "denza_trip_panel"
    private const val ENABLED = "enabled"
    private const val MODE = "mode"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

    // Keep the persistent write boundary explicit, matching the other features.
    @SuppressLint("UseKtx")
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED, enabled)
            .apply()
    }

    fun mode(context: Context): TripMode =
        TripMode.fromStorage(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(MODE, TripMode.FLIGHT.storageValue),
        )

    @SuppressLint("UseKtx")
    fun setMode(context: Context, mode: TripMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(MODE, mode.storageValue)
            .apply()
    }
}
