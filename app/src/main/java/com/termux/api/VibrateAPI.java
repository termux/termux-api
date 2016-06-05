package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Vibrator;

import com.termux.api.util.ResultReturner;

public class VibrateAPI {

    static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
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
