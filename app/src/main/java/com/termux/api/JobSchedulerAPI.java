package com.termux.api;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.PersistableBundle;
import android.os.Vibrator;
import android.util.Log;

import com.termux.api.util.ResultReturner;

import java.io.File;
import java.io.PrintWriter;

public class JobSchedulerAPI {

    private static final String LOG_TAG = "JobSchedulerAPI";


    static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {

        final String scriptPath = intent.getStringExtra("script");

        final int periodicMillis = intent.getIntExtra("period_ms", 0);
        final int jobId = intent.getIntExtra("job_id", 0);
        final String networkType = intent.getStringExtra("network");
        final boolean batteryNotLow = intent.getBooleanExtra("battery_not_low", true);
        final boolean charging = intent.getBooleanExtra("charging", false);
        final boolean idle = intent.getBooleanExtra("idle", false);
        final boolean storageNotLow = intent.getBooleanExtra("storage_not_low", false);

        int networkTypeCode = JobInfo.NETWORK_TYPE_NONE;
        if (networkType != null) {
            switch (networkType) {
                case "any":
                    networkTypeCode = JobInfo.NETWORK_TYPE_ANY;
                    break;
                case "unmetered":
                    networkTypeCode = JobInfo.NETWORK_TYPE_UNMETERED;
                    break;
                case "cellular":
                    networkTypeCode = JobInfo.NETWORK_TYPE_CELLULAR;
                    break;
                case "not_roaming":
                    networkTypeCode = JobInfo.NETWORK_TYPE_NOT_ROAMING;
                    break;
                default:
                case "none":
                    networkTypeCode = JobInfo.NETWORK_TYPE_NONE;
                    break;
            }
        }
        if (scriptPath == null) {
            ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultWriter() {
                @Override
                public void writeResult(PrintWriter out) {
                    out.println("No script path given");
                }
            });
            return;
        }
        final File file = new File(scriptPath);
        final String fileCheckMsg;
        if (!file.isFile()) {
            fileCheckMsg = "No such file: %s";
        } else if (!file.canRead()) {
            fileCheckMsg = "Cannot read file: %s";
        } else if (!file.canExecute()) {
            fileCheckMsg = "Cannot execute file: %s";
        } else {
            fileCheckMsg = "";
        }

        if (!fileCheckMsg.isEmpty()) {
            ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultWriter() {
                @Override
                public void writeResult(PrintWriter out) {
                    out.println(String.format(fileCheckMsg, scriptPath));
                }
            });
            return;
        }

        PersistableBundle extras = new PersistableBundle();
        extras.putString(SchedulerJobService.SCRIPT_FILE_PATH, file.getAbsolutePath());


        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        // Display pending jobs
        for (JobInfo job : jobScheduler.getAllPendingJobs()) {
            final JobInfo j = job;
            ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultWriter() {
                @Override
                public void writeResult(PrintWriter out) {
                    out.println(String.format("Pending job %d %s", j.getId(), j.toString()));
                }
            });
        }

        ComponentName serviceComponent = new ComponentName(context, SchedulerJobService.class);
        JobInfo.Builder builder = null;
        ;
        builder = new JobInfo.Builder(jobId, serviceComponent)
                .setExtras(extras)
                .setRequiredNetworkType(networkTypeCode)
                .setRequiresCharging(charging)
                .setRequiresDeviceIdle(idle);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = builder.setRequiresBatteryNotLow(batteryNotLow);
            builder = builder.setRequiresStorageNotLow(storageNotLow);
        }

        if (periodicMillis > 0) {
            builder = builder.setPeriodic(periodicMillis);
        }

        JobInfo job = builder.build();

        final int scheduleResponse = jobScheduler.schedule(job);

        Log.i(LOG_TAG, String.format("Scheduled job %d to call %s every %d ms - response %d",
                jobId, scriptPath, periodicMillis, scheduleResponse));
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultWriter() {
            @Override
            public void writeResult(PrintWriter out) {
                out.println(String.format("Scheduled job %d to call %s every %d ms - response %d",
                        jobId, scriptPath, periodicMillis, scheduleResponse));
            }
        });

    }

}
