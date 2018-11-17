package com.termux.api;

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
import com.termux.api.util.TermuxApiLogger;

import org.json.JSONObject;

import java.io.PrintWriter;

public class ToastAPI {

    public static void onReceive(final Context context, JSONObject opts) {
        final int durationExtra = opts.optBoolean("short", false) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        final int backgroundColor = getColor(opts, "background", Color.GRAY);
        final int textColor = getColor(opts, "text_color", Color.WHITE);
        final int gravity = getGravity(opts);

        final Handler handler = new Handler();

        ResultReturner.returnData(context, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast toast = Toast.makeText(context, inputString, durationExtra);
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
