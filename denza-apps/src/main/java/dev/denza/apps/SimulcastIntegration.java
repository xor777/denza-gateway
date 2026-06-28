package dev.denza.apps;

import android.content.Context;
import android.content.SharedPreferences;

final class SimulcastIntegration {
    private static final String PREFS = "simulcast_integration";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_TARGET_PACKAGE = "last_target_package";
    private static final String KEY_LAST_RECEIVER = "last_receiver";

    private SimulcastIntegration() {
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static String getLastTargetPackage(Context context) {
        return prefs(context).getString(KEY_LAST_TARGET_PACKAGE, null);
    }

    static String getLastReceiver(Context context) {
        return prefs(context).getString(KEY_LAST_RECEIVER, null);
    }

    static void setLastTargetPackage(Context context, String packageName) {
        prefs(context).edit().putString(KEY_LAST_TARGET_PACKAGE, packageName).apply();
    }

    static void setLastTarget(Context context, String packageName, String receiver) {
        prefs(context).edit()
                .putString(KEY_LAST_TARGET_PACKAGE, packageName)
                .putString(KEY_LAST_RECEIVER, receiver)
                .apply();
    }

    static void clearLastTargetPackage(Context context) {
        prefs(context).edit()
                .remove(KEY_LAST_TARGET_PACKAGE)
                .remove(KEY_LAST_RECEIVER)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
