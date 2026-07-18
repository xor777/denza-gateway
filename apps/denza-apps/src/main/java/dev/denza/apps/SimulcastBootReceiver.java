package dev.denza.apps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import dev.denza.apps.core.DenzaRuntimeCoordinator;

/**
 * Forwards DiShare dialog broadcasts (to keep the floating exit control in sync) and
 * the debug start/stop actions to {@link SimulcastOverlayService}. The Simulcast
 * picker overlay itself runs from {@link SimulcastAccessibilityService}.
 */
public class SimulcastBootReceiver extends BroadcastReceiver {
    private static final String TAG = "DenzaSimulcastBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return;
        }
        Log.i(TAG, "action=" + action);
        boolean enabled = SimulcastIntegration.isEnabled(context);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            try {
                DenzaRuntimeCoordinator.INSTANCE.recover(context);
            } catch (RuntimeException e) {
                Log.i(TAG, "runtime recovery failed", e);
            }
            return;
        }

        if (SimulcastOverlayService.ACTION_DISHARE_DIALOG_HOME.equals(action)
                || SimulcastOverlayService.ACTION_DISHARE_DIALOG_LAUNCHER.equals(action)
                || SimulcastOverlayService.ACTION_DISHARE_DIALOG_CLOSE.equals(action)) {
            if (enabled) {
                forwardToOverlay(context, action, intent);
            }
            return;
        }
        if (SimulcastOverlayService.ACTION_START_TARGET.equals(action)
                || SimulcastOverlayService.ACTION_STOP_CURRENT.equals(action)) {
            forwardToOverlay(context, action, intent);
        }
    }

    private void forwardToOverlay(Context context, String action, Intent source) {
        Intent intent = new Intent(context, SimulcastOverlayService.class).setAction(action);
        if (source != null && source.getExtras() != null) {
            intent.putExtras(source.getExtras());
        }
        try {
            context.startService(intent);
        } catch (RuntimeException e) {
            Log.i(TAG, "start overlay action failed: " + action, e);
        }
    }
}
