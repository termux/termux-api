package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Vibrator;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

public class VibrateAPI {

    private static final String LOG_TAG = "VibrateAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        int milliseconds = intent.getIntExtra("duration_ms", 1000);
        boolean force = intent.getBooleanExtra("force", false);

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am.getRingerMode() == AudioManager.RINGER_MODE_SILENT && !force) {
            // Not vibrating since in silent mode and -f/--force option not used.
        } else {
            vibrator.vibrate(milliseconds);
        }

        ResultReturner.noteDone(apiReceiver, intent);
    }

}
