package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Vibrator;

import com.termux.api.util.ResultReturner;

import org.json.JSONObject;

public class VibrateAPI {

    static void onReceive(Context context, JSONObject opts) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        int milliseconds = opts.optInt("duration_ms", 1000);
        boolean force = opts.optBoolean("force", false);

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am.getRingerMode() == AudioManager.RINGER_MODE_SILENT && !force) {
            // Not vibrating since in silent mode and -f/--force option not used.
        } else {
            vibrator.vibrate(milliseconds);
        }

        ResultReturner.noteDone(context);
    }

}
