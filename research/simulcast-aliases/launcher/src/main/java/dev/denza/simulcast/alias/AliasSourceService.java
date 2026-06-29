package dev.denza.simulcast.alias;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import dev.denza.disharebridge.DiShareProjectionBridge;

public class AliasSourceService extends Service {
    private static final String TAG = "DenzaSimulcastSource";

    private static DiShareProjectionBridge sourceBridge;

    public static void start(Context context) {
        try {
            context.startService(new Intent(context, AliasSourceService.class));
        } catch (RuntimeException e) {
            Log.i(TAG, "start service failed", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureRegistered();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        ensureRegistered();
        return null;
    }

    @Override
    public void onDestroy() {
        if (sourceBridge != null) {
            sourceBridge.stop();
            sourceBridge = null;
        }
        super.onDestroy();
    }

    private void ensureRegistered() {
        if (sourceBridge != null && sourceBridge.isStarted()) {
            return;
        }
        sourceBridge = new DiShareProjectionBridge(
                getApplicationContext(),
                BuildConfig.APPLICATION_ID,
                new DiShareProjectionBridge.Callback() {
                    @Override
                    public void onLog(String message) {
                        Log.i(TAG, message);
                    }

                    @Override
                    public void onStarted(Bundle result) {
                        Log.i(TAG, "source registered "
                                + DiShareProjectionBridge.bundleToString(result));
                    }

                    @Override
                    public void onFailed(String message) {
                        Log.i(TAG, "source failed " + message);
                        sourceBridge = null;
                    }

                    @Override
                    public void onStopped(String message) {
                        Log.i(TAG, "source stopped " + message);
                    }
                });
        sourceBridge.startSourceOnly();
    }
}
