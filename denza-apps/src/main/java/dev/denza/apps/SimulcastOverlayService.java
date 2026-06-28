package dev.denza.apps;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.denza.disharebridge.DiShareProjectionBridge;
import dev.denza.disharebridge.DiShareScreens;

public class SimulcastOverlayService extends Service {
    private static final String TAG = "DenzaSimulcastOverlay";
    static final String ACTION_SHOW = "dev.denza.apps.SHOW_SIMULCAST_OVERLAY";
    static final String ACTION_ARM_APP_CHANGE = "dev.denza.apps.ARM_SIMULCAST_APP_CHANGE";
    static final String ACTION_HIDE = "dev.denza.apps.HIDE_SIMULCAST_OVERLAY";
    private static final String ACTION_MONITOR = "dev.denza.apps.MONITOR_SIMULCAST";
    private static final String ACTION_SHOW_ACTIVE_EXIT = "dev.denza.apps.SHOW_ACTIVE_EXIT";
    private static final String ACTION_HIDE_ACTIVE_EXIT = "dev.denza.apps.HIDE_ACTIVE_EXIT";
    static final String ACTION_START_TARGET = "dev.denza.apps.START_SIMULCAST_TARGET";
    static final String ACTION_STOP_CURRENT = "dev.denza.apps.STOP_SIMULCAST_TARGET";
    static final String EXTRA_TARGET_PACKAGE = "targetPackage";
    static final String EXTRA_RECEIVER = "receiver";
    private static final String EXTRA_VIDEO_WIDTH = "videoWidth";
    private static final String EXTRA_VIDEO_HEIGHT = "videoHeight";
    private static final String ACTION_DISHARE_DIALOG_HOME = "action.byd.dishare.DIALOG_HOME";
    private static final String ACTION_DISHARE_DIALOG_LAUNCHER =
            "action.byd.dishare.DIALOG_LAUNCHER";
    private static final String ACTION_DISHARE_DIALOG_CLOSE = "action.byd.dishare.DIALOG_CLOSE";
    private static final long HOTSPOT_IDLE_TIMEOUT_MS = 90_000L;
    private static final LayoutProfile IVI_R_PROFILE = new LayoutProfile(
            "ivi_r",
            839f,
            new RectF(326, 166, 538, 392),
            new RectF(118, 208, 256, 294),
            new RectF(576, 208, 776, 296),
            new RectF(308, 402, 556, 558),
            new RectF(95, 432, 276, 538),
            new RectF(588, 432, 769, 538),
            new RectF(260, 610, 610, 736),
            618f,
            632f);
    private static final LayoutProfile INTEGRATE_PROFILE = new LayoutProfile(
            "integrate",
            1280f,
            new RectF(526, 174, 761, 354),
            new RectF(275, 224, 411, 326),
            new RectF(836, 214, 1047, 320),
            new RectF(542, 383, 739, 508),
            new RectF(234, 393, 445, 508),
            new RectF(836, 393, 1047, 508),
            new RectF(450, 610, 830, 736),
            618f,
            632f);

    private static final AppTarget[] APP_TARGETS = {
            new AppTarget("com.vk.vkvideo", "VK", Color.rgb(28, 117, 238), Color.WHITE),
            new AppTarget("ru.rutube.app", "R", Color.rgb(240, 31, 57), Color.WHITE),
            new AppTarget("ru.yandex.yandexnavi", "N", Color.rgb(28, 117, 238), Color.WHITE),
            new AppTarget("ru.yandex.music", "M", Color.rgb(255, 202, 40), Color.rgb(18, 23, 27))
    };

    private WindowManager windowManager;
    private SimulcastDragView overlayView;
    private SimulcastPreviewView previewView;
    private View endHotspotView;
    private SimulcastExitButtonView receiverExitView;
    private SimulcastExitButtonView activeShareExitView;
    private WindowManager.LayoutParams overlayParams;
    private BroadcastReceiver dialogReceiver;
    private DiShareProjectionBridge activeBridge;
    private LayoutProfile lastProfile;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideIdleHotspotRunnable = new Runnable() {
        @Override
        public void run() {
            if (overlayView != null && !overlayView.isRowVisible()) {
                Log.i(TAG, "idle app change hotspot hidden");
                hideOverlay();
            }
        }
    };

    public static void startMonitor(Context context) {
        startAction(context, ACTION_MONITOR);
    }

    public static void show(Context context) {
        startAction(context, ACTION_SHOW);
    }

    public static void armAppChange(Context context) {
        startAction(context, ACTION_ARM_APP_CHANGE);
    }

    public static void hide(Context context) {
        startAction(context, ACTION_HIDE);
    }

    public static void stopCurrent(Context context) {
        startAction(context, ACTION_STOP_CURRENT);
    }

    /** Launch a target share through the proven bridge path at the given source size. */
    public static void startTarget(Context context, String targetPackage, String receiver,
            int videoWidth, int videoHeight) {
        try {
            context.startService(new Intent(context, SimulcastOverlayService.class)
                    .setAction(ACTION_START_TARGET)
                    .putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                    .putExtra(EXTRA_RECEIVER, receiver)
                    .putExtra(EXTRA_VIDEO_WIDTH, videoWidth)
                    .putExtra(EXTRA_VIDEO_HEIGHT, videoHeight));
        } catch (RuntimeException e) {
            Log.i(TAG, "startTarget failed", e);
        }
    }

    public static void showActiveExit(Context context) {
        startAction(context, ACTION_SHOW_ACTIVE_EXIT);
    }

