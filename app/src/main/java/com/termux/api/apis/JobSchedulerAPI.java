package com.termux.api.apis;

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
import java.io.PrintWriter;
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

        return String.format(Locale.ENGLISH, "Job %d: %s    %s", jobInfo.getId(), path,
                TextUtils.join(" ", description));
    }

    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final boolean pending = intent.getBooleanExtra("pending", false);
        final boolean cancel = intent.getBooleanExtra("cancel", false);
        final boolean cancelAll = intent.getBooleanExtra("cancel_all", false);

        if (pending) {
            ResultReturner.returnData(apiReceiver, intent, out -> {
                runDisplayPendingJobsAction(context, out);
            });
        } else if (cancelAll) {
            ResultReturner.returnData(apiReceiver, intent, out -> {
                runCancelAllJobsAction(context, out);
            });
        } else if (cancel) {
            ResultReturner.returnData(apiReceiver, intent, out -> {
                runCancelJobAction(context, intent, out);
            });
        } else {
            ResultReturner.returnData(apiReceiver, intent, out -> {
                runScheduleJobAction(context, intent, out);
            });
        }
    }

    private static void runScheduleJobAction(Context context, Intent intent, PrintWriter out) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        int jobId = intent.getIntExtra("job_id", 0);

        Logger.logVerbose(LOG_TAG, "schedule_job: Running action for job " + jobId);

        String scriptPath = intent.getStringExtra("script");
        String networkType = intent.getStringExtra("network");
        int periodicMillis = intent.getIntExtra("period_ms", 0);
        boolean batteryNotLow = intent.getBooleanExtra("battery_not_low", true);
        boolean charging = intent.getBooleanExtra("charging", false);
        boolean persisted = intent.getBooleanExtra("persisted", false);
        boolean idle = intent.getBooleanExtra("idle", false);
        boolean storageNotLow = intent.getBooleanExtra("storage_not_low", false);


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


        if (scriptPath == null) {
            Logger.logErrorPrivate(LOG_TAG, "schedule_job: " + "Script path not passed");
            out.println("No script path given");
            return;
        }

        File file = new File(scriptPath);
        String fileCheckMsg;
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
            Logger.logErrorPrivate(LOG_TAG, "schedule_job: " + String.format(fileCheckMsg, scriptPath));
            out.println(String.format(fileCheckMsg, scriptPath));
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
            // For Android `>= 7`, the minimum period is 900000ms (15 minutes).
            // - https://developer.android.com/reference/android/app/job/JobInfo#getMinPeriodMillis()
            // - https://cs.android.com/android/_/android/platform/frameworks/base/+/10be4e90
            builder = builder.setPeriodic(periodicMillis);
        }

        JobInfo jobInfo = builder.build();
        final int scheduleResponse = jobScheduler.schedule(jobInfo);
        String message = String.format(Locale.ENGLISH, "Scheduling %s - response %d", formatJobInfo(jobInfo), scheduleResponse);
        printMessage(out, "schedule_job", message);

        displayPendingJob(out, jobScheduler, "schedule_job", "Pending", jobId);
    }

    private static void runDisplayPendingJobsAction(Context context, PrintWriter out) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        Logger.logVerbose(LOG_TAG, "display_pending_jobs: Running action");
        displayPendingJobs(out, jobScheduler, "display_pending_jobs", "Pending");
    }

    private static void runCancelAllJobsAction(Context context, PrintWriter out) {
        Logger.logVerbose(LOG_TAG, "cancel_all_jobs: Running action");
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        int jobsCount = displayPendingJobs(out, jobScheduler, "cancel_all_jobs", "Cancelling");
        if (jobsCount >= 0) {
            Logger.logVerbose(LOG_TAG, "cancel_all_jobs: Cancelling " + jobsCount + " jobs");
            jobScheduler.cancelAll();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static void runCancelJobAction(Context context, Intent intent, PrintWriter out) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (!intent.hasExtra("job_id")) {
            Logger.logErrorPrivate(LOG_TAG, "cancel_job: Job id not passed");
            out.println("Job id not passed");
            return;
        }

        int jobId = intent.getIntExtra("job_id", 0);
        Logger.logVerbose(LOG_TAG, "cancel_job: Running action for job " + jobId);

        if (displayPendingJob(out, jobScheduler, "cancel_job", "Cancelling", jobId)) {
            Logger.logVerbose(LOG_TAG, "cancel_job: Cancelling job " + jobId);
            jobScheduler.cancel(jobId);
        }
    }



    private static boolean displayPendingJob(PrintWriter out, JobScheduler jobScheduler,
                                             String actionTag, String actionLabel, int jobId) {
        JobInfo jobInfo = jobScheduler.getPendingJob(jobId);
        if (jobInfo == null) {
            printMessage(out, actionTag, String.format(Locale.ENGLISH, "No job %d found", jobId));
            return false;
        }

        printMessage(out, actionTag, String.format(Locale.ENGLISH, actionLabel + " %s", formatJobInfo(jobInfo)));
        return true;
    }


    private static int displayPendingJobs(PrintWriter out, JobScheduler jobScheduler, String actionTag, String actionLabel) {
        List<JobInfo> jobs = jobScheduler.getAllPendingJobs();
        if (jobs.isEmpty()) {
            printMessage(out, actionTag, "No jobs found");
            return 0;
        }

        StringBuilder stringBuilder = new StringBuilder();
        boolean jobAdded = false;
        for (JobInfo job : jobs) {
            if (jobAdded) stringBuilder.append("\n");
            stringBuilder.append(String.format(Locale.ENGLISH, actionLabel + " %s", formatJobInfo(job)));
            jobAdded = true;
        }
        printMessage(out, actionTag, stringBuilder.toString());

        return jobs.size();
    }



    private static void printMessage(PrintWriter out, String actionTag, String message) {
        Logger.logVerbose(LOG_TAG, actionTag + ": " + message);
        out.println(message);
    }



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
