package dev.denza.apps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dev.denza.apps.ui.DenzaAppsRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        DenzaAppRepository.initialize(this)
        setContent(
            content = {
                DenzaAppsRoot(
                    state = DenzaAppRepository.state,
                    onToggleSimulcast = DenzaAppRepository::setSimulcastEnabled,
                    onRepairSimulcast = DenzaAppRepository::repairSimulcast,
                    onToggleMirrors = DenzaAppRepository::setMirrorsEnabled,
                    onMirrorsPosition = DenzaAppRepository::setMirrorsPosition,
                    onMirrorsProcessing = DenzaAppRepository::setMirrorsProcessing,
                    onPreviewMirrors = DenzaAppRepository::previewMirrors,
                    onNavigationAction = DenzaAppRepository::performNavigationAction,
                    onSelectClusterDisplay = DenzaAppRepository::selectClusterDisplay,
                    onChooseApps = DenzaAppRepository::showAppPicker,
                    onCloseAppPicker = DenzaAppRepository::hideAppPicker,
                    onToggleApp = DenzaAppRepository::toggleAppSelection,
                )
            },
        )
    }

    override fun onResume() {
        super.onResume()
        DenzaAppRepository.refresh()
        SimulcastOverlayService.hide(this)
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
