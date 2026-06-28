package dev.denza.apps;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String DISHARE_PACKAGE = "com.byd.dishare";

    private Button toggleButton;
    private Button accessibilityButton;
    private TextView statusText;
    private TextView errorText;
    private boolean enabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enabled = SimulcastIntegration.isEnabled(this);
        setContentView(buildContent());
        if (enabled) {
            SourceKeeperService.start(this);
            SimulcastOverlayService.startMonitor(this);
        }
        refreshUi(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enabled = SimulcastIntegration.isEnabled(this);
        if (enabled) {
            SourceKeeperService.start(this);
        }
        refreshUi(null);
        SimulcastOverlayService.hide(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (SimulcastIntegration.isEnabled(this)) {
            SourceKeeperService.start(this);
            // The Simulcast picker overlay is drawn by SimulcastAccessibilityService;
            // here we only keep the active-share exit button alive.
            if (SimulcastIntegration.getLastTargetPackage(this) != null) {
                SimulcastOverlayService.showActiveExit(this);
            }
        }
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(44), dp(44), dp(44), dp(44));
        root.setBackgroundColor(Color.rgb(8, 10, 12));

        toggleButton = new Button(this);
        toggleButton.setAllCaps(false);
        toggleButton.setTextSize(34);
        toggleButton.setTextColor(Color.WHITE);
        toggleButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toggleButton.setOnClickListener(view -> toggle());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(132));
        buttonParams.leftMargin = dp(32);
        buttonParams.rightMargin = dp(32);
        root.addView(toggleButton, buttonParams);

        Button pickerButton = new Button(this);
        pickerButton.setAllCaps(false);
        pickerButton.setTextSize(22);
        pickerButton.setTextColor(Color.WHITE);
        pickerButton.setText("Выбрать приложения");
        pickerButton.setBackground(round(Color.rgb(34, 40, 50), dp(10)));
        pickerButton.setOnClickListener(view ->
                startActivity(new Intent(this, AppPickerActivity.class)));
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(96));
        pickerParams.leftMargin = dp(32);
        pickerParams.rightMargin = dp(32);
        pickerParams.topMargin = dp(20);
        root.addView(pickerButton, pickerParams);

        statusText = new TextView(this);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextSize(22);
        statusText.setTextColor(Color.rgb(214, 222, 232));
        statusText.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.leftMargin = dp(36);
        statusParams.rightMargin = dp(36);
        statusParams.topMargin = dp(22);
        root.addView(statusText, statusParams);

        errorText = new TextView(this);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTextSize(20);
        errorText.setTextColor(Color.rgb(255, 112, 122));
        errorText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        errorParams.topMargin = dp(12);
        root.addView(errorText, errorParams);

        accessibilityButton = new Button(this);
        accessibilityButton.setAllCaps(false);
        accessibilityButton.setTextSize(22);
        accessibilityButton.setTextColor(Color.WHITE);
        accessibilityButton.setText("Включить наложение Simulcast");
        accessibilityButton.setBackground(round(Color.rgb(46, 70, 92), dp(10)));
        accessibilityButton.setOnClickListener(view -> openAccessibilitySettings());
        LinearLayout.LayoutParams a11yParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(96));
        a11yParams.leftMargin = dp(32);
        a11yParams.rightMargin = dp(32);
        a11yParams.topMargin = dp(28);
        root.addView(accessibilityButton, a11yParams);

        return root;
    }

    private boolean isAccessibilityEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(flat)) {
            return false;
        }
        String name = SimulcastAccessibilityService.class.getName();
        return flat.contains(getPackageName() + "/" + name)
                || flat.contains(getPackageName() + "/." + SimulcastAccessibilityService.class.getSimpleName());
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            // Settings activity may be unavailable on some firmware; ignore.
        }
    }

    private void toggle() {
        if (enabled) {
            stopIntegration();
        } else {
            startIntegration();
        }
    }

    private void startIntegration() {
        String error = diagnoseStartError();
        if (error != null) {
            SimulcastIntegration.setEnabled(this, false);
            enabled = false;
            SourceKeeperService.stop(this);
            SimulcastOverlayService.stopCurrent(this);
            refreshUi(error);
            return;
        }

        SimulcastIntegration.setEnabled(this, true);
        enabled = true;
        SourceKeeperService.start(this);
        SimulcastOverlayService.startMonitor(this);
        refreshUi(null);
    }

    private void stopIntegration() {
        SimulcastIntegration.setEnabled(this, false);
        SimulcastIntegration.clearLastTargetPackage(this);
        enabled = false;
        SimulcastOverlayService.stopCurrent(this);
        SourceKeeperService.stop(this);
        refreshUi(null);
    }

    private String diagnoseStartError() {
        if (!isInstalled(DISHARE_PACKAGE)) {
            return "Simulcast не найден";
        }
        if (!Settings.canDrawOverlays(this)) {
            return "Нет разрешения поверх окон";
        }
        if (SimulcastApps.getSelected(this).isEmpty()) {
            return "Выберите приложения для трансляции";
        }
        if (installedCount(SourceKeeperService.SLOT_PACKAGES) == 0) {
            return "Не установлены слоты Simulcast";
        }
        return null;
    }

    private void refreshUi(String error) {
        if (toggleButton == null || statusText == null || errorText == null) {
            return;
        }
        enabled = SimulcastIntegration.isEnabled(this);
        if (enabled) {
            toggleButton.setText("STOP");
            toggleButton.setBackground(round(Color.rgb(190, 45, 58), dp(10)));
            statusText.setText("Сервис запущен. Это окно можно закрыть, интеграция работает в фоне.");
        } else {
            toggleButton.setText("START");
            toggleButton.setBackground(round(Color.rgb(12, 145, 105), dp(10)));
            statusText.setText(error == null
                    ? "Сервис остановлен."
                    : "Сервис не запущен.");
        }

        errorText.setText(error == null ? "" : error);

        if (accessibilityButton != null) {
            boolean needA11y = enabled && !isAccessibilityEnabled();
            accessibilityButton.setVisibility(needA11y ? View.VISIBLE : View.GONE);
            if (needA11y) {
                statusText.setText("Сервис запущен. Включите «наложение Simulcast» в спец. возможностях,"
                        + " чтобы российские приложения отображались поверх Simulcast.");
            }
        }
    }

    private int installedCount(String[] packages) {
        int count = 0;
        for (String packageName : packages) {
            if (isInstalled(packageName)) {
                count++;
            }
        }
        return count;
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
