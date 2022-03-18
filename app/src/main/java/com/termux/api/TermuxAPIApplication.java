package com.termux.api;

import android.app.Application;
import android.content.Context;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;


public class TermuxAPIApplication extends Application {

    public void onCreate() {
        super.onCreate();

        // Set crash handler for the app
        TermuxCrashUtils.setCrashHandler(this);
        ResultReturner.setContext(this);

        // Set log config for the app
        setLogConfig(getApplicationContext(), true);

        Logger.logDebug("Starting Application");

        SocketListener.createSocketListener(this);
    }

    public static void setLogConfig(Context context, boolean commitToFile) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_API_APP_NAME.replaceAll(":", ""));

        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAPIAppSharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel(true), commitToFile);
    }

}
