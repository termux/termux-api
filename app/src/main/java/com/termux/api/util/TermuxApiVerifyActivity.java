package com.termux.api.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.Toast;

import com.termux.api.R;

import java.io.File;

/**
 * Verifies installation of termux-api package for user and provides notification
 * dialog if it isn't as well as help link to the Termux Wiki
 */
public class TermuxApiVerifyActivity extends Activity {
    private static final String TERMUX_API_WIKI_URL = "https://wiki.termux.com/wiki/Termux:API";
    private static final String EXTRA_NOTIFY_TYPE = "notify_type";


    private static final int EXTRA_NOTIFY_NOT_INSTALLED = R.layout.dialog_missing_tools;
    private static final int EXTRA_NOTIFY_UPDATE = R.layout.dialog_tools_update;

    private static final int DIALOG_DELAY = 2500;


    protected Dialog dialog;


    public static void checkAndNotifyToolStatus(final Context context) {
        boolean shouldNotifyUser = false;

        final Intent notifyIntent = new Intent(context, TermuxApiVerifyActivity.class);
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (!TermuxApiHelper.hasToolsInstalled()) {
            notifyIntent.putExtra(EXTRA_NOTIFY_TYPE, EXTRA_NOTIFY_NOT_INSTALLED);
            shouldNotifyUser = true;
        } else if (TermuxApiHelper.checkIfVersionChanged(context)) {
            notifyIntent.putExtra(EXTRA_NOTIFY_TYPE, EXTRA_NOTIFY_UPDATE);
            shouldNotifyUser = TermuxApiHelper.canRemindUser(context);
        }

        if (shouldNotifyUser) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    context.startActivity(notifyIntent);
                }
            }, DIALOG_DELAY);
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFinishOnTouchOutside(false);
        showDialog();
    }

    protected void showDialog() {
        int dialogType = getIntent().getIntExtra(EXTRA_NOTIFY_TYPE, 0);
        View dialogView = View.inflate(this, dialogType, null);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        dialog = getDialog(dialogView);
        dialog.show();
    }

    /**
     * Installs termux-api package
     * @param view
     */
    public void installToolsButtonClicked(View view) {
        dismiss();

        Toast.makeText(this, "Installing termux-api", Toast.LENGTH_SHORT).show();

        // install termux-api and use our notification api to display success
        String command = "pkg install termux-api && termux-notification --content 'termux-api package installed successfully!'";
        TermuxApiHelper.execTermuxCommand(this, command);
        TermuxApiHelper.saveVersion(this);
    }

    /**
     * Updates Termux packages
     * @param view
     */
    public void updateButtonClicked(View view) {
        dismiss();

        Toast.makeText(this, "Updated packages", Toast.LENGTH_SHORT).show();

        // update packages and use our notification api to display success
        String command = "yes | pkg update && termux-notification --content 'packages updated successfully!'";
        TermuxApiHelper.execTermuxCommand(this, command);
        TermuxApiHelper.saveVersion(this);
    }

    /**
     * Launches TermuxAPI Wiki page
     * @param view
     */
    public void learnMoreButtonClicked(View view) {
        dismiss();

        Intent urlIntent = new Intent(Intent.ACTION_VIEW);
        urlIntent.setData(Uri.parse(TERMUX_API_WIKI_URL));
        startActivity(urlIntent);
    }

    /**
     * Toggle preference to remind user
     * @param view
     */
    public void remindMeCheckBoxClicked(View view) {
        CheckBox checkBox = (CheckBox)view;
        // if box is NOT checked, we CAN remind the user
        TermuxApiHelper.setCanRemindUser(this, !checkBox.isChecked());
    }

    protected Dialog getDialog(View dialogView) {
        Dialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.version, TermuxApiHelper.getInstalledVersion(this)))
                .setView(dialogView)
                .setPositiveButton("Okay", null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        finish();
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    protected void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }


    /**
     * Helper class to manage installation
     */
    static final class TermuxApiHelper {
        private static final String PREFERENCES = "termux_api_prefs";
        private static final String KEY_API_VERSION = "api_version";
        private static final String KEY_REMINDER = "reminder";

        /**
         * Check if installed version doesn't match our previously stored version
         * @param context
         * @return
         */
        static boolean checkIfVersionChanged(Context context) {
            SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            final String installedApiVersion = getInstalledVersion(context);
            return !preferences.getString(KEY_API_VERSION, "").equals(installedApiVersion);
        }

        /**
         * Checks to see if we can remind user
         * @param context
         * @return
         */
        static boolean canRemindUser(Context context) {
            SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            return preferences.getBoolean(KEY_REMINDER, true);
        }

        /**
         * Returns version number of our TermuxAPI
         * @param context
         * @return
         */
        static String getInstalledVersion(Context context) {
            String version = "Unknown";
            try {
                PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                version = packageInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                TermuxApiLogger.error("Failed to obtain TermuxAPIVersion", e);
            }
            return version;
        }

        /**
         * Executes specified command through Termux using a PendingIntent
         * @param context
         * @param command
         */
        static void execTermuxCommand(Context context, String command) {
            PendingIntent pi = TermuxIntentHelper.createPendingIntent(context, command);
            try {
                pi.send();
            } catch (PendingIntent.CanceledException e) {
                TermuxApiLogger.error("execTermuxCommand error", e);
            }
        }

        /**
         * Checks to see if 'termux-api' pkg is installed
         * @return
         */
        @SuppressLint("SdCardPath")
        static boolean hasToolsInstalled() {
            return new File("/data/data/com.termux/files/usr/libexec/termux-api").exists();
        }

        /**
         * Saves installed version to preferences
         * @param context
         */
        static void saveVersion(Context context) {
            SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(KEY_API_VERSION, getInstalledVersion(context));
            editor.apply();
        }

        static void setCanRemindUser(Context context, boolean canRemind) {
            SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(KEY_REMINDER, canRemind);
            editor.apply();
        }
    }
}
