package com.termux.api.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.Arrays;

public final class TermuxIntentHelper {
    private static final String TERMUX_SERVICE = "com.termux.app.TermuxService";
    private static final String ACTION_EXECUTE = "com.termux.service_execute";
    private static final String EXTRA_ARGUMENTS = "com.termux.execute.arguments";
    private static final String BIN_SH = "/data/data/com.termux/files/usr/bin/sh";
    private static final String EXTRA_EXECUTE_IN_BACKGROUND = "com.termux.execute.background";


    public static PendingIntent createPendingIntent(Context context, String action) {
        String[] arguments = new String[]{"-c", action};
        Uri executeUri = new Uri.Builder().scheme("com.termux.file")
                .path(BIN_SH)
                .appendQueryParameter("arguments", Arrays.toString(arguments))
                .build();
        Intent executeIntent = new Intent(ACTION_EXECUTE, executeUri);
        executeIntent.setClassName("com.termux", TERMUX_SERVICE);
        executeIntent.putExtra(EXTRA_EXECUTE_IN_BACKGROUND, true);
        executeIntent.putExtra(EXTRA_ARGUMENTS, arguments);
        return PendingIntent.getService(context, 0, executeIntent, 0);
    }
}
