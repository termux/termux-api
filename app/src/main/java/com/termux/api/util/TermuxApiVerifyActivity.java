package com.termux.api.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.termux.api.R;

import java.io.File;

/**
 * Verifies installation of termux-api package for user and provides notification
 * dialog if it isn't as well as help link to the Termux Wiki
 */
public class TermuxApiVerifyActivity extends Activity {
    private static final String TERMUX_API_WIKI_URL = "https://wiki.termux.com/wiki/Termux:API";
    private static final int DIALOG_DELAY = 2500;


    protected Dialog dialog;


    public static void checkAndNotifyToolStatus(final Context context) {
        if (!hasApiToolsInstalled()) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent installIntent = new Intent(context, TermuxApiVerifyActivity.class);
                    installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(installIntent);
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
        View dialogView = View.inflate(this, R.layout.dialog_missing_tools, null);
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
        String command = "pkg install termux-api && termux-notification --title 'Termux:API!' --content 'termux-api package installed successfully!'";
        execTermuxCommand(this, command);
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

    protected Dialog getDialog(View dialogView) {
        Dialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.version, getInstalledTermuxAPIVersion(this)))
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
     * Returns version number of our TermuxAPI
     * @param context
     * @return
     */
    protected static String getInstalledTermuxAPIVersion(Context context) {
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
    protected static void execTermuxCommand(Context context, String command) {
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
    protected static boolean hasApiToolsInstalled() {
        return new File("/data/data/com.termux/files/usr/libexec/termux-api").exists();
    }
}
