package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.PrintWriter;

public class ToastAPI {

    private static final String LOG_TAG = "ToastAPI";

    public static void onReceive(final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final int durationExtra = intent.getBooleanExtra("short", false) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        final int backgroundColor = getColorExtra(intent, "background", Color.GRAY);
        final int textColor = getColorExtra(intent, "text_color", Color.WHITE);
        final int gravity = getGravityExtra(intent);

        final Handler handler = new Handler();

        ResultReturner.returnData(context, intent, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
                handler.post(() -> {
                    Toast toast = Toast.makeText(context, inputString, durationExtra);
                    View toastView = toast.getView();

                    Drawable background = toastView.getBackground();
                    background.setTint(backgroundColor);

                    TextView textView = toastView.findViewById(android.R.id.message);
                    textView.setTextColor(textColor);

                    toast.setGravity(gravity, 0, 0);
                    toast.show();
                });
            }
        });
    }

    protected static int getColorExtra(Intent intent, String extra, int defaultColor) {
        int color = defaultColor;

        if (intent.hasExtra(extra)) {
            String colorExtra = intent.getStringExtra(extra);

            try {
                color = Color.parseColor(colorExtra);
            } catch (IllegalArgumentException e) {
                Logger.logError(LOG_TAG, String.format("Failed to parse color '%s' for '%s'", colorExtra, extra));
            }
        }
        return color;
    }

    protected static int getGravityExtra(Intent intent) {
        String extraGravity = intent.getStringExtra("gravity");

        switch (extraGravity == null ? "" : extraGravity) {
            case "top": return Gravity.TOP;
            case "middle": return Gravity.CENTER;
            case "bottom": return Gravity.BOTTOM;
            default: return Gravity.CENTER;
        }
    }

}
