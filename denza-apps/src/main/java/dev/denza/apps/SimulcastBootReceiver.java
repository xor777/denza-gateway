package dev.denza.apps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SimulcastBootReceiver extends BroadcastReceiver {
    private static final String TAG = "DenzaSimulcastBoot";
    private static final String ACTION_DISHARE_DIALOG_HOME = "action.byd.dishare.DIALOG_HOME";
    private static final String ACTION_DISHARE_DIALOG_LAUNCHER =
            "action.byd.dishare.DIALOG_LAUNCHER";
    private static final String ACTION_DISHARE_DIALOG_CLOSE =
            "action.byd.dishare.DIALOG_CLOSE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        Log.i(TAG, "action=" + action);
        boolean enabled = SimulcastIntegration.isEnabled(context);
        if (enabled) {
            SourceKeeperService.start(context);
        }

        if (ACTION_DISHARE_DIALOG_HOME.equals(action)
                || ACTION_DISHARE_DIALOG_LAUNCHER.equals(action)
                || ACTION_DISHARE_DIALOG_CLOSE.equals(action)) {
            if (enabled) {
                forwardToOverlay(context, action, intent);
            }
            return;
        }
        if (SimulcastOverlayService.ACTION_HIDE.equals(action)) {
            forwardToOverlay(context, action, intent);
            return;
        }
        if (SimulcastOverlayService.ACTION_ARM_APP_CHANGE.equals(action)) {
            if (enabled) {
                forwardToOverlay(context, action, intent);
            }
            return;
        }
        if (SimulcastOverlayService.ACTION_STOP_CURRENT.equals(action)) {
            forwardToOverlay(context, action, intent);
            return;
        }
        if (SimulcastOverlayService.ACTION_SHOW.equals(action)) {
            if (enabled) {
                forwardToOverlay(context, action, intent);
            }
            return;
        }
        if (SimulcastOverlayService.ACTION_START_TARGET.equals(action)) {
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
