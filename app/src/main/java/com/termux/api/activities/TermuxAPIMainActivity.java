package com.termux.api.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.api.util.ViewUtils;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.models.ReportInfo;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.api.R;

public class TermuxAPIActivity extends AppCompatActivity {

    private TextView mBatteryOptimizationNotDisabledWarning;
    private TextView mDisplayOverOtherAppsPermissionNotGrantedWarning;

    private Button mDisableBatteryOptimization;
    private Button mGrantDisplayOverOtherAppsPermission;

    private static final String LOG_TAG = "TermuxAPIActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux_api);

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(this);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setToolbarTitle(this, com.termux.shared.R.id.toolbar, TermuxConstants.TERMUX_API_APP_NAME, 0);

        TextView pluginInfo = findViewById(R.id.textview_plugin_info);
        pluginInfo.setText(getString(R.string.plugin_info, TermuxConstants.TERMUX_GITHUB_REPO_URL,
                TermuxConstants.TERMUX_API_GITHUB_REPO_URL, TermuxConstants.TERMUX_API_APT_PACKAGE_NAME,
                TermuxConstants.TERMUX_API_APT_GITHUB_REPO_URL));

        mBatteryOptimizationNotDisabledWarning = findViewById(R.id.textview_battery_optimization_not_disabled_warning);
        mDisableBatteryOptimization = findViewById(R.id.btn_disable_battery_optimizations);
        mDisableBatteryOptimization.setOnClickListener(v -> requestDisableBatteryOptimizations());

        mDisplayOverOtherAppsPermissionNotGrantedWarning = findViewById(R.id.textview_display_over_other_apps_not_granted_warning);
        mGrantDisplayOverOtherAppsPermission = findViewById(R.id.btn_grant_display_over_other_apps_permission);
        mGrantDisplayOverOtherAppsPermission.setOnClickListener(v -> requestDisplayOverOtherAppsPermission());
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkIfBatteryOptimizationNotDisabled();
        checkIfDisplayOverOtherAppsPermissionNotGranted();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_termux_api, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_info) {
            showInfo();
            return true;
        } else if (id == R.id.menu_settings) {
            openSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showInfo() {
        new Thread() {
            @Override
            public void run() {
                String title = "About";

                StringBuilder aboutString = new StringBuilder();
                aboutString.append(TermuxUtils.getAppInfoMarkdownString(TermuxAPIActivity.this, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGE));
                aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(TermuxAPIActivity.this));
                aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(TermuxAPIActivity.this));

                ReportInfo reportInfo = new ReportInfo(title,
                        TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                reportInfo.setReportString(aboutString.toString());
                reportInfo.setReportSaveFileLabelAndPath(title,
                        Environment.getExternalStorageDirectory() + "/" +
                                FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + title + ".log", true, true));

                ReportActivity.startReportActivity(TermuxAPIActivity.this, reportInfo);
            }
        }.start();
    }



    private void checkIfBatteryOptimizationNotDisabled() {
        if (mBatteryOptimizationNotDisabledWarning == null) return;

        // If battery optimizations not disabled
        if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(this)) {
            ViewUtils.setWarningTextViewAndButtonState(this, mBatteryOptimizationNotDisabledWarning,
                    mDisableBatteryOptimization, true, getString(R.string.action_disable_battery_optimizations));
        } else {
            ViewUtils.setWarningTextViewAndButtonState(this, mBatteryOptimizationNotDisabledWarning,
                    mDisableBatteryOptimization, false, getString(R.string.action_already_disabled));
        }
    }

    private void requestDisableBatteryOptimizations() {
        Logger.logDebug(LOG_TAG, "Requesting to disable battery optimizations");
        PermissionUtils.requestDisableBatteryOptimizations(this, PermissionUtils.REQUEST_DISABLE_BATTERY_OPTIMIZATIONS);
    }



    private void checkIfDisplayOverOtherAppsPermissionNotGranted() {
        if (mDisplayOverOtherAppsPermissionNotGrantedWarning == null) return;

        // If display over other apps permission not granted
        if (!PermissionUtils.checkDisplayOverOtherAppsPermission(this)) {
            ViewUtils.setWarningTextViewAndButtonState(this, mDisplayOverOtherAppsPermissionNotGrantedWarning,
                    mGrantDisplayOverOtherAppsPermission, true, getString(R.string.action_grant_display_over_other_apps_permission));
        } else {
            ViewUtils.setWarningTextViewAndButtonState(this, mDisplayOverOtherAppsPermissionNotGrantedWarning,
                    mGrantDisplayOverOtherAppsPermission, false, getString(R.string.action_already_granted));
        }
    }

    private void requestDisplayOverOtherAppsPermission() {
        Logger.logDebug(LOG_TAG, "Requesting to grant display over other apps permission");
        PermissionUtils.requestDisplayOverOtherAppsPermission(this, PermissionUtils.REQUEST_GRANT_DISPLAY_OVER_OTHER_APPS_PERMISSION);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));

        switch (requestCode) {
            case PermissionUtils.REQUEST_DISABLE_BATTERY_OPTIMIZATIONS:
                if(PermissionUtils.checkIfBatteryOptimizationsDisabled(this))
                    Logger.logDebug(LOG_TAG, "Battery optimizations disabled by user on request.");
                else
                    Logger.logDebug(LOG_TAG, "Battery optimizations not disabled by user on request.");
                break;
            case PermissionUtils.REQUEST_GRANT_DISPLAY_OVER_OTHER_APPS_PERMISSION:
                if(PermissionUtils.checkDisplayOverOtherAppsPermission(this))
                    Logger.logDebug(LOG_TAG, "Display over other apps granted by user on request.");
                else
                    Logger.logDebug(LOG_TAG, "Display over other apps denied by user on request.");
                break;
            default:
                Logger.logError(LOG_TAG, "Unknown request code \"" + requestCode + "\" passed to onRequestPermissionsResult");
        }
    }



    private void openSettings() {
        ActivityUtils.startActivity(this, new Intent().setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME));
    }

}
