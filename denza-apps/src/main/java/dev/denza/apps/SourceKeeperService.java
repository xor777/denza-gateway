package dev.denza.apps;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.denza.disharebridge.DiShareProjectionBridge;

public class SourceKeeperService extends Service {
    private static final String TAG = "DenzaSourceKeeper";

    static final String[] SLOT_PACKAGES = {
            "com.tencent.qqlive.audiobox",
            "com.mgtv.auto",
            "cn.cmvideo.car.play",
            "com.youku.car",
            "com.tencent.qqlive",
            "com.qiyi.video.pad"
    };

    private static final Map<String, DiShareProjectionBridge> BRIDGES = new LinkedHashMap<>();

    public static void start(Context context) {
        if (!SimulcastIntegration.isEnabled(context)) {
            return;
        }
        try {
            context.startService(new Intent(context, SourceKeeperService.class));
        } catch (RuntimeException e) {
            Log.i(TAG, "start failed", e);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, SourceKeeperService.class));
        } catch (RuntimeException e) {
            Log.i(TAG, "stop failed", e);
        }
    }

    public static int registeredCount() {
        int count = 0;
        for (DiShareProjectionBridge bridge : BRIDGES.values()) {
            if (bridge != null && bridge.isStarted()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!SimulcastIntegration.isEnabled(this)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        ensureRegistered();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!SimulcastIntegration.isEnabled(this)) {
            return null;
        }
        ensureRegistered();
        return null;
    }

    @Override
    public void onDestroy() {
        for (DiShareProjectionBridge bridge : BRIDGES.values()) {
            if (bridge != null) {
                bridge.stop();
            }
        }
        BRIDGES.clear();
        super.onDestroy();
    }

    private void ensureRegistered() {
        // Reserve exactly as many native slots as the user selected casting apps, so
        // the native row shows one slot per Russian app with no leftover stock icons.
        int desired = Math.min(SimulcastApps.selectedCount(this), SLOT_PACKAGES.length);
        for (int i = 0; i < SLOT_PACKAGES.length; i++) {
            String packageName = SLOT_PACKAGES[i];
            if (i < desired) {
                DiShareProjectionBridge existing = BRIDGES.get(packageName);
                if (existing != null && existing.isStarted()) {
                    continue;
                }
                register(packageName);
            } else {
                DiShareProjectionBridge stale = BRIDGES.remove(packageName);
                if (stale != null) {
                    stale.stop();
                }
            }
        }
    }

    private void register(final String packageName) {
        DiShareProjectionBridge bridge = new DiShareProjectionBridge(
                getApplicationContext(),
                packageName,
                new DiShareProjectionBridge.Callback() {
                    @Override
                    public void onLog(String message) {
                        Log.i(TAG, packageName + " " + message);
                    }

                    @Override
                    public void onStarted(Bundle result) {
                        Log.i(TAG, packageName + " registered "
                                + DiShareProjectionBridge.bundleToString(result));
                    }

                    @Override
                    public void onFailed(String message) {
                        Log.i(TAG, packageName + " failed " + message);
                        BRIDGES.remove(packageName);
                    }

                    @Override
                    public void onStopped(String message) {
                        Log.i(TAG, packageName + " stopped " + message);
                    }
                });
        BRIDGES.put(packageName, bridge);
        bridge.startSourceOnly();
    }
}
