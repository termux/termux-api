package com.termux.api.apis;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

public class AdbWifiAPI {

    private static final String LOG_TAG = "AdbWifiAPI";

    public static void onReceive(final TermuxApiReceiver receiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final ContentResolver contentResolver = context.getContentResolver();

        boolean enabled = intent.getBooleanExtra("enabled", true);

        Settings.Global.putInt(contentResolver, "adb_wifi_enabled", enabled?1:0);

        ResultReturner.noteDone(receiver, intent);
    }
}
