package com.termux.api.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.api.TermuxAPIApplication;
import com.termux.api.settings.activities.TermuxAPISettingsActivity;
import com.termux.api.util.ViewUtils;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.api.R;

public class TermuxAPIMainActivity extends AppCompatActivity {

    private TextView mBatteryOptimizationNotDisabledWarning;
    private TextView mDisplayOverOtherAppsPermissionNotGrantedWarning;

    private Button mDisableBatteryOptimization;
    private Button mGrantDisplayOverOtherAppsPermission;

    public static final String LOG_TAG = "TermuxAPIMainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux_api_main);

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
        mGrantDisplayOverOtherAppsPermission = findViewById(R.id.button_grant_display_over_other_apps_permission);
        mGrantDisplayOverOtherAppsPermission.setOnClickListener(v -> requestDisplayOverOtherAppsPermission());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set log level for the app
        TermuxAPIApplication.setLogConfig(this, false);

        Logger.logVerbose(LOG_TAG, "onResume");

        checkIfBatteryOptimizationNotDisabled();
        checkIfDisplayOverOtherAppsPermissionNotGranted();
        setChangeLauncherActivityStateViews();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_termux_api_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_settings) {
            openSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
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



    private void setChangeLauncherActivityStateViews() {
        String packageName = TermuxConstants.TERMUX_API_PACKAGE_NAME;
        String className = TermuxConstants.TERMUX_API_APP.TERMUX_API_LAUNCHER_ACTIVITY_NAME;

        TextView changeLauncherActivityStateTextView = findViewById(R.id.textview_change_launcher_activity_state_details);
        changeLauncherActivityStateTextView.setText(MarkdownUtils.getSpannedMarkdownText(this,
                getString(R.string.msg_change_launcher_activity_state_info, packageName, getClass().getName())));

        Button changeLauncherActivityStateButton = findViewById(R.id.button_change_launcher_activity_state);
        String stateChangeMessage;
        boolean newState;

        Boolean currentlyDisabled = PackageUtils.isComponentDisabled(this,
                packageName, className, false);
        if (currentlyDisabled == null) {
            Logger.logError(LOG_TAG, "Failed to check if \"" + packageName + "/" + className + "\" launcher activity is disabled");
            changeLauncherActivityStateButton.setEnabled(false);
            changeLauncherActivityStateButton.setAlpha(.5f);
            changeLauncherActivityStateButton.setText(com.termux.shared.R.string.action_disable_launcher_icon);
            changeLauncherActivityStateButton.setOnClickListener(null);
            return;
        }

        changeLauncherActivityStateButton.setEnabled(true);
        changeLauncherActivityStateButton.setAlpha(1f);
        if (currentlyDisabled) {
            changeLauncherActivityStateButton.setText(com.termux.shared.R.string.action_enable_launcher_icon);
            stateChangeMessage = getString(com.termux.shared.R.string.msg_enabling_launcher_icon, TermuxConstants.TERMUX_API_APP_NAME);
            newState = true;
        } else {
            changeLauncherActivityStateButton.setText(com.termux.shared.R.string.action_disable_launcher_icon);
            stateChangeMessage = getString(com.termux.shared.R.string.msg_disabling_launcher_icon, TermuxConstants.TERMUX_API_APP_NAME);
            newState = false;
        }

        changeLauncherActivityStateButton.setOnClickListener(v -> {
            Logger.logInfo(LOG_TAG, stateChangeMessage);
            String errmsg = PackageUtils.setComponentState(this,
                    packageName, className, newState, stateChangeMessage, true);
            if (errmsg == null)
                setChangeLauncherActivityStateViews();
            else
                Logger.logError(LOG_TAG, errmsg);
        });
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
        ActivityUtils.startActivity(this, new Intent().setClass(this, TermuxAPISettingsActivity.class));
    }

}
