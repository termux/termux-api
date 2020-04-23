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
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.util.Pair;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
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
    private static final String KEY_TEXT_REPLY = "TERMUX_TEXT_REPLY";

    /**
     * Show a notification. Driven by the termux-show-notification script.
     */
    static void onReceiveShowNotification(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Pair<NotificationCompat.Builder, String> pair = buildNotification(context, intent);
        NotificationCompat.Builder notification = pair.first;
        String notificationId = pair.second;
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                if (!TextUtils.isEmpty(inputString)) {
                    if (inputString.contains("\n")) {
                        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
                        style.bigText(inputString);
                        notification.setStyle(style);
                    } else {
                        notification.setContentText(inputString);
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String priorityExtra = intent.getStringExtra("priority");
                    if (priorityExtra == null) priorityExtra = "default";
                    int importance;
                    switch (priorityExtra) {
                        case "high":
                        case "max":
                            importance = NotificationManager.IMPORTANCE_HIGH;
                            break;
                        case "low":
                            importance = NotificationManager.IMPORTANCE_LOW;
                            break;
                        case "min":
                            importance = NotificationManager.IMPORTANCE_MIN;
                            break;
                        default:
                            importance = NotificationManager.IMPORTANCE_DEFAULT;
                    }
                    NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                            CHANNEL_TITLE, importance);
                    manager.createNotificationChannel(channel);
                    notification.setChannelId(CHANNEL_ID);
                }

                manager.notify(notificationId, 0, notification.build());
            }
        });
    }


    static Pair<NotificationCompat.Builder, String> buildNotification(final Context context, final Intent intent) {
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
        boolean alertOnce = intent.getBooleanExtra("alert-once", false);

        String actionExtra = intent.getStringExtra("action");

        final String notificationId = getNotificationId(intent);

        String groupKey = intent.getStringExtra("group");

        final NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                CHANNEL_ID);
        notification.setSmallIcon(R.drawable.ic_event_note_black_24dp);
        notification.setColor(0xFF000000);
        notification.setContentTitle(title);
        notification.setPriority(priority);
        notification.setOngoing(ongoing);
        notification.setOnlyAlertOnce(alertOnce);
        notification.setWhen(System.currentTimeMillis());
        notification.setShowWhen(true);

        String SmallIcon = intent.getStringExtra("icon");

        if (SmallIcon != null) {
            final Class<?> clz = R.drawable.class;
            final Field[] fields = clz.getDeclaredFields();
            for (Field field : fields) {
                String name = field.getName();
                if (name.equals("ic_" + SmallIcon + "_black_24dp")) {
                    try {
                        notification.setSmallIcon(field.getInt(clz));
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        }

        String ImagePath = intent.getStringExtra("image-path");

        if (ImagePath != null) {
            File imgFile = new File(ImagePath);
            if (imgFile.exists()) {
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());

                notification.setLargeIcon(myBitmap)
                        .setStyle(new NotificationCompat.BigPictureStyle()
                                .bigPicture(myBitmap));
            }
        }

        String styleType = intent.getStringExtra("type");
        if (Objects.equals(styleType, "media")) {
            String mediaPrevious = intent.getStringExtra("media-previous");
            String mediaPause = intent.getStringExtra("media-pause");
            String mediaPlay = intent.getStringExtra("media-play");
            String mediaNext = intent.getStringExtra("media-next");

            if (mediaPrevious != null && mediaPause != null && mediaPlay != null && mediaNext != null) {
                if (SmallIcon == null) {
                    notification.setSmallIcon(android.R.drawable.ic_media_play);
                }

                PendingIntent previousIntent = createAction(context, mediaPrevious);
                PendingIntent pauseIntent = createAction(context, mediaPause);
                PendingIntent playIntent = createAction(context, mediaPlay);
                PendingIntent nextIntent = createAction(context, mediaNext);

                notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, "previous", previousIntent));
                notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, "pause", pauseIntent));
                notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, "play", playIntent));
                notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, "next", nextIntent));

                notification.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
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
                if (buttonAction.contains("$REPLY")) {
                    NotificationCompat.Action action = createReplyAction(context, intent,
                            button,
                            buttonText, buttonAction, notificationId);
                    notification.addAction(action);
                } else {
                PendingIntent pi = createAction(context, buttonAction);
                    notification.addAction(new NotificationCompat.Action(android.R.drawable.ic_input_add, buttonText, pi));
                }
            }
        }

        String onDeleteActionExtra = intent.getStringExtra("on_delete_action");
        if (onDeleteActionExtra != null) {
            PendingIntent pi = createAction(context, onDeleteActionExtra);
            notification.setDeleteIntent(pi);
        }
        return new Pair(notification, notificationId);
    }

    private static String getNotificationId(Intent intent) {
        String id = intent.getStringExtra("id");
        if (id == null) id = UUID.randomUUID().toString();
        return id;
    }

    static void onReceiveRemoveNotification(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        ResultReturner.noteDone(apiReceiver, intent);
        String notificationId = intent.getStringExtra("id");
        if (notificationId != null) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(notificationId, 0);
        }
    }

    static NotificationCompat.Action createReplyAction(final Context context, Intent intent,
                                                       int buttonNum,
                                                       String buttonText,
                                                       String buttonAction, String notificationId) {
        String replyLabel = buttonText;
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(replyLabel)
                .build();

        // Build a PendingIntent for the reply action to trigger.
        PendingIntent replyPendingIntent =
                PendingIntent.getBroadcast(context,
                        buttonNum,
                        getMessageReplyIntent((Intent)intent.clone(), buttonText, buttonAction, notificationId),
                        PendingIntent.FLAG_UPDATE_CURRENT);

        // Create the reply action and add the remote input.
        NotificationCompat.Action action =
                new NotificationCompat.Action.Builder(R.drawable.ic_event_note_black_24dp,
                        buttonText,
                        replyPendingIntent)
                        .addRemoteInput(remoteInput)
                        .build();

        return action;
    }

    private static Intent getMessageReplyIntent(Intent oldIntent,
                                                String buttonText, String buttonAction,
                                                String notificationId) {
        Intent intent = oldIntent.
                setClassName("com.termux.api", "com.termux.api.TermuxApiReceiver").
                putExtra("api_method", "NotificationReply").
                putExtra("id", notificationId).
                putExtra("action", buttonAction).
                putExtra("replyKey", buttonText);
        return intent;
    }


    static private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(KEY_TEXT_REPLY);
        }
        return null;
    }

    static CharSequence shellEscape(CharSequence input) {
        return "\"" + input.toString().replace("\"", "\\\"") + "\"";
    }

    static void onReceiveReplyToNotification(TermuxApiReceiver termuxApiReceiver,
                                                    Context context, Intent intent) {
        String replyKey = intent.getStringExtra("replyKey");
        CharSequence reply = getMessageText(intent);

        String action = intent.getStringExtra("action")
                .replace("$REPLY", shellEscape(reply));
        try {
            createAction(context, action).send();
        } catch (PendingIntent.CanceledException e) {
            TermuxApiLogger.error("CanceledException when performing action: " + action);
        }

        String notificationId = intent.getStringExtra("id");
        boolean ongoing = intent.getBooleanExtra("ongoing", false);
        Notification repliedNotification;
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (ongoing) {
            // Re-issue the new notification to clear the spinner
            repliedNotification = buildNotification(context, intent).first.build();
            notificationManager.notify(notificationId, 0, repliedNotification);
        } else {
            // Cancel the notification
            notificationManager.cancel(notificationId, 0);
        }
    }

    static Intent createExecuteIntent(String action){
        String[] arguments = new String[]{"-c", action};
        Uri executeUri = new Uri.Builder().scheme("com.termux.file")
                .path(BIN_SH)
                .appendQueryParameter("arguments", Arrays.toString(arguments))
                .build();
        Intent executeIntent = new Intent(ACTION_EXECUTE, executeUri);
        executeIntent.setClassName("com.termux", TERMUX_SERVICE);
        executeIntent.putExtra(EXTRA_EXECUTE_IN_BACKGROUND, true);
        executeIntent.putExtra(EXTRA_ARGUMENTS, arguments);
        return executeIntent;
    }

    static PendingIntent createAction(final Context context, String action){
        Intent executeIntent = createExecuteIntent(action);
        return PendingIntent.getService(context, 0, executeIntent, 0);
    }
}
