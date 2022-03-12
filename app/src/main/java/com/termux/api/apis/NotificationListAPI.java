package com.termux.api.apis;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.JsonWriter;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.shared.logger.Logger;


public class NotificationListAPI {

    private static final String LOG_TAG = "NotificationListAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                listNotifications(context, out);
            }
        });
    }


    static void listNotifications(Context context, JsonWriter out) throws Exception {
        NotificationService notificationService = NotificationService.get();
        StatusBarNotification[] notifications = notificationService.getActiveNotifications();

        out.beginArray();
        for (StatusBarNotification n : notifications) {
            int id = n.getId();
            String key = "";
            String title = "";
            String text = "";
            CharSequence[] lines = null;
            String packageName = "";
            String tag = "";
            String group = "";
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String when = dateFormat.format(new Date(n.getNotification().when));

            if (n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE) != null) {
                title = n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString();
            }
            if (n.getNotification().extras.getCharSequence(Notification.EXTRA_BIG_TEXT) != null) {
                text = n.getNotification().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
            } else if (n.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT) != null) {
                text = n.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            }
            if (n.getNotification().extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) != null) {
                lines = n.getNotification().extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            }
            if (n.getTag() != null) {
                tag = n.getTag();
            }
            if (n.getNotification().getGroup() != null) {
                group = n.getNotification().getGroup();
            }
            if (n.getKey() != null) {
                key = n.getKey();
            }
            if (n.getPackageName() != null) {
                packageName = n.getPackageName();
            }
            out.beginObject()
                    .name("id").value(id)
                    .name("tag").value(tag)
                    .name("key").value(key)
                    .name("group").value(group)
                    .name("packageName").value(packageName)
                    .name("title").value(title)
                    .name("content").value(text)
                    .name("when").value(when);
            if (lines != null) {
                out.name("lines").beginArray();
                for (CharSequence line : lines) {
                    out.value(line.toString());
                }
                out.endArray();
            }
            out.endObject();
        }
        out.endArray();
    }



    public static class NotificationService extends NotificationListenerService {
        static NotificationService _this;

        public static NotificationService get() {
            return _this;
        }

        @Override
        public void onListenerConnected() {
            _this = this;
        }

        @Override
        public void onListenerDisconnected() {
            _this = null;
        }
    }

}
