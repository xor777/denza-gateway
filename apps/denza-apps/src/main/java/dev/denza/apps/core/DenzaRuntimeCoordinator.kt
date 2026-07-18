package dev.denza.apps.core

import android.content.Context
import dev.denza.apps.DenzaAppRepository

/** Restores desired, non-navigation features after boot or an APK replacement. */
object DenzaRuntimeCoordinator {
    fun recover(context: Context) {
        DenzaAppRepository.recoverEnabledFeatures(context.applicationContext)
    }
}
