package com.termux.api.apis;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;

public class BrightnessAPI {

    public static void onReceive(final TermuxApiReceiver receiver, final Context context, final Intent intent) {
        final ContentResolver contentResolver = context.getContentResolver();
        if (intent.hasExtra("auto")) {
            boolean auto = intent.getBooleanExtra("auto", false);
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, auto?Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC:Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        }

        int brightness = intent.getIntExtra("brightness", 0);

        if (brightness <= 0) {
            brightness = 0;
        } else if (brightness >= 255) {
            brightness = 255;
        }
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
        ResultReturner.noteDone(receiver, intent);
    }
}
