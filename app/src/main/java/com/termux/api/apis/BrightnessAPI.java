package com.termux.api.apis;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import java.io.PrintWriter;
import java.io.StringWriter;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

public class BrightnessAPI {

    private static final String LOG_TAG = "BrightnessAPI";
    static PrintWriter pw;
    static StringWriter sw;
    static Context context;
    static TermuxApiReceiver receiver;
    static Intent intent;

    static void write() {
        // ResultReturner.noteDone(receiver, intent);
        ResultReturner.returnData(context.getApplicationContext(), intent, new ResultReturner.WithInput() {
            @Override public void writeResult(PrintWriter _out) throws Exception {
            try {
                Logger.logInfo(LOG_TAG, "printing \""+sw+"\"");
                // out.value("\n");
                _out.print(sw);
            } catch (Exception e) {
                Logger.logError(LOG_TAG, e.toString());
                _out.print(e.toString());
            }
            }
        });
    }

    public static void onReceive(final TermuxApiReceiver _receiver, final Context _context, final Intent _intent) {
        context = _context;
        receiver = _receiver;
        intent = _intent;
        sw = new StringWriter();
        pw = new PrintWriter(sw);
        Logger.logDebug(LOG_TAG, "onReceive");

        try {
            work();
        } catch (Exception e) {
            Logger.logError(LOG_TAG, e.toString());
            pw.println(e.toString());
        }

        // pw.println("test");
        write();
    }

    static void work() throws Exception {
        final ContentResolver contentResolver = context.getContentResolver();

        if (intent.hasExtra("auto")) {
            boolean auto = intent.getBooleanExtra("auto", false);
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, auto?Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC:Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Logger.logInfo(LOG_TAG, " set brightness auto to "+auto);
            pw.println("setting brightness  auto to "+auto);
        }

        if (intent.hasExtra("brightness")) {
            int brightness = intent.getIntExtra("brightness", 0);
            if (brightness <= 0) {
                brightness = 0;
            } else if (brightness >= 255) {
                brightness = 255;
            }
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
            Logger.logInfo(LOG_TAG, " set brightness "+brightness);
            pw.println("setting brightness to "+brightness);
        } else {
            int b = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS);
            Logger.logInfo(LOG_TAG, "received brightness "+b);
            pw.println(""+b);
        }
    }
}
