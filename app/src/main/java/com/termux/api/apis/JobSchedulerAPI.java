package com.termux.api.apis;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import androidx.annotation.RequiresApi;
import android.text.TextUtils;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JobSchedulerAPI {

    private static final String LOG_TAG = "JobSchedulerAPI";

    private static String formatJobInfo(JobInfo jobInfo) {
        final String path = jobInfo.getExtras().getString(JobSchedulerService.SCRIPT_FILE_PATH);
        List<String> description = new ArrayList<String>();
        if (jobInfo.isPeriodic()) {
            description.add(String.format(Locale.ENGLISH, "(periodic: %dms)", jobInfo.getIntervalMillis()));
        }
        if (jobInfo.isRequireCharging()) {
            description.add("(while charging)");
        }
        if (jobInfo.isRequireDeviceIdle()) {
            description.add("(while idle)");
        }
        if (jobInfo.isPersisted()) {
            description.add("(persisted)");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (jobInfo.isRequireBatteryNotLow()) {
                description.add("(battery not low)");
            }
            if (jobInfo.isRequireStorageNotLow()) {
                description.add("(storage not low)");
            }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            description.add(String.format(Locale.ENGLISH, "(network: %s)", jobInfo.getRequiredNetwork().toString()));
        }

        return String.format(Locale.ENGLISH, "Job %d: %s\t%s", jobInfo.getId(), path,
                TextUtils.join(" ", description));
    }

    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final String scriptPath = intent.getStringExtra("script");

        final int jobId = intent.getIntExtra("job_id", 0);

        final boolean pending = intent.getBooleanExtra("pending", false);

        final boolean cancel = intent.getBooleanExtra("cancel", false);
        final boolean cancelAll = intent.getBooleanExtra("cancel_all", false);

        final int periodicMillis = intent.getIntExtra("period_ms", 0);
        String networkType = intent.getStringExtra("network");
        final boolean batteryNotLow = intent.getBooleanExtra("battery_not_low", true);
        final boolean charging = intent.getBooleanExtra("charging", false);
        final boolean persisted = intent.getBooleanExtra("persisted", false);
        final boolean idle = intent.getBooleanExtra("idle", false);
        final boolean storageNotLow = intent.getBooleanExtra("storage_not_low", false);

        int networkTypeCode;
        if (networkType != null) {
            switch (networkType) {
                case "any":
                    networkTypeCode = JobInfo.NETWORK_TYPE_ANY;
                    break;
                case "unmetered":
                    networkTypeCode = JobInfo.NETWORK_TYPE_UNMETERED;
                    break;
                case "cellular":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        networkTypeCode = JobInfo.NETWORK_TYPE_CELLULAR;
                    else
                        networkTypeCode = JobInfo.NETWORK_TYPE_UNMETERED;
                    break;
                case "not_roaming":
                    networkTypeCode = JobInfo.NETWORK_TYPE_NOT_ROAMING;
                    break;
                default:
                case "none":
                    networkTypeCode = JobInfo.NETWORK_TYPE_NONE;
                    break;
            }
        } else { // networkType == null
            networkTypeCode = JobInfo.NETWORK_TYPE_ANY;
        }


        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (pending) {
            displayPendingJobs(apiReceiver, intent, jobScheduler);
            return;
        }
        if (cancelAll) {
            displayPendingJobs(apiReceiver, intent, jobScheduler);
            ResultReturner.returnData(apiReceiver, intent, out -> out.println("Cancelling all jobs"));
            jobScheduler.cancelAll();
            return;
        } else if (cancel) {
            cancelJob(apiReceiver, intent, jobScheduler, jobId);
            return;
        }

        // Schedule new job
        if (scriptPath == null) {
            ResultReturner.returnData(apiReceiver, intent, out -> out.println("No script path given"));
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
            ResultReturner.returnData(apiReceiver, intent, out -> out.println(String.format(fileCheckMsg, scriptPath)));
            return;
        }

        PersistableBundle extras = new PersistableBundle();
        extras.putString(JobSchedulerService.SCRIPT_FILE_PATH, file.getAbsolutePath());

        ComponentName serviceComponent = new ComponentName(context, JobSchedulerService.class);
        JobInfo.Builder builder = new JobInfo.Builder(jobId, serviceComponent)
                .setExtras(extras)
                .setRequiredNetworkType(networkTypeCode)
                .setRequiresCharging(charging)
                .setPersisted(persisted)
                .setRequiresDeviceIdle(idle);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = builder.setRequiresBatteryNotLow(batteryNotLow);
            builder = builder.setRequiresStorageNotLow(storageNotLow);
        }

        if (periodicMillis > 0) {
            builder = builder.setPeriodic(periodicMillis);
        }

        JobInfo job = builder.build();

        final int scheduleResponse = jobScheduler.schedule(job);

        final String message = String.format(Locale.ENGLISH, "Scheduling %s - response %d", formatJobInfo(job), scheduleResponse);
        Logger.logInfo(LOG_TAG, message);
        ResultReturner.returnData(apiReceiver, intent, out -> out.println(message));


        displayPendingJobs(apiReceiver, intent, jobScheduler);

    }

    private static void displayPendingJobs(TermuxApiReceiver apiReceiver, Intent intent, JobScheduler jobScheduler) {
        // Display pending jobs
        final List<JobInfo> jobs = jobScheduler.getAllPendingJobs();
        if (jobs.isEmpty()) {
            ResultReturner.returnData(apiReceiver, intent, out -> out.println("No pending jobs"));
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (JobInfo job : jobs) {
            stringBuilder.append(String.format(Locale.ENGLISH, "Pending %s\n", formatJobInfo(job)));
        }
        ResultReturner.returnData(apiReceiver, intent, out -> out.println(stringBuilder.toString()));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static void cancelJob(TermuxApiReceiver apiReceiver, Intent intent, JobScheduler jobScheduler, int jobId) {
        final JobInfo jobInfo = jobScheduler.getPendingJob(jobId);
        if (jobInfo == null) {
            ResultReturner.returnData(apiReceiver, intent, out -> out.println(String.format(Locale.ENGLISH, "No job %d found", jobId)));
            return;
        }
        ResultReturner.returnData(apiReceiver, intent, out -> out.println(String.format(Locale.ENGLISH, "Cancelling %s", formatJobInfo(jobInfo))));
        jobScheduler.cancel(jobId);

    }



    @SuppressLint("SpecifyJobSchedulerIdRange")
    public static class JobSchedulerService extends JobService {

        public static final String SCRIPT_FILE_PATH = TermuxConstants.TERMUX_API_PACKAGE_NAME + ".jobscheduler_script_path";

        private static final String LOG_TAG = "JobSchedulerService";

        @Override
        public boolean onStartJob(JobParameters params) {
            Logger.logInfo(LOG_TAG, "onStartJob: " + params.toString());

            PersistableBundle extras = params.getExtras();
            String filePath = extras.getString(SCRIPT_FILE_PATH);

            ExecutionCommand executionCommand = new ExecutionCommand();
            executionCommand.executableUri = new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(filePath).build();
            executionCommand.runner = ExecutionCommand.Runner.APP_SHELL.getName();

            // Create execution intent with the action TERMUX_SERVICE#ACTION_SERVICE_EXECUTE to be sent to the TERMUX_SERVICE
            Intent executionIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executionCommand.executableUri);
            executionIntent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
            executionIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, executionCommand.runner);
            executionIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, true); // Also pass in case user using termux-app version < 0.119.0

            Context context = getApplicationContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // https://developer.android.com/about/versions/oreo/background.html
                context.startForegroundService(executionIntent);
            } else {
                context.startService(executionIntent);
            }

            Logger.logInfo(LOG_TAG, "Job started for \"" + filePath + "\"");

            return false;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            Logger.logInfo(LOG_TAG, "onStopJob: " + params.toString());
            return false;
        }
    }

}
