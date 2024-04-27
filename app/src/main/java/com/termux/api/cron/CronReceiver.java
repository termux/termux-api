package com.termux.api.cron;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static com.termux.api.TermuxAPIConstants.*;

public class CronReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "CronReceiver";

    static final Map<Integer, CountDownLatch> workerSignals = new ConcurrentHashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive Cron");
        Logger.logDebug(LOG_TAG, "Intent Received:\n" + IntentUtils.getIntentString(intent));

        int id = Integer.parseInt(intent.getData().getLastPathSegment());

        switch (intent.getData().getScheme()) {
            case TERMUX_API_CRON_ALARM_SCHEME:
                CronEntry entry = CronTab.getById(id);
                if (entry != null) {
                    CronScheduler.enqueueWorkRequest(context, entry);
                } else {
                    Logger.logWarn(String.format(Locale.getDefault(), "Cron job with id %d not found", id));
                }
                break;
            case TERMUX_API_CRON_EXECUTION_RESULT_SCHEME:
                CountDownLatch countDownLatch = workerSignals.get(id);
                if (countDownLatch != null) {
                    countDownLatch.countDown();
                }
                break;
            case TERMUX_API_CRON_CONSTRAINT_SCHEME:
                CronScheduler.cancelWorkRequestDueToConstraintTimeout(context, id);
                break;
            default:
                Logger.logError(LOG_TAG, "Unrecognized data URI scheme " + intent.getData().toString());
        }

        // some housekeeping
        workerSignals.entrySet().removeIf(e -> e.getValue().getCount() == 0);
    }
}
