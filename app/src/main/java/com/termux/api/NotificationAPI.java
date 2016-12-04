package com.termux.api;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import com.termux.api.util.ResultReturner;

import java.util.UUID;

/**
 * Shows a notification. Driven by the termux-show-notification script.
 */
public class NotificationAPI {

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        String content = intent.getStringExtra("content");

        String notificationId = intent.getStringExtra("id");
        if (notificationId == null) notificationId = UUID.randomUUID().toString();

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

        String urlExtra = intent.getStringExtra("url");

        Notification.Builder notification = new Notification.Builder(context);
        notification.setSmallIcon(R.drawable.ic_event_note_black_24dp);
        notification.setColor(0xFF000000);
        notification.setContentTitle(title);
        notification.setContentText(content);
        notification.setPriority(priority);
        notification.setWhen(System.currentTimeMillis());

        if (ledColor != 0) notification.setLights(ledColor, ledOnMs, ledOffMs);

        if (vibratePattern != null) {
            // Do not force the user to specify a delay first element, let it be 0.
            long[] vibrateArg = new long[vibratePattern.length + 1];
            System.arraycopy(vibratePattern, 0, vibrateArg, 1, vibratePattern.length);
            notification.setVibrate(vibrateArg);
        }

        if (useSound) notification.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);


        if (urlExtra != null) {
            Intent urlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlExtra));
            PendingIntent pi = PendingIntent.getActivity(context, 0, urlIntent, 0);
            notification.setContentIntent(pi);
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(notificationId, 0, notification.build());

        ResultReturner.noteDone(apiReceiver, intent);
    }

}
