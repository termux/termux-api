package com.termux.api;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.termux.api.util.ResultReturner;

import org.json.JSONObject;

public class BrightnessAPI {

    public static void onReceive(final Context context, JSONObject opts) {
        final ContentResolver contentResolver = context.getContentResolver();

        int brightness = opts.optInt("brightness", 0);

        if (brightness <= 0) {
            brightness = 0;
        } else if (brightness >= 255) {
            brightness = 255;
        }

        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
        ResultReturner.noteDone(context);
    }
}