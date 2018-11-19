package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import org.json.JSONObject;

import java.io.PrintWriter;

public class ToastAPI extends AppCompatActivity {
    public static void onReceive(final Context context, JSONObject opts) {
        final int durationExtra = opts.optBoolean("short", false) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        final int backgroundColor = getColor(opts, "background", Color.GRAY);
        final int textColor = getColor(opts, "text_color", Color.WHITE);
        final int gravity = getGravity(opts);
        final String text = opts.optString("text");

        makeToast(context, durationExtra, backgroundColor, textColor, gravity, text);
        ResultReturner.noteDone(context);
    }

    static void makeText(final Context context, final String text, final int durationExtra) {
        final int backgroundColor = Color.GRAY;
        final int textColor = Color.WHITE;
        final int gravity = Gravity.BOTTOM;
        makeToast(context, durationExtra, backgroundColor, textColor, gravity, text);
    }

        static void makeToast(final Context context, final int durationExtra, final int backgroundColor, final int textColor, final int gravity, final String text) {
        final Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(context, text, durationExtra);
                View toastView = toast.getView();

                Drawable background = toastView.getBackground();
                background.setTint(backgroundColor);

                TextView textView = toastView.findViewById(android.R.id.message);
                textView.setTextColor(textColor);

                toast.setGravity(gravity, 0, 0);
                toast.show();
            }
        });

    }

    protected static int getColor(JSONObject opts, String extra, int defaultColor) {
        int color = defaultColor;

        if (!opts.isNull(extra)) {
            String colorExtra = opts.optString(extra);

            try {
                color = Color.parseColor(colorExtra);
            } catch (IllegalArgumentException e) {
                TermuxApiLogger.error(String.format("Failed to parse color '%s' for '%s'", colorExtra, extra));
            }
        }
        return color;
    }

    protected static int getGravity(JSONObject opts) {
        String extraGravity = opts.optString("gravity");

        switch (extraGravity == null ? "" : extraGravity) {
            case "top": return Gravity.TOP;
            case "middle": return Gravity.CENTER;
            case "bottom": return Gravity.BOTTOM;
            default: return Gravity.CENTER;
        }
    }


}
