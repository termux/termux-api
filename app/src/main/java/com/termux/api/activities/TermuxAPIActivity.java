package com.termux.api.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.models.ReportInfo;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.api.R;

public class TermuxAPIActivity extends AppCompatActivity {

    private static final String LOG_TAG = "TermuxAPIActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux_api);

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(this);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        AppCompatActivityUtils.setToolbar(this, R.id.toolbar);
        AppCompatActivityUtils.setToolbarTitle(this, R.id.toolbar, TermuxConstants.TERMUX_API_APP_NAME, 0);

        TextView pluginInfo = findViewById(R.id.textview_plugin_info);
        pluginInfo.setText(getString(R.string.plugin_info, TermuxConstants.TERMUX_GITHUB_REPO_URL,
                TermuxConstants.TERMUX_API_GITHUB_REPO_URL, TermuxConstants.TERMUX_API_APT_PACKAGE_NAME,
                TermuxConstants.TERMUX_API_APT_GITHUB_REPO_URL));
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

    private void openSettings() {
        ActivityUtils.startActivity(this, new Intent().setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME));
    }

}
