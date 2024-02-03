package com.termux.api.cron;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.termux.api.TermuxAPIConstants.TERMUX_API_CRON_EXECUTION_RESULT_SCHEME;
import static com.termux.api.cron.CronScheduler.*;

public class CronWorker extends Worker {

    private static final String LOG_TAG = "CronWorker";

    private Uri executableUri;
    private int jobId;
    private long maxRuntime;
    private boolean continueOnConstraints;
    private int gracePeriod;

    public CronWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Logger.logDebug(LOG_TAG, getId() + " Work started");

        handleInputData();

        CronScheduler.cancelAlarmForConstraintsTimeout(getApplicationContext(), jobId);
        sendStartIntent();

        CountDownLatch doneSignal = new CountDownLatch(1);
        CronReceiver.workerSignals.put(jobId, doneSignal);

        try {
            Logger.logDebug(LOG_TAG, getId() + " Waiting for Termux to finish");

            boolean hasFinished;
            if (maxRuntime == 0) {
                doneSignal.await();
                hasFinished = true;
            } else {
                hasFinished = doneSignal.await(maxRuntime, TimeUnit.SECONDS);
            }

            if (isStopped()) {
                return Result.failure();
            }

            if (hasFinished) {
                Logger.logDebug(LOG_TAG, getId() + " Work finished");
                scheduleNextExecution();
                return Result.success();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Logger.logDebug(LOG_TAG, getId() + " Work aborted or other error");

        sendKillIntent();
        scheduleNextExecution();
        return Result.failure();
    }

    private void handleInputData() {
        Data inputData = getInputData();

        jobId = inputData.getInt(WORKER_INPUT_ID, -1);
        if (jobId == -1) {
            throw new IllegalArgumentException("id should be set!");
        }

        String scriptPath = inputData.getString(WORKER_INPUT_SCRIPT);
        this.executableUri = new Uri.Builder()
                .scheme(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
                .path(scriptPath)
                .build();

        maxRuntime = inputData.getLong(WORKER_INPUT_MAX_RUNTIME, 3600);
        continueOnConstraints = inputData.getBoolean(WORKER_INPUT_CONTINUE, false);
        gracePeriod = inputData.getInt(WORKER_INPUT_DELAY, 5000);
    }

    @Override
    public void onStopped() {
        if (continueOnConstraints) {
            Logger.logDebug(LOG_TAG, getId() + " Constraints no longer apply - continuing anyway!");
            return;
        }

        Logger.logDebug(LOG_TAG, getId() + " Work stopped!");

        sendKillIntent();
        scheduleNextExecution();

        CountDownLatch countDownLatch = CronReceiver.workerSignals.get(jobId);
        if (countDownLatch != null) {
            countDownLatch.countDown();
        }
    }

    private void sendStartIntent() {
        ExecutionCommand executionCommand = new ExecutionCommand();
        executionCommand.runner = ExecutionCommand.Runner.APP_SHELL.getName();
        executionCommand.executableUri = executableUri;

        // Create pendingIntent so the result is reported back
        Intent resultIntent = new Intent(getApplicationContext(), CronReceiver.class);
        resultIntent.setData(new Uri.Builder()
                .scheme(TERMUX_API_CRON_EXECUTION_RESULT_SCHEME)
                .appendPath("id")
                .appendPath(String.valueOf(jobId))
                .build());
        @SuppressLint("UnspecifiedImmutableFlag")
        PendingIntent pi = PendingIntent.getBroadcast(getApplicationContext(), 0, resultIntent, 0);

        // Create execution intent with the action TERMUX_SERVICE#ACTION_SERVICE_EXECUTE to be sent to the TERMUX_SERVICE
        Intent intent = new Intent(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executionCommand.executableUri);
        intent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        intent.putExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_RUNNER, executionCommand.runner);
        intent.putExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_BACKGROUND, true); // Also pass in case user using termux-app version < 0.119.0
        intent.putExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PENDING_INTENT, pi);

        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // https://developer.android.com/about/versions/oreo/background.html
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void sendKillIntent() {
        // Create execution intent with the action TERMUX_SERVICE#ACTION_SERVICE_EXECUTE to be sent to the TERMUX_SERVICE
        // needs to be replaced with TermuxConstants.ACTION_SERVICE_STOP
        Intent intent = new Intent("com.termux.service_execution_stop", executableUri);
        intent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        // needs to be replaced with TermuxConstants.EXTRA_TERMINATE_GRACE_PERIOD
        intent.putExtra("com.termux.execute.stop.delay", gracePeriod);

        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // https://developer.android.com/about/versions/oreo/background.html
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void scheduleNextExecution() {
        CronScheduler.scheduleAlarmForJob(getApplicationContext(), jobId);
    }
}