    public static void hideActiveExit(Context context) {
        startAction(context, ACTION_HIDE_ACTIVE_EXIT);
    }

    private static void startAction(Context context, String action) {
        try {
            context.startService(new Intent(context, SimulcastOverlayService.class)
                    .setAction(action));
        } catch (RuntimeException e) {
            Log.i(TAG, "start " + action + " failed", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        ensureDialogReceiver();
        if (ACTION_SHOW_ACTIVE_EXIT.equals(action)) {
            if (SimulcastIntegration.isEnabled(this)
                    && SimulcastIntegration.getLastTargetPackage(this) != null) {
                showActiveShareExit();
            }
            return START_STICKY;
        }
        if (ACTION_HIDE_ACTIVE_EXIT.equals(action)) {
            hideActiveShareExit();
            return START_STICKY;
        }
        if (ACTION_MONITOR.equals(action)) {
            if (SimulcastIntegration.getLastTargetPackage(this) != null) {
                showActiveShareExit();
            }
            return START_STICKY;
        }
        if (ACTION_ARM_APP_CHANGE.equals(action)) {
            // The dialog overlay is now drawn by SimulcastAccessibilityService.
            // Here we only keep the active-share exit button in sync.
            if (SimulcastIntegration.isEnabled(this)
                    && SimulcastIntegration.getLastTargetPackage(this) != null) {
                showActiveShareExit();
            }
            return START_STICKY;
        }
        if (ACTION_HIDE.equals(action)) {
            hideOverlay();
            hideActiveControls();
            hideActiveShareExit();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_CURRENT.equals(action)) {
            stopCurrentShare();
            hideOverlay();
            hideActiveControls();
            sendBroadcast(new Intent(ACTION_DISHARE_DIALOG_CLOSE));
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_DISHARE_DIALOG_LAUNCHER.equals(action)) {
            // Dialog overlay handled by SimulcastAccessibilityService; just clear
            // the active-share exit while the picker is up.
            if (SimulcastIntegration.isEnabled(this)) {
                hideActiveShareExit();
            }
            return START_STICKY;
        }
        if (ACTION_DISHARE_DIALOG_HOME.equals(action)
                || ACTION_DISHARE_DIALOG_CLOSE.equals(action)) {
            hideOverlay();
            hideActiveControls();
            if (SimulcastIntegration.isEnabled(this)
                    && SimulcastIntegration.getLastTargetPackage(this) != null) {
                showActiveShareExit();
            }
            return START_STICKY;
        }
        if (ACTION_START_TARGET.equals(action)) {
            String packageName = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
            String receiver = intent.getStringExtra(EXTRA_RECEIVER);
            int videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 0);
            int videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 0);
            if (packageName == null || packageName.trim().isEmpty()) {
                Log.w(TAG, "start target with empty package");
                return START_NOT_STICKY;
            }
            // Any selected package can be cast, not just a hard-coded list.
            startTargetByPackage(packageName.trim(), resolveLabel(packageName.trim()),
                    receiver == null || receiver.trim().isEmpty()
                            ? "screen_hud" : receiver.trim(), videoWidth, videoHeight);
            return START_STICKY;
        }
        // Default (ACTION_SHOW): the dialog overlay is owned by
        // SimulcastAccessibilityService now, so there is nothing to draw here.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshVisibleGeometry();
    }

    @Override
    public void onDestroy() {
        if (dialogReceiver != null) {
            try {
                unregisterReceiver(dialogReceiver);
            } catch (RuntimeException ignored) {
            }
            dialogReceiver = null;
        }
        hideOverlay();
        hideActiveControls();
        hideActiveShareExit();
        handler.removeCallbacksAndMessages(null);
        stopBridge();
        super.onDestroy();
    }

