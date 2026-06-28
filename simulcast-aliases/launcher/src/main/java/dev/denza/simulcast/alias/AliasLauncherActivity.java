package dev.denza.simulcast.alias;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import dev.denza.disharebridge.DiShareProjectionBridge;

public class AliasLauncherActivity extends Activity {
    private static final String TAG = "DenzaSimulcastAlias";
    private static DiShareProjectionBridge activeBridge;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean launchInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchTarget();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        launchTarget();
    }

    private void launchTarget() {
        if (launchInProgress) {
            return;
        }
        launchInProgress = true;

        final boolean launchedBySimulcast = getDisplay() != null
                && getDisplay().getDisplayId() != Display.DEFAULT_DISPLAY;
        if (!launchedBySimulcast) {
            AliasSourceService.start(this);
        }
        Log.i(TAG, "launch target=" + BuildConfig.TARGET_PACKAGE
                + " display=" + (getDisplay() == null ? "null" : getDisplay().getDisplayId())
                + " launchedBySimulcast=" + launchedBySimulcast);

        final DiShareProjectionBridge bridge = new DiShareProjectionBridge(
                getApplicationContext(),
                BuildConfig.TARGET_PACKAGE,
                new DiShareProjectionBridge.Callback() {
                    @Override
                    public void onLog(String message) {
                        Log.i(TAG, message);
                    }

                    @Override
                    public void onStarted(Bundle result) {
                        Log.i(TAG, "DiShare bridge started "
                                + DiShareProjectionBridge.bundleToString(result));
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                finishQuietly();
                            }
                        }, 500L);
                    }

                    @Override
                    public void onFailed(String message) {
                        Log.i(TAG, "DiShare bridge failed " + message);
                        if (launchedBySimulcast) {
                            finishQuietly();
                        } else {
                            launchFallback();
                        }
                    }

                    @Override
                    public void onStopped(String message) {
                        Log.i(TAG, "DiShare bridge stopped " + message);
                    }
        });
        activeBridge = bridge;
        if (launchedBySimulcast) {
            bridge.startLikeCurrentShare();
        } else {
            bridge.start();
        }
    }

    private void launchFallback() {
        Intent launchIntent = getPackageManager()
                .getLaunchIntentForPackage(BuildConfig.TARGET_PACKAGE);
        if (launchIntent == null && !BuildConfig.FALLBACK_URI.isEmpty()) {
            launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.FALLBACK_URI));
        }
        if (launchIntent == null) {
            Toast.makeText(this, "Целевое приложение не найдено", Toast.LENGTH_SHORT).show();
            finishQuietly();
            return;
        }

        if (getDisplay() != null && getDisplay().getDisplayId() != Display.DEFAULT_DISPLAY) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        } else {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        try {
            startActivity(launchIntent);
        } catch (RuntimeException e) {
            Log.i(TAG, "fallback failed", e);
            Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show();
        } finally {
            finishQuietly();
        }
    }

    private void finishQuietly() {
        launchInProgress = false;
        finish();
        overridePendingTransition(0, 0);
    }
}
