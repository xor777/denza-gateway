package dev.denza.apps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import dev.denza.apps.feature.trip.TripSettings
import dev.denza.apps.ui.DenzaAppsRoot

class MainActivity : ComponentActivity() {

    private var locationRequested = false

    // The trip panel uses GNSS when granted and degrades gracefully otherwise.
    // We ask at most once per process; a denial is respected (the panel shows a
    // muted hint and keeps running its IMU-only elements).
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            DenzaAppRepository.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        DenzaAppRepository.initialize(this)
        setContent(
            content = {
                DenzaAppsRoot(
                    state = DenzaAppRepository.state,
                    onToggleSimulcast = DenzaAppRepository::setSimulcastEnabled,
                    onLaunchSimulcast = DenzaAppRepository::launchSimulcast,
                    onRepairSimulcast = DenzaAppRepository::repairSimulcast,
                    onToggleMirrors = DenzaAppRepository::setMirrorsEnabled,
                    onMirrorsPosition = DenzaAppRepository::setMirrorsPosition,
                    onMirrorsProcessing = DenzaAppRepository::setMirrorsProcessing,
                    onPreviewMirrors = DenzaAppRepository::previewMirrors,
                    onNavigationAction = DenzaAppRepository::performNavigationAction,
                    onNavigationAutomatic = DenzaAppRepository::setNavigationAutomatic,
                    onNavigationSteeringWheelButton =
                        DenzaAppRepository::setNavigationSteeringWheelButton,
                    onNavigationPlacement = DenzaAppRepository::setNavigationPlacement,
                    onChooseNavigationApp = DenzaAppRepository::showNavigationAppPicker,
                    onCloseNavigationPicker = DenzaAppRepository::hideNavigationAppPicker,
                    onSelectNavigationApp = DenzaAppRepository::selectNavigationApp,
                    onToggleSplitScreen = DenzaAppRepository::setSplitScreenEnabled,
                    onToggleHudGuidance = DenzaAppRepository::setHudGuidanceEnabled,
                    onSelectClusterDisplay = DenzaAppRepository::selectClusterDisplay,
                    onRefreshScreenDiagnostics = DenzaAppRepository::refreshScreenDiagnostics,
                    onChooseApps = DenzaAppRepository::showAppPicker,
                    onCloseAppPicker = DenzaAppRepository::hideAppPicker,
                    onToggleApp = DenzaAppRepository::toggleAppSelection,
                    onChooseFseApp = DenzaAppRepository::showFseInstallerPicker,
                    onCloseFseInstallerPicker = DenzaAppRepository::hideFseInstallerPicker,
                    onInstallFseApp = DenzaAppRepository::installOnPassengerScreen,
                    onSetTripPanelEnabled = DenzaAppRepository::setTripPanelEnabled,
                )
            },
        )
    }

    override fun onResume() {
        super.onResume()
        DenzaAppRepository.refresh()
        SimulcastOverlayService.hide(this)
        maybeRequestLocation()
    }

    private fun maybeRequestLocation() {
        if (locationRequested) return
        if (!TripSettings.isEnabled(this)) return
        val granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        locationRequested = true
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onPause() {
        super.onPause()
        if (SimulcastIntegration.isEnabled(this) &&
            SimulcastIntegration.getLastTargetPackage(this) != null
        ) {
            SimulcastOverlayService.showActiveExit(this)
        }
    }
}