    private void ensureDialogReceiver() {
        if (dialogReceiver != null) {
            return;
        }
        dialogReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                Log.i(TAG, "dialog action=" + action);
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                    dismissDialogOverlay();
                    return;
                }
                if (!SimulcastIntegration.isEnabled(context)) {
                    hideOverlay();
                    hideActiveControls();
                    hideActiveShareExit();
                    return;
                }
                if (ACTION_DISHARE_DIALOG_LAUNCHER.equals(action)) {
                    // SimulcastAccessibilityService draws the picker overlay now.
                    hideActiveShareExit();
                } else if (ACTION_DISHARE_DIALOG_HOME.equals(action)
                        || ACTION_DISHARE_DIALOG_CLOSE.equals(action)) {
                    hideOverlay();
                    hideActiveControls();
                    if (SimulcastIntegration.getLastTargetPackage(context) != null) {
                        showActiveShareExit();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_DISHARE_DIALOG_HOME);
        filter.addAction(ACTION_DISHARE_DIALOG_LAUNCHER);
        filter.addAction(ACTION_DISHARE_DIALOG_CLOSE);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dialogReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(dialogReceiver, filter);
        }
    }

    private void showOverlay() {
        handler.removeCallbacks(hideIdleHotspotRunnable);
        hideActiveControls();
        hideActiveShareExit();
        showOverlayWithParams(true, fullOverlayParams());
    }

    private void showAppChangeHotspot() {
        showOverlayWithParams(false, appChangeHotspotParams());
    }

    private void showOverlayWithParams(boolean rowVisible, WindowManager.LayoutParams params) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "overlay permission is not granted");
        }
        ensureWindowManager();
        ensureAppIcons();
        if (overlayView != null) {
            overlayView.setRowVisible(rowVisible);
            overlayParams = params;
            try {
                windowManager.updateViewLayout(overlayView, params);
                overlayView.invalidate();
                updateHotspotTimeout(rowVisible);
            } catch (RuntimeException e) {
                Log.i(TAG, "overlay update failed", e);
            }
            return;
        }
        overlayView = new SimulcastDragView(this);
        overlayView.setRowVisible(rowVisible);
        overlayParams = params;
        try {
            windowManager.addView(overlayView, params);
            Log.i(TAG, rowVisible ? "drag overlay shown" : "app change hotspot shown");
            updateHotspotTimeout(rowVisible);
            queryAvailableScreens();
        } catch (RuntimeException e) {
            Log.e(TAG, "overlay failed", e);
            overlayView = null;
            overlayParams = null;
            Toast.makeText(this, "Не удалось показать ряд приложений Simulcast", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private WindowManager.LayoutParams fullOverlayParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private WindowManager.LayoutParams appChangeHotspotParams() {
        RectF hotspot = currentProfile().appChangeHotspotBoundsDp;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(hotspot.width()),
                dp(hotspot.height()),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(hotspot.left);
        params.y = dp(hotspot.top);
        return params;
    }

    private WindowManager.LayoutParams activePreviewParams() {
        RectF screen = centralPreviewBoundsDp();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(screen.width()),
                dp(screen.height()),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(screen.left);
        params.y = dp(screen.top);
        return params;
    }

    private WindowManager.LayoutParams endHotspotParams() {
        LayoutProfile profile = currentProfile();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(416),
                dp(90),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp((profile.layoutWidthDp - 416f) / 2f);
        params.y = dp(profile.activeActionsTopDp);
        return params;
    }

    private WindowManager.LayoutParams receiverExitParams(String receiver) {
        RectF screen = screenBoundsDp(receiver);
        if (screen == null) {
            return null;
        }
        int size = dp(48);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(screen.left + 10);
        params.y = dp(screen.centerY() - 24);
        return params;
    }

    private WindowManager.LayoutParams activeShareExitParams() {
        int size = dp(56);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(24);
        params.y = dp(78);
        return params;
    }

    private void queryAvailableScreens() {
        DiShareScreens.query(getApplicationContext(), "com.byd.dishare",
                new DiShareScreens.Callback() {
                    @Override
                    public void onScreens(List<DiShareScreens.Screen> screens) {
                        Log.i(TAG, "available screens=" + screens);
                        if (overlayView != null) {
                            overlayView.setAvailableScreens(screens);
                        }
                    }

                    @Override
                    public void onFailed(String message) {
                        Log.w(TAG, "screen query failed " + message);
                    }
                });
    }

    private void hideOverlay() {
        handler.removeCallbacks(hideIdleHotspotRunnable);
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
            }
        }
        overlayView = null;
        overlayParams = null;
    }

    private void dismissDialogOverlay() {
        hideOverlay();
        hideActiveControls();
        if (SimulcastIntegration.isEnabled(this)
                && SimulcastIntegration.getLastTargetPackage(this) != null) {
            showActiveShareExit();
        } else {
            hideActiveShareExit();
        }
    }

    private void closeSimulcastDialog() {
        dismissDialogOverlay();
        DiShareProjectionBridge.closeUi(getApplicationContext(), "screen_ivi",
                new DiShareProjectionBridge.Callback() {
                    @Override
                    public void onLog(String message) {
                        Log.i(TAG, "close ui " + message);
                    }

                    @Override
                    public void onStarted(Bundle result) {
                    }

                    @Override
                    public void onFailed(String message) {
                        Log.w(TAG, "close ui failed " + message);
                        launchHomeFallback();
                    }

                    @Override
                    public void onStopped(String message) {
                        Log.i(TAG, "close ui stopped " + message);
                    }
                });
    }

    private void launchHomeFallback() {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Log.i(TAG, "home fallback failed", e);
        }
    }

    private void updateHotspotTimeout(boolean rowVisible) {
        handler.removeCallbacks(hideIdleHotspotRunnable);
        if (!rowVisible) {
            handler.postDelayed(hideIdleHotspotRunnable, HOTSPOT_IDLE_TIMEOUT_MS);
        }
    }

    private void showActiveControls() {
        AppTarget target = findAppTarget(SimulcastIntegration.getLastTargetPackage(this));
        if (target == null) {
            hideActiveControls();
            return;
        }
        ensureWindowManager();
        ensureAppIcons();
        if (previewView != null) {
            previewView.setTarget(target);
            try {
                windowManager.updateViewLayout(previewView, activePreviewParams());
                previewView.invalidate();
            } catch (RuntimeException e) {
                Log.i(TAG, "active preview update failed", e);
            }
        } else {
            previewView = new SimulcastPreviewView(this);
            previewView.setTarget(target);
            try {
                windowManager.addView(previewView, activePreviewParams());
                Log.i(TAG, "active preview shown package=" + target.packageName);
            } catch (RuntimeException e) {
                Log.i(TAG, "active preview failed", e);
                previewView = null;
            }
        }
        showEndHotspot();
        showReceiverExit();
    }

    private void hideActiveControls() {
        if (previewView != null && windowManager != null) {
            try {
                windowManager.removeView(previewView);
            } catch (RuntimeException ignored) {
            }
        }
        previewView = null;
        if (endHotspotView != null && windowManager != null) {
            try {
                windowManager.removeView(endHotspotView);
            } catch (RuntimeException ignored) {
            }
        }
        endHotspotView = null;
        if (receiverExitView != null && windowManager != null) {
            try {
                windowManager.removeView(receiverExitView);
            } catch (RuntimeException ignored) {
            }
        }
        receiverExitView = null;
    }

    private void showActiveShareExit() {
        if (SimulcastIntegration.getLastTargetPackage(this) == null) {
            hideActiveShareExit();
            return;
        }
        ensureWindowManager();
        if (activeShareExitView != null) {
            try {
                windowManager.updateViewLayout(activeShareExitView, activeShareExitParams());
                activeShareExitView.invalidate();
            } catch (RuntimeException e) {
                Log.i(TAG, "active share exit update failed", e);
            }
            return;
        }
        activeShareExitView = new SimulcastExitButtonView(this, true);
        activeShareExitView.setOnClickListener(view -> {
            stopCurrentShare();
            hideOverlay();
            hideActiveControls();
            sendBroadcast(new Intent(ACTION_DISHARE_DIALOG_CLOSE));
        });
        try {
            windowManager.addView(activeShareExitView, activeShareExitParams());
            Log.i(TAG, "active share exit shown");
        } catch (RuntimeException e) {
            Log.i(TAG, "active share exit failed", e);
            activeShareExitView = null;
        }
    }

    private void hideActiveShareExit() {
        if (activeShareExitView != null && windowManager != null) {
            try {
                windowManager.removeView(activeShareExitView);
            } catch (RuntimeException ignored) {
            }
        }
        activeShareExitView = null;
    }

    private void showEndHotspot() {
        if (endHotspotView != null) {
            try {
                windowManager.updateViewLayout(endHotspotView, endHotspotParams());
                endHotspotView.invalidate();
            } catch (RuntimeException e) {
                Log.i(TAG, "end hotspot update failed", e);
            }
            return;
        }
        endHotspotView = new SimulcastActiveActionsView(this);
        try {
            windowManager.addView(endHotspotView, endHotspotParams());
        } catch (RuntimeException e) {
            Log.i(TAG, "end hotspot failed", e);
            endHotspotView = null;
        }
    }

    private void showReceiverExit() {
        String receiver = SimulcastIntegration.getLastReceiver(this);
        WindowManager.LayoutParams params = receiverExitParams(receiver);
        if (params == null) {
            if (receiverExitView != null && windowManager != null) {
                try {
                    windowManager.removeView(receiverExitView);
                } catch (RuntimeException ignored) {
                }
            }
            receiverExitView = null;
            return;
        }
        if (receiverExitView != null) {
            try {
                windowManager.updateViewLayout(receiverExitView, params);
            } catch (RuntimeException e) {
                Log.i(TAG, "receiver exit update failed", e);
            }
            return;
        }
        receiverExitView = new SimulcastExitButtonView(this);
        receiverExitView.setOnClickListener(view -> {
            stopCurrentShare();
            hideOverlay();
            hideActiveControls();
            sendBroadcast(new Intent(ACTION_DISHARE_DIALOG_CLOSE));
        });
        try {
            windowManager.addView(receiverExitView, params);
        } catch (RuntimeException e) {
            Log.i(TAG, "receiver exit failed", e);
            receiverExitView = null;
        }
    }

    private void refreshVisibleGeometry() {
        if (windowManager == null) {
            return;
        }
        LayoutProfile profile = currentProfile();
        if (profile != lastProfile) {
            Log.i(TAG, "layout profile=" + profile.name);
            lastProfile = profile;
        }
        if (overlayView != null) {
            WindowManager.LayoutParams params = overlayView.isRowVisible()
                    ? fullOverlayParams() : appChangeHotspotParams();
            overlayParams = params;
            try {
                windowManager.updateViewLayout(overlayView, params);
                overlayView.invalidate();
            } catch (RuntimeException e) {
                Log.i(TAG, "overlay relayout failed", e);
            }
        }
        if (previewView != null) {
            try {
                windowManager.updateViewLayout(previewView, activePreviewParams());
                previewView.invalidate();
            } catch (RuntimeException e) {
                Log.i(TAG, "active preview relayout failed", e);
            }
        }
        if (endHotspotView != null) {
            try {
                windowManager.updateViewLayout(endHotspotView, endHotspotParams());
                endHotspotView.invalidate();
            } catch (RuntimeException e) {
                Log.i(TAG, "end hotspot relayout failed", e);
            }
        }
        if (receiverExitView != null) {
            showReceiverExit();
        }
        if (activeShareExitView != null) {
            showActiveShareExit();
        }
    }

    private void startTarget(final AppTarget target, final String receiver) {
        startTarget(target, receiver, 0, 0);
    }

    private void startTarget(final AppTarget target, final String receiver,
            final int videoWidth, final int videoHeight) {
        stopBridge();
        SimulcastIntegration.clearLastTargetPackage(this);
        hideActiveControls();
        hideActiveShareExit();
        Log.i(TAG, "start target=" + target.packageName + " receiver=" + receiver
                + " video=" + videoWidth + "x" + videoHeight);
        activeBridge = new DiShareProjectionBridge(
                getApplicationContext(),
                target.packageName,
                new DiShareProjectionBridge.Callback() {
                    @Override
                    public void onLog(String message) {
                        Log.i(TAG, target.packageName + " " + message);
                    }

                    @Override
                    public void onStarted(Bundle result) {
                        Log.i(TAG, target.packageName + " started "
                                + DiShareProjectionBridge.bundleToString(result));
                        SimulcastIntegration.setLastTarget(SimulcastOverlayService.this,
                                target.packageName, receiver);
                        Toast.makeText(SimulcastOverlayService.this,
                                "Запускаю " + target.fallbackLabel, Toast.LENGTH_SHORT).show();
                        sendBroadcast(new Intent(ACTION_DISHARE_DIALOG_CLOSE));
                        hideOverlay();
                        showActiveShareExit();
                    }

                    @Override
                    public void onFailed(String message) {
                        Log.w(TAG, target.packageName + " failed " + message);
                        Toast.makeText(SimulcastOverlayService.this,
                                "Simulcast не запустил " + target.fallbackLabel + ": " + message,
                                Toast.LENGTH_LONG).show();
                        activeBridge = null;
                    }

                    @Override
                    public void onStopped(String message) {
                        Log.i(TAG, target.packageName + " stopped " + message);
                    }
                });
        if (videoWidth > 0 && videoHeight > 0) {
            activeBridge.startToReceiver(receiver, videoWidth, videoHeight);
        } else {
            activeBridge.startToReceiver(receiver);
        }
    }

    private void stopCurrentShare() {
        if (activeBridge != null) {
            stopBridge();
            SimulcastIntegration.clearLastTargetPackage(this);
            hideActiveShareExit();
            Toast.makeText(this, "Simulcast завершен", Toast.LENGTH_SHORT).show();
            return;
        }
        DiShareProjectionBridge.stopCurrentShare(getApplicationContext(),
                new DiShareProjectionBridge.Callback() {
                    @Override
                    public void onLog(String message) {
                        Log.i(TAG, "stop current " + message);
                    }

                    @Override
                    public void onStarted(Bundle result) {
                    }

                    @Override
                    public void onFailed(String message) {
                        Log.w(TAG, "stop current failed " + message);
                        Toast.makeText(SimulcastOverlayService.this,
                                "Simulcast не завершился: " + message,
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onStopped(String message) {
                        SimulcastIntegration.clearLastTargetPackage(SimulcastOverlayService.this);
                        hideActiveShareExit();
                        Toast.makeText(SimulcastOverlayService.this,
                                "Simulcast завершен", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void stopBridge() {
        if (activeBridge != null) {
            activeBridge.stop();
            activeBridge = null;
        }
    }

    private AppTarget findAppTarget(String packageName) {
        if (packageName == null) {
            return null;
        }
        for (AppTarget target : APP_TARGETS) {
            if (packageName.equals(target.packageName)) {
                return target;
            }
        }
        return null;
    }

    private Drawable loadLauncherIcon(String packageName) {
        try {
            return getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "target app icon not found: " + packageName);
            return null;
        }
    }

    private RectF screenBoundsDp(String receiver) {
        return currentProfile().screenBounds(receiver);
    }

    private RectF centralPreviewBoundsDp() {
        return new RectF(currentProfile().centralPreviewBoundsDp);
    }

    private LayoutProfile currentProfile() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        LayoutProfile profile = widthDp >= 1000 ? INTEGRATE_PROFILE : IVI_R_PROFILE;
        if (profile != lastProfile) {
            Log.i(TAG, "layout profile=" + profile.name + " widthDp=" + widthDp);
            lastProfile = profile;
        }
        return profile;
    }

    private String findReceiverAt(float rawX, float rawY) {
        String[] receivers = {
                "screen_hud", "screen_fse", "screen_overhead", "screen_rse_l", "screen_rse_r"
        };
        String nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (String receiver : receivers) {
            RectF bounds = screenBoundsDp(receiver);
            RectF pixelBounds = new RectF(dp(bounds.left), dp(bounds.top),
                    dp(bounds.right), dp(bounds.bottom));
            RectF hitBounds = expanded(pixelBounds, dp(42));
            if (hitBounds.contains(rawX, rawY)) {
                return receiver;
            }
            float dx = pixelBounds.centerX() - rawX;
            float dy = pixelBounds.centerY() - rawY;
            float distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = receiver;
            }
        }
        float maxDistance = dp(180);
        return nearestDistance <= maxDistance * maxDistance ? nearest : null;
    }

    private RectF expanded(RectF source, float amount) {
        return new RectF(source.left - amount, source.top - amount,
                source.right + amount, source.bottom + amount);
    }

    private void ensureWindowManager() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
    }

    private void ensureAppIcons() {
        for (AppTarget target : APP_TARGETS) {
            if (target.icon == null) {
                target.icon = loadLauncherIcon(target.packageName);
            }
        }
    }

    private void drawAppIcon(Canvas canvas, Paint paint, Paint textPaint, Rect iconRect,
            AppTarget target, RectF bounds, float alpha) {
        int save = canvas.saveLayerAlpha(bounds, Math.round(alpha * 255f));
        if (target.icon != null) {
            iconRect.set(Math.round(bounds.left), Math.round(bounds.top),
                    Math.round(bounds.right), Math.round(bounds.bottom));
            target.icon.setBounds(iconRect);
            target.icon.draw(canvas);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(target.backgroundColor);
            canvas.drawRoundRect(bounds, dp(10), dp(10), paint);
            textPaint.setColor(target.textColor);
            textPaint.setTextSize(target.fallbackLabel.length() > 1 ? dp(20) : dp(25));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(target.fallbackLabel, bounds.centerX(), baseline, textPaint);
        }
        canvas.restoreToCount(save);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class AppTarget {
        final String packageName;
        final String fallbackLabel;
        final int backgroundColor;
        final int textColor;
        Drawable icon;
        final RectF bounds = new RectF();

        AppTarget(String packageName, String fallbackLabel, int backgroundColor, int textColor) {
            this.packageName = packageName;
            this.fallbackLabel = fallbackLabel;
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
        }
    }

    private static final class ScreenTarget {
        final String name;
        final String receiver;
        final RectF bounds = new RectF();

        ScreenTarget(String name, String receiver) {
            this.name = name;
            this.receiver = receiver;
        }
    }

    private static final class LayoutProfile {
        final String name;
        final float layoutWidthDp;
        final RectF centralPreviewBoundsDp;
        final RectF hudBoundsDp;
        final RectF fseBoundsDp;
        final RectF overheadBoundsDp;
        final RectF rearLeftBoundsDp;
        final RectF rearRightBoundsDp;
        final RectF appChangeHotspotBoundsDp;
        final float rowTopDp;
        final float activeActionsTopDp;

        LayoutProfile(String name, float layoutWidthDp, RectF centralPreviewBoundsDp,
                RectF hudBoundsDp, RectF fseBoundsDp, RectF overheadBoundsDp,
                RectF rearLeftBoundsDp, RectF rearRightBoundsDp,
                RectF appChangeHotspotBoundsDp, float rowTopDp, float activeActionsTopDp) {
            this.name = name;
            this.layoutWidthDp = layoutWidthDp;
            this.centralPreviewBoundsDp = centralPreviewBoundsDp;
            this.hudBoundsDp = hudBoundsDp;
            this.fseBoundsDp = fseBoundsDp;
            this.overheadBoundsDp = overheadBoundsDp;
            this.rearLeftBoundsDp = rearLeftBoundsDp;
            this.rearRightBoundsDp = rearRightBoundsDp;
            this.appChangeHotspotBoundsDp = appChangeHotspotBoundsDp;
            this.rowTopDp = rowTopDp;
            this.activeActionsTopDp = activeActionsTopDp;
        }

        RectF screenBounds(String receiver) {
            if ("screen_hud".equals(receiver)) {
                return new RectF(hudBoundsDp);
            }
            if ("screen_ivi".equals(receiver)) {
                return new RectF(centralPreviewBoundsDp);
            }
            if ("screen_fse".equals(receiver)) {
                return new RectF(fseBoundsDp);
            }
            if ("screen_overhead".equals(receiver)) {
                return new RectF(overheadBoundsDp);
            }
            if ("screen_rse_l".equals(receiver)) {
                return new RectF(rearLeftBoundsDp);
            }
            if ("screen_rse_r".equals(receiver)) {
                return new RectF(rearRightBoundsDp);
            }
            return null;
        }
    }

    private final class SimulcastPreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect iconRect = new Rect();
        private final RectF cardBounds = new RectF();
        private final RectF iconBounds = new RectF();
        private AppTarget target;

        SimulcastPreviewView(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(Color.TRANSPARENT);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setTarget(AppTarget target) {
            this.target = target;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (target == null) {
                return;
            }
            layout();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(18, 23, 27));
            canvas.drawRoundRect(cardBounds, dp(16), dp(16), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(230, 48, 144, 255));
            canvas.drawRoundRect(cardBounds, dp(16), dp(16), paint);

            drawAppIcon(canvas, paint, textPaint, iconRect, target, iconBounds, 1f);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (target == null) {
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                String receiver = findReceiverAt(event.getRawX(), event.getRawY());
                if (receiver != null) {
                    startTarget(target, receiver);
                }
                return true;
            }
            return true;
        }

        private void layout() {
            cardBounds.set(0, 0, getWidth(), getHeight());
            float iconSize = Math.min(cardBounds.width(), cardBounds.height()) * 0.38f;
            iconBounds.set(
                    cardBounds.centerX() - iconSize / 2f,
                    cardBounds.centerY() - iconSize / 2f,
                    cardBounds.centerX() + iconSize / 2f,
                    cardBounds.centerY() + iconSize / 2f);
        }
    }

    private final class SimulcastActiveActionsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF appChangeBounds = new RectF();
        private final RectF endBounds = new RectF();

        SimulcastActiveActionsView(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(Color.TRANSPARENT);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            layoutButtons();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(49, 49, 58));
            canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()),
                    dp(8), dp(8), paint);
            drawActionButton(canvas, appChangeBounds, "App Change");
            drawActionButton(canvas, endBounds, "End");
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_UP) {
                return true;
            }
            layoutButtons();
            float x = event.getX();
            float y = event.getY();
            if (appChangeBounds.contains(x, y)) {
                hideActiveControls();
                showOverlay();
                return true;
            }
            if (endBounds.contains(x, y)) {
                stopCurrentShare();
                hideOverlay();
                hideActiveControls();
                sendBroadcast(new Intent(ACTION_DISHARE_DIALOG_CLOSE));
                return true;
            }
            return true;
        }

        private void layoutButtons() {
            float buttonWidth = dp(176);
            float buttonHeight = dp(56);
            float gap = dp(32);
            float left = (getWidth() - buttonWidth * 2f - gap) / 2f;
            float top = (getHeight() - buttonHeight) / 2f;
            appChangeBounds.set(left, top, left + buttonWidth, top + buttonHeight);
            endBounds.set(appChangeBounds.right + gap, top,
                    appChangeBounds.right + gap + buttonWidth, top + buttonHeight);
        }

        private void drawActionButton(Canvas canvas, RectF bounds, String label) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(31, 37, 45));
            canvas.drawRoundRect(bounds, dp(8), dp(8), paint);

            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dp(18));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(label, bounds.centerX(), baseline, textPaint);
        }
    }

    private final class SimulcastExitButtonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean prominent;

        SimulcastExitButtonView(Context context) {
            this(context, false);
        }

        SimulcastExitButtonView(Context context, boolean prominent) {
            super(context);
            this.prominent = prominent;
            setWillNotDraw(false);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(4);
            RectF background = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(prominent ? Color.rgb(220, 38, 48) : Color.argb(168, 0, 0, 0));
            canvas.drawRoundRect(background, dp(10), dp(10), paint);

            paint.setColor(prominent ? Color.WHITE : Color.rgb(255, 64, 70));
            paint.setStrokeWidth(dp(2.2f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            float left = dp(14);
            float top = dp(12);
            float right = getWidth() - dp(11);
            float bottom = getHeight() - dp(12);
            canvas.drawLine(dp(24), top, right, top, paint);
            canvas.drawLine(right, top, right, bottom, paint);
            canvas.drawLine(dp(24), bottom, right, bottom, paint);

            float centerY = getHeight() / 2f;
            canvas.drawLine(left, centerY, dp(31), centerY, paint);
            canvas.drawLine(left, centerY, dp(22), centerY - dp(8), paint);
            canvas.drawLine(left, centerY, dp(22), centerY + dp(8), paint);
        }
    }

    private final class SimulcastDragView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect iconRect = new Rect();
        private final List<ScreenTarget> screens = new ArrayList<>();
        private final List<String> availableReceivers = new ArrayList<>();
        private final RectF rowBounds = new RectF();
        private final RectF previewBounds = new RectF();
        private AppTarget selectedTarget;
        private AppTarget draggingTarget;
        private ScreenTarget hoverTarget;
        private boolean rowVisible = true;
        private boolean nativeCloseDown;
        private float dragX;
        private float dragY;

        SimulcastDragView(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(Color.TRANSPARENT);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            ensureAppIcons();
            selectedTarget = APP_TARGETS[0];
            screens.add(new ScreenTarget("HUD", "screen_hud"));
            screens.add(new ScreenTarget("Passenger", "screen_fse"));
            screens.add(new ScreenTarget("Overhead", "screen_overhead"));
            screens.add(new ScreenTarget("Rear left", "screen_rse_l"));
            screens.add(new ScreenTarget("Rear right", "screen_rse_r"));
            screens.add(new ScreenTarget("Main", "screen_ivi"));
        }

        void setRowVisible(boolean visible) {
            rowVisible = visible;
            if (!visible) {
                nativeCloseDown = false;
                draggingTarget = null;
                hoverTarget = null;
            }
            invalidate();
        }

        boolean isRowVisible() {
            return rowVisible;
        }

        void setAvailableScreens(List<DiShareScreens.Screen> availableScreens) {
            availableReceivers.clear();
            for (DiShareScreens.Screen screen : availableScreens) {
                if (screen != null && screen.available && screen.screenId != null
                        && !availableReceivers.contains(screen.screenId)) {
                    availableReceivers.add(screen.screenId);
                }
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!rowVisible) {
                return;
            }
            layoutTargets();
            drawSelectedPreview(canvas);
            if (draggingTarget != null) {
                drawScreenHints(canvas);
            }
            drawAppRow(canvas);
            if (draggingTarget != null) {
                drawDragIcon(canvas);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (!rowVisible) {
                if (action == MotionEvent.ACTION_UP) {
                    showOverlay();
                }
                return true;
            }
            float x = event.getX();
            float y = event.getY();
            layoutTargets();
            if (action == MotionEvent.ACTION_DOWN) {
                if (isNativeCloseTap(x, y)) {
                    nativeCloseDown = true;
                    return true;
                }
                draggingTarget = findAppTarget(x, y);
                if (draggingTarget == null) {
                    return true;
                }
                selectedTarget = draggingTarget;
                dragX = x;
                dragY = y;
                hoverTarget = findScreenTarget(x, y);
                invalidate();
                return true;
            }
            if (nativeCloseDown) {
                if (action == MotionEvent.ACTION_UP) {
                    nativeCloseDown = false;
                    closeSimulcastDialog();
                    return true;
                }
                if (action == MotionEvent.ACTION_CANCEL) {
                    nativeCloseDown = false;
                    return true;
                }
            }
            if (draggingTarget == null) {
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                dragX = x;
                dragY = y;
                hoverTarget = findScreenTarget(x, y);
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                dragX = x;
                dragY = y;
                ScreenTarget target = findScreenTarget(x, y);
                AppTarget app = draggingTarget;
                draggingTarget = null;
                hoverTarget = null;
                invalidate();
                if (target != null && !"screen_ivi".equals(target.receiver)) {
                    startTarget(app, target.receiver);
                } else {
                    Toast.makeText(SimulcastOverlayService.this,
                            "Перетащите на другой экран", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                nativeCloseDown = false;
                draggingTarget = null;
                hoverTarget = null;
                invalidate();
                return true;
            }
            return true;
        }

        private void layoutTargets() {
            float scale = getResources().getDisplayMetrics().density;
            LayoutProfile profile = currentProfile();
            float offsetX = 0f;
            float offsetY = 0f;
            float contentWidth = Math.min(getWidth(), profile.layoutWidthDp * scale);
            float contentCenterX = contentWidth / 2f;
            for (ScreenTarget screen : screens) {
                RectF designBounds = screenBoundsDp(screen.receiver);
                if (designBounds == null) {
                    screen.bounds.setEmpty();
                    continue;
                }
                screen.bounds.set(
                        offsetX + designBounds.left * scale,
                        offsetY + designBounds.top * scale,
                        offsetX + designBounds.right * scale,
                        offsetY + designBounds.bottom * scale);
            }

            float iconSize = dp(62);
            float gap = dp(16);
            float paddingX = dp(20);
            float paddingTop = dp(10);
            float paddingBottom = dp(20);
            float rowWidth = paddingX * 2 + APP_TARGETS.length * iconSize
                    + (APP_TARGETS.length - 1) * gap;
            float rowHeight = iconSize + paddingTop + paddingBottom;
            float rowLeft = contentCenterX - rowWidth / 2f;
            float rowTop = Math.min(getHeight() - rowHeight - dp(16), dp(profile.rowTopDp));
            rowBounds.set(rowLeft, rowTop, rowLeft + rowWidth, rowTop + rowHeight);

            RectF centralScreen = centralPreviewBoundsDp();
            previewBounds.set(
                    offsetX + centralScreen.left * scale,
                    offsetY + centralScreen.top * scale,
                    offsetX + centralScreen.right * scale,
                    offsetY + centralScreen.bottom * scale);

            float left = rowBounds.left + paddingX;
            for (AppTarget target : APP_TARGETS) {
                target.bounds.set(left, rowBounds.top + paddingTop, left + iconSize,
                        rowBounds.top + paddingTop + iconSize);
                left += iconSize + gap;
            }
        }

        private void drawSelectedPreview(Canvas canvas) {
            if (selectedTarget == null) {
                return;
            }
            RectF background = expanded(previewBounds, dp(8));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(18, 23, 27));
            canvas.drawRoundRect(background, dp(16), dp(16), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(230, 48, 144, 255));
            canvas.drawRoundRect(background, dp(16), dp(16), paint);

            float iconSize = Math.min(previewBounds.width(), previewBounds.height()) * 0.38f;
            RectF iconBounds = new RectF(
                    previewBounds.centerX() - iconSize / 2f,
                    previewBounds.centerY() - iconSize / 2f,
                    previewBounds.centerX() + iconSize / 2f,
                    previewBounds.centerY() + iconSize / 2f);
            drawIcon(canvas, selectedTarget, iconBounds, draggingTarget == selectedTarget ? 0.55f : 1f);
        }

        private void drawScreenHints(Canvas canvas) {
            for (ScreenTarget screen : screens) {
                if ("screen_ivi".equals(screen.receiver)) {
                    continue;
                }
                if (!isReceiverAvailable(screen.receiver)) {
                    continue;
                }
                boolean selected = screen == hoverTarget;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(selected ? Color.argb(68, 31, 194, 142) : Color.argb(18, 255, 255, 255));
                canvas.drawRoundRect(expanded(screen.bounds, dp(18)), dp(12), dp(12), paint);
                if (selected) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(2));
                    paint.setColor(Color.argb(210, 31, 194, 142));
                    canvas.drawRoundRect(expanded(screen.bounds, dp(18)), dp(12), dp(12), paint);
                }
            }
        }

        private void drawAppRow(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(16, 18, 24));
            canvas.drawRoundRect(rowBounds, dp(16), dp(16), paint);
            for (AppTarget target : APP_TARGETS) {
                if (target == selectedTarget) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(2));
                    paint.setColor(Color.argb(230, 48, 144, 255));
                    canvas.drawRoundRect(expanded(target.bounds, dp(3)), dp(12), dp(12), paint);
                }
                drawIcon(canvas, target, target.bounds, target == draggingTarget ? 0.45f : 1f);
            }
        }

        private void drawDragIcon(Canvas canvas) {
            float size = dp(82);
            RectF bounds = new RectF(dragX - size / 2f, dragY - size / 2f,
                    dragX + size / 2f, dragY + size / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(72, 0, 0, 0));
            canvas.drawCircle(dragX + dp(4), dragY + dp(6), size * 0.54f, paint);
            drawIcon(canvas, draggingTarget, bounds, 0.96f);
        }

        private void drawIcon(Canvas canvas, AppTarget target, RectF bounds, float alpha) {
            drawAppIcon(canvas, paint, textPaint, iconRect, target, bounds, alpha);
        }

        private AppTarget findAppTarget(float x, float y) {
            if (selectedTarget != null && expanded(previewBounds, dp(18)).contains(x, y)) {
                return selectedTarget;
            }
            for (AppTarget target : APP_TARGETS) {
                if (expanded(target.bounds, dp(12)).contains(x, y)) {
                    return target;
                }
            }
            return null;
        }

        private ScreenTarget findScreenTarget(float x, float y) {
            ScreenTarget nearest = null;
            float nearestDistance = Float.MAX_VALUE;
            for (ScreenTarget screen : screens) {
                if (!isReceiverAvailable(screen.receiver)) {
                    continue;
                }
                RectF hitBounds = expanded(screen.bounds, dp(42));
                if (hitBounds.contains(x, y)) {
                    return screen;
                }
                float dx = screen.bounds.centerX() - x;
                float dy = screen.bounds.centerY() - y;
                float distance = dx * dx + dy * dy;
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = screen;
                }
            }
            float maxDistance = dp(180);
            return nearestDistance <= maxDistance * maxDistance ? nearest : null;
        }

        private boolean isReceiverAvailable(String receiver) {
            return availableReceivers.isEmpty() || availableReceivers.contains(receiver);
        }

        private boolean isNativeCloseTap(float x, float y) {
            return x >= 0 && x <= dp(170) && y >= dp(48) && y <= dp(160);
        }

        private RectF expanded(RectF source, float amount) {
            return new RectF(source.left - amount, source.top - amount,
                    source.right + amount, source.bottom + amount);
        }
    }
}
