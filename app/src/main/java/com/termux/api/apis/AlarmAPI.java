package com.termux.api.apis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;

public class AlarmAPI {

    private static final String LOG_TAG = "AlarmAPI";
    private static TermuxApiReceiver current_receiver;
    private static Intent original_intent;
    private static int wl_duration;

    static public class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(wl_duration!=0) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "termux_api:Wakelock");
                wl.acquire(wl_duration);
            }

            final String command = Objects.toString(intent.getStringExtra("command"));
            Log.i(LOG_TAG, "Command is: " + command);

            try {

                Process process = Runtime.getRuntime().exec(command);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            //wl.release();
        }

    }

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        current_receiver=apiReceiver;
        original_intent=intent;
        int seconds=Integer.parseInt(Objects.toString(intent.getStringExtra("seconds"), "0"));
        wl_duration=Integer.parseInt(Objects.toString(intent.getStringExtra("wl_duration"), "0"));
        final String command = Objects.toString(intent.getStringExtra("command"));

        Intent alarm_intent = new Intent(context, AlarmReceiver.class);
        alarm_intent.putExtra("command", command);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarm_intent, 0);

        long nextTrigger = System.currentTimeMillis() +seconds*1000;
        Log.i(LOG_TAG, System.currentTimeMillis()  + ": scheduling next alarm at " + nextTrigger);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        AlarmManager.AlarmClockInfo ac = new AlarmManager.AlarmClockInfo(nextTrigger, null);
        alarmManager.setAlarmClock(ac, pendingIntent);


        ResultReturner.noteDone(current_receiver, original_intent);

    }


}
