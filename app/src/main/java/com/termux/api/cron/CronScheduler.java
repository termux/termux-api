package com.termux.api.cron;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.work.*;
import com.termux.shared.logger.Logger;

import java.util.List;
import java.util.Locale;

public class CronScheduler {

    private static final String LOG_TAG = "CronScheduler";
    static final String WORKER_INPUT_ID = "id";
    static final String WORKER_INPUT_SCRIPT = "scriptPath";
    static final String WORKER_INPUT_MAX_RUNTIME = "maxRuntime";
    static final String WORKER_INPUT_CONTINUE = "continue";
    static final String WORKER_INPUT_DELAY = "delay";

    private CronScheduler() {
        /* static class */
    }

    public static void scheduleAlarmForJob(Context context, int id) {
        try {
            CronEntry entry = CronTab.getById(id);
            if (entry != null) {
                scheduleAlarmForJob(context, entry);
            } else {
                Logger.logError(LOG_TAG, String.format(Locale.getDefault(),
                        "Could not schedule next alarm for job id %d", id));
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, String.format(Locale.getDefault(),
                    "Could not schedule next alarm for job id %d", id));
            Logger.logStackTrace(e);
        }
    }

    public static void scheduleAlarmForJob(Context context, CronEntry entry) {
        Intent intent = entry.getIntent(context, CronReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        scheduleAlarm(context, entry.isExact(), entry.getNextExecutionTime(), pi, String.format(Locale.getDefault(),
                "Alarm scheduled for job id %d", entry.getId()));
    }

    public static void cancelAlarmForJob(Context context, CronEntry entry) {
        Intent intent = entry.getIntent(context, CronReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        cancelAlarm(context, pi, String.format(Locale.getDefault(),
                "Alarm canceled for job id %d", entry.getId()));
    }

    public static void enqueueWorkRequest(Context context, CronEntry entry) {
        Data inputData = new Data.Builder()
                .putInt(WORKER_INPUT_ID, entry.getId())
                .putString(WORKER_INPUT_SCRIPT, entry.getScriptPath())
                .putLong(WORKER_INPUT_MAX_RUNTIME, entry.getMaxRuntime())
                .putBoolean(WORKER_INPUT_CONTINUE, entry.continueOnFailingConstraint())
                .putInt(WORKER_INPUT_DELAY, entry.getGracePeriod())
                .build();

        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(CronWorker.class)
                .setInputData(inputData);

        if (!entry.hasNoConstraints()) {
            builder.setConstraints(entry.getConstraints());
            scheduleAlarmForConstraintsTimeout(context, entry);
        }

        WorkManager
                .getInstance(context)
                .enqueueUniqueWork(getUniqueWorkName(entry.getId()), ExistingWorkPolicy.KEEP, builder.build());

        Logger.logDebug(LOG_TAG, String.format(Locale.getDefault(),
                "CronWorker enqueued for job id %d", entry.getId()));
    }

    public static void scheduleAlarmForConstraintsTimeout(Context context, CronEntry entry) {
        long constraintTimeout = entry.getConstraintTimeout();

        if (constraintTimeout == 0) {
            Logger.logDebug(LOG_TAG, String.format(Locale.getDefault(),
                    "No constraint timeout scheduled for job id %d!", entry.getId()));
            return;
        }

        Intent intent = entry.getTimeoutIntent(context, CronReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        long triggerAtMillis = System.currentTimeMillis() + constraintTimeout;

        scheduleAlarm(context, entry.isExact(), triggerAtMillis, pi, String.format(Locale.getDefault(),
                "Alarm scheduled for constraint timeout for job id %d", entry.getId()));
    }

    public static void cancelAlarmForConstraintsTimeout(Context context, int id) {
        CronEntry entry = CronTab.getById(id);
        if (entry == null) {
            Logger.logError(LOG_TAG, String.format(Locale.getDefault(),
                    "Could not cancel constraint timeout alarm for job id %d", id));
            return;
        }

        Intent intent = entry.getTimeoutIntent(context, CronReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        cancelAlarm(context, pi, String.format(Locale.getDefault(),
                "Timeout constraint alarm canceled for job id %d", id));
    }

    public static void cancelWorkRequestDueToConstraintTimeout(Context context, int id) {
        String name = getUniqueWorkName(id);
        WorkManager workManager = WorkManager
                .getInstance(context);

        try {
            List<WorkInfo> workInfoList = workManager.getWorkInfosForUniqueWork(name).get();
            if (workInfoList != null
                    && !workInfoList.isEmpty()
                    && (workInfoList.get(0).getState() == WorkInfo.State.ENQUEUED)) {
                workManager.cancelUniqueWork(name);

                Logger.logDebug(LOG_TAG, String.format(Locale.getDefault(),
                        "CronWorker for job id %d canceled due to constraint timeout", id));

                CronScheduler.scheduleAlarmForJob(context, id);
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            Logger.logStackTrace(LOG_TAG, e);
        }
    }

    private static String getUniqueWorkName(int id) {
        return String.format(Locale.getDefault(), "cron-worker-%d", id);
    }

    private static void scheduleAlarm(Context context, boolean exact, long triggerAtMillis, PendingIntent pendingIntent, String logMessage) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 10*60*1000L, pendingIntent);
        }
        Logger.logDebug(LOG_TAG, logMessage);
    }

    private static void cancelAlarm(Context context, PendingIntent pendingIntent, String logMessage) {
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
            Logger.logDebug(LOG_TAG, logMessage);
        }
    }
}
