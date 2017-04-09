package com.termux.api;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import com.termux.api.util.ResultReturner;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.UUID;

public class NotificationAPI {

    public static final String TERMUX_SERVICE = "com.termux.app.TermuxService";
    public static final String ACTION_EXECUTE = "com.termux.service_execute";
    public static final String EXTRA_ARGUMENTS = "com.termux.execute.arguments";
    public static final String BIN_SH = "/data/data/com.termux/files/usr/bin/sh";
    private static final String EXTRA_EXECUTE_IN_BACKGROUND = "com.termux.execute.background";

    /**
     * Show a notification. Driven by the termux-show-notification script.
     */
    static void onReceiveShowNotification(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        String priorityExtra = intent.getStringExtra("priority");
        if (priorityExtra == null) priorityExtra = "default";
        int priority;
        switch (priorityExtra) {
            case "high":
                priority = Notification.PRIORITY_HIGH;
                break;
            case "low":
                priority = Notification.PRIORITY_LOW;
                break;
            case "max":
                priority = Notification.PRIORITY_MAX;
                break;
            case "min":
                priority = Notification.PRIORITY_MIN;
                break;
            default:
                priority = Notification.PRIORITY_DEFAULT;
                break;
        }

        String title = intent.getStringExtra("title");

        String lightsArgbExtra = intent.getStringExtra("led-color");
        int ledColor = (lightsArgbExtra == null) ? 0 : (Integer.parseInt(lightsArgbExtra, 16) | 0xff000000);
        int ledOnMs = intent.getIntExtra("led-on", 800);
        int ledOffMs = intent.getIntExtra("led-off", 800);

        long[] vibratePattern = intent.getLongArrayExtra("vibrate");
        boolean useSound = intent.getBooleanExtra("sound", false);

        String actionExtra = intent.getStringExtra("action");

        String id = intent.getStringExtra("id");
        if (id == null) id = UUID.randomUUID().toString();
        final String notificationId = id;

        final Notification.Builder notification = new Notification.Builder(context);
        notification.setSmallIcon(R.drawable.ic_event_note_black_24dp);
        notification.setColor(0xFF000000);
        notification.setContentTitle(title);
        notification.setPriority(priority);
        notification.setWhen(System.currentTimeMillis());

        if (ledColor != 0) {
            notification.setLights(ledColor, ledOnMs, ledOffMs);

            if (vibratePattern == null) {
                // Hack to make led work without vibrating.
                vibratePattern = new long[]{0};
            }
        }

        if (vibratePattern != null) {
            // Do not force the user to specify a delay first element, let it be 0.
            long[] vibrateArg = new long[vibratePattern.length + 1];
            System.arraycopy(vibratePattern, 0, vibrateArg, 1, vibratePattern.length);
            notification.setVibrate(vibrateArg);
        }

        if (useSound) notification.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);

        notification.setAutoCancel(true);

        if (actionExtra != null) {
            String[] arguments = new String[]{"-c", actionExtra};
            Uri executeUri = new Uri.Builder().scheme("com.termux.file")
                    .path(BIN_SH)
                    .appendQueryParameter("arguments", Arrays.toString(arguments))
                    .build();
            Intent executeIntent = new Intent(ACTION_EXECUTE, executeUri);
            executeIntent.setClassName("com.termux", TERMUX_SERVICE);
            executeIntent.putExtra(EXTRA_EXECUTE_IN_BACKGROUND, true);
            executeIntent.putExtra(EXTRA_ARGUMENTS, arguments);
            PendingIntent pi = PendingIntent.getService(context, 0, executeIntent, 0);
            notification.setContentIntent(pi);
        }

        for (int button = 1; button <= 3; button++) {
            String buttonText = intent.getStringExtra("button_text_" + button);
            String buttonAction = intent.getStringExtra("button_action_" + button);
            if (buttonText != null && buttonAction != null) {
                String[] arguments = new String[]{"-c", buttonAction};
                Uri executeUri = new Uri.Builder().scheme("com.termux.file")
                        .path(BIN_SH)
                        .appendQueryParameter("arguments", Arrays.toString(arguments))
                        .build();
                Intent executeIntent = new Intent(ACTION_EXECUTE, executeUri);
                executeIntent.setClassName("com.termux", TERMUX_SERVICE);
                executeIntent.putExtra(EXTRA_EXECUTE_IN_BACKGROUND, true);
                executeIntent.putExtra(EXTRA_ARGUMENTS, arguments);
                PendingIntent pi = PendingIntent.getService(context, 0, executeIntent, 0);
                notification.addAction(new Notification.Action(android.R.drawable.ic_input_add, buttonText, pi));
            }
        }

        String onDeleteActionExtra = intent.getStringExtra("on_delete_action");
        if (onDeleteActionExtra != null) {
            String[] arguments = new String[]{"-c", onDeleteActionExtra};
            Uri executeUri = new Uri.Builder().scheme("com.termux.file")
                    .path(BIN_SH)
                    .appendQueryParameter("arguments", Arrays.toString(arguments))
                    .build();
            Intent executeIntent = new Intent(ACTION_EXECUTE, executeUri);
            executeIntent.setClassName("com.termux", TERMUX_SERVICE);
            executeIntent.putExtra(EXTRA_EXECUTE_IN_BACKGROUND, true);
            executeIntent.putExtra(EXTRA_ARGUMENTS, arguments);
            PendingIntent pi = PendingIntent.getService(context, 0, executeIntent, 0);
            notification.setDeleteIntent(pi);
        }

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) throws Exception {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                if (!TextUtils.isEmpty(inputString)) {
                    if (inputString.contains("\n")) {
                        Notification.BigTextStyle style = new Notification.BigTextStyle();
                        style.bigText(inputString);
                        notification.setStyle(style);
                    } else {
                        notification.setContentText(inputString);
                    }
                }
                manager.notify(notificationId, 0, notification.build());
            }
        });
    }

    static void onReceiveRemoveNotification(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        ResultReturner.noteDone(apiReceiver, intent);
        String notificationId = intent.getStringExtra("id");
        if (notificationId != null) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(notificationId, 0);
        }
    }

}
