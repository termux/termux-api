package com.termux.api.util;

import android.util.Log;

public class TermuxApiLogger {

    private static final String TAG = "termux-api";

    public static void info(String message) {
        Log.i(TAG, message);
    }

    public static void error(String message) {
        Log.e(TAG, message);
    }

    public static void error(String message, Exception exception) {
        Log.e(TAG, message, exception);
    }

}
