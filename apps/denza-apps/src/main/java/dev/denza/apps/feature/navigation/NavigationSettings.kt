package dev.denza.apps.feature.navigation

import android.annotation.SuppressLint
import android.content.Context
import dev.denza.apps.feature.cluster.ClusterMapPlacement

object NavigationSettings {
    private const val PREFS = "denza_navigation"
    private const val SELECTED_PACKAGE = "selected_package"
    private const val MAP_PLACEMENT = "map_placement"

    fun selectedPackage(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SELECTED_PACKAGE, null)
        return saved
            ?.takeIf(NavigationAppPolicy::isAllowed)
            ?.takeIf { isInstalled(context, it) }
            ?: installedApps(context).firstOrNull()?.packageName
            ?: NavigationAppPolicy.DEFAULT_PACKAGE
    }

    // Keep validated preference writes explicit at the navigation policy boundary.
    @SuppressLint("UseKtx")
    fun setSelectedPackage(context: Context, packageName: String) {
        require(NavigationAppPolicy.isAllowed(packageName)) { "unsupported navigation package" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SELECTED_PACKAGE, packageName)
            .apply()
    }

    fun placement(context: Context): ClusterMapPlacement = runCatching {
        ClusterMapPlacement.valueOf(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(MAP_PLACEMENT, ClusterMapPlacement.FULL.name)
                ?: ClusterMapPlacement.FULL.name,
        )
    }.getOrDefault(ClusterMapPlacement.FULL)

    @SuppressLint("UseKtx")
    fun setPlacement(context: Context, placement: ClusterMapPlacement) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(MAP_PLACEMENT, placement.name)
            .apply()
    }

    fun installedApps(context: Context): List<NavigationAppDefinition> =
        NavigationAppPolicy.supported.filter { isInstalled(context, it.packageName) }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }
}
