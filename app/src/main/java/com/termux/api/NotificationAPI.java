package com.termux.api;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class NotificationAPI {

    public static final String TERMUX_SERVICE = "com.termux.app.TermuxService";
    public static final String ACTION_EXECUTE = "com.termux.service_execute";
    public static final String EXTRA_ARGUMENTS = "com.termux.execute.arguments";
    public static final String BIN_SH = "/data/data/com.termux/files/usr/bin/sh";
    private static final String EXTRA_EXECUTE_IN_BACKGROUND = "com.termux.execute.background";
    private static final String CHANNEL_ID = "termux-notification";
    private static final String CHANNEL_TITLE = "Termux API notification channel";

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

        int ledColor = 0;

        if (lightsArgbExtra != null) {
            try {
                ledColor = Integer.parseInt(lightsArgbExtra, 16) | 0xff000000;
            } catch (NumberFormatException e) {
                TermuxApiLogger.error("Invalid LED color format! Ignoring!");
            }
        }

        int ledOnMs = intent.getIntExtra("led-on", 800);
        int ledOffMs = intent.getIntExtra("led-off", 800);

        long[] vibratePattern = intent.getLongArrayExtra("vibrate");
        boolean useSound = intent.getBooleanExtra("sound", false);
        boolean ongoing = intent.getBooleanExtra("ongoing", false);
        boolean alertOnce = intent.getBooleanExtra("alert-once", true);

        String actionExtra = intent.getStringExtra("action");

        String id = intent.getStringExtra("id");
        if (id == null) id = UUID.randomUUID().toString();
        final String notificationId = id;

        String groupKey = intent.getStringExtra("group");

        final Notification.Builder notification = new Notification.Builder(context);
        notification.setSmallIcon(R.drawable.ic_event_note_black_24dp);
        notification.setColor(0xFF000000);
        notification.setContentTitle(title);
        notification.setPriority(priority);
        notification.setOngoing(ongoing);
        notification.setOnlyAlertOnce(alertOnce);
        notification.setWhen(System.currentTimeMillis());



        String ImagePath = intent.getStringExtra("image-path");

        if(ImagePath != null){
            File imgFile = new  File(ImagePath);
            if(imgFile.exists()) {
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());

                notification.setLargeIcon(myBitmap)
                    .setStyle(new Notification.BigPictureStyle()
                    .bigPicture(myBitmap));
            }
        }

        String styleType = intent.getStringExtra("type");
        if(Objects.equals(styleType, "media")) {
            String mediaPrevious = intent.getStringExtra("media-previous");
            String mediaPause = intent.getStringExtra("media-pause");
            String mediaPlay = intent.getStringExtra("media-play");
            String mediaNext = intent.getStringExtra("media-next");

            if (mediaPrevious != null && mediaPause != null && mediaPlay != null && mediaNext != null) {
                notification.setSmallIcon(android.R.drawable.ic_media_play);

                PendingIntent previousIntent = createAction(context, mediaPrevious);
                PendingIntent pauseIntent = createAction(context, mediaPause);
                PendingIntent playIntent = createAction(context, mediaPlay);
                PendingIntent nextIntent = createAction(context, mediaNext);

                notification.addAction(new Notification.Action(android.R.drawable.ic_media_previous, "previous", previousIntent));
                notification.addAction(new Notification.Action(android.R.drawable.ic_media_pause, "pause", pauseIntent));
                notification.addAction(new Notification.Action(android.R.drawable.ic_media_play, "play", playIntent));
                notification.addAction(new Notification.Action(android.R.drawable.ic_media_next, "next", nextIntent));

                notification.setStyle(new Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 3));
            }
        }

        if (groupKey != null) notification.setGroup(groupKey);

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
            PendingIntent pi = createAction(context, actionExtra);
            notification.setContentIntent(pi);
        }

        for (int button = 1; button <= 3; button++) {
            String buttonText = intent.getStringExtra("button_text_" + button);
            String buttonAction = intent.getStringExtra("button_action_" + button);
            if (buttonText != null && buttonAction != null) {
                PendingIntent pi = createAction(context, buttonAction);
                notification.addAction(new Notification.Action(android.R.drawable.ic_input_add, buttonText, pi));
            }
        }

        String onDeleteActionExtra = intent.getStringExtra("on_delete_action");
        if (onDeleteActionExtra != null) {
            PendingIntent pi = createAction(context, onDeleteActionExtra);
            notification.setDeleteIntent(pi);
        }

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                            CHANNEL_TITLE, NotificationManager.IMPORTANCE_DEFAULT);
                    manager.createNotificationChannel(channel);
                    notification.setChannelId(CHANNEL_ID);
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

    static PendingIntent createAction(final Context context, String action){
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
