package com.termux.api;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

public class SchedulerJobService extends JobService {

    private static final String LOG_TAG = "TermuxAPISchedulerJob";
    public static final String SCRIPT_FILE_PATH = "com.termux.api.jobscheduler_script_path";

    // Constants from TermuxService.
    private static final String TERMUX_SERVICE = "com.termux.app.TermuxService";
    private static final String ACTION_EXECUTE = "com.termux.service_execute";
    private static final String EXTRA_EXECUTE_IN_BACKGROUND = "com.termux.execute.background";

    @Override
    public boolean onStartJob(JobParameters params) {

        Log.i(LOG_TAG, "Starting job " + params.toString());
        PersistableBundle extras = params.getExtras();
        String filePath = extras.getString(SCRIPT_FILE_PATH);

        Uri scriptUri = new Uri.Builder().scheme("com.termux.file").path(filePath).build();
        Intent executeIntent = new Intent(ACTION_EXECUTE, scriptUri);
        executeIntent.setClassName("com.termux", TERMUX_SERVICE);
        executeIntent.putExtra(EXTRA_EXECUTE_IN_BACKGROUND, true);

        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // https://developer.android.com/about/versions/oreo/background.html
            context.startForegroundService(executeIntent);
        } else {
            context.startService(executeIntent);
        }

        Log.i(LOG_TAG, "Started job " + params.toString());
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(LOG_TAG, "Stopped job " + params.toString());
        return false;
    }
}
