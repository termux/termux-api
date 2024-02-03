package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import com.termux.api.TermuxApiReceiver;
import com.termux.api.cron.CronTab;
import com.termux.api.cron.CronEntry;
import com.termux.api.cron.CronScheduler;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.util.List;
import java.util.Locale;

public class CronAPI {

    public static final String LOG_TAG = "CronAPI";

    private CronAPI() {
        /* static class */
    }

    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        if (intent.getBooleanExtra("list", false)) {
            handleList(apiReceiver, intent);
        } else if (intent.getIntExtra("info", -1) != -1) {
            handleInfo(apiReceiver, intent, intent.getIntExtra("info", -1));
        } else if (intent.getBooleanExtra("reschedule", false)) {
            handleRescheduleAll(apiReceiver, context, intent);
        } else if (intent.getBooleanExtra("delete_all", false)) {
            handleDeleteAll(apiReceiver, context, intent);
        } else if (intent.getIntExtra("delete", -1) != -1) {
            handleDelete(apiReceiver, context, intent, intent.getIntExtra("delete", -1));
        } else {
            handleAddJob(apiReceiver, context, intent);
        }
    }

    private static void handleAddJob(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        try {
            CronEntry entry = CronTab.add(intent);
            CronScheduler.scheduleAlarmForJob(context, entry);
            ResultReturner.returnData(apiReceiver, intent, out -> out.println(entry.describe()));
        } catch (Exception e) {
            Logger.logError(LOG_TAG, e.getMessage());
            Logger.logStackTrace(LOG_TAG, e);
            ResultReturner.returnData(apiReceiver, intent, out -> out.println(e.getMessage()));
        }
    }

    private static void handleList(TermuxApiReceiver apiReceiver, Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, out -> out.println(CronTab.print()));
    }

    private static void handleInfo(TermuxApiReceiver apiReceiver, Intent intent, int id) {
        CronEntry entry = CronTab.getById(id);
        if (entry != null) {
            ResultReturner.returnData(apiReceiver, intent, out -> out.println(entry.describe()));
        } else {
            ResultReturner.returnData(apiReceiver, intent, out ->
                    out.println(String.format(Locale.getDefault(), "Cron job with id %d not found", id)));
        }
    }

    private static void handleRescheduleAll(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        for (CronEntry entry : CronTab.getAll()) {
            CronScheduler.scheduleAlarmForJob(context, entry);
        }
        ResultReturner.returnData(apiReceiver, intent, out -> out.println("All cron jobs have been rescheduled"));
    }

    private static void handleDeleteAll(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        List<CronEntry> entries = CronTab.clear();
        for (CronEntry entry : entries) {
            CronScheduler.cancelAlarmForJob(context, entry);
        }
        ResultReturner.returnData(apiReceiver, intent, out -> out.println("All cron jobs deleted"));
    }

    private static void handleDelete(TermuxApiReceiver apiReceiver, Context context, Intent intent, int id) {
        CronEntry entry = CronTab.delete(id);
        if (entry != null) {
            CronScheduler.cancelAlarmForJob(context, entry);
            ResultReturner.returnData(apiReceiver, intent, out ->
                    out.println(String.format(Locale.getDefault(), "Deleted cron job with id %d", id)));
        } else {
            ResultReturner.returnData(apiReceiver, intent, out ->
                    out.println(String.format(Locale.getDefault(), "Cron job with id %d not found?", id)));
        }
    }
}
