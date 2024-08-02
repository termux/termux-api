package com.termux.api.cron;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;

public class CronBootReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "CronBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Logger.logDebug(LOG_TAG, "Unknown intent Received:\n" + IntentUtils.getIntentString(intent));
            return;
        }

        for (CronEntry entry : CronTab.getAll()) {
            CronScheduler.scheduleAlarmForJob(context, entry);
        }
    }
}
