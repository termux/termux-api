package com.termux.api;

import android.service.notification.NotificationListenerService;

public class NotificationService extends NotificationListenerService {
    static NotificationService _this;

    public static NotificationService get() {
        NotificationService ret = _this;
        return ret;
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
