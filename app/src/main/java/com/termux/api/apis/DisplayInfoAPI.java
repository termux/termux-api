package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.util.JsonWriter;
import android.view.Display;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

public class DisplayInfoAPI {
    private static final String LOG_TAG = "DisplayInfoAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);

                out.beginArray();
                for (Display display : displayManager.getDisplays()) {
                    out.beginObject();
                    out.name("Name").value(display.getName());
                    out.name("DisplayId").value(display.getDisplayId());
                    out.name("AppVsyncOffset").value(display.getAppVsyncOffsetNanos());
                    out.name("PresentationDeadline").value(display.getPresentationDeadlineNanos());
                    out.name("RefreshRate").value(display.getRefreshRate());
                    out.name("Rotation").value(display.getRotation());

                    out.name("SupportedModes");
                    out.beginArray();
                    for (Display.Mode mode : display.getSupportedModes()) {
                        out.beginObject();
                        out.name("ModeId").value(mode.getModeId());
                        out.name("PhysicalHeight").value(mode.getPhysicalHeight());
                        out.name("PhysicalWidth").value(mode.getPhysicalWidth());
                        out.name("RefreshRate").value(mode.getRefreshRate());
                        out.endObject();
                    }
                    out.endArray();

                    out.name("HdrCapabilities");
                    out.beginObject();
                    Display.HdrCapabilities hdrCapabilities = display.getHdrCapabilities();
                    out.name("DesiredMaxAverageLuminance").value(hdrCapabilities.getDesiredMaxAverageLuminance());
                    out.name("DesiredMaxLuminance").value(hdrCapabilities.getDesiredMaxLuminance());
                    out.name("DesiredMinLuminance").value(hdrCapabilities.getDesiredMinLuminance());
                    out.endObject();

                    out.name("DisplayMetrics");
                    out.beginObject();
                    DisplayMetrics displayMetrics = new DisplayMetrics();
                    display.getMetrics(displayMetrics);
                    out.name("Density").value(displayMetrics.density);
                    out.name("DensityDPI").value(displayMetrics.densityDpi);
                    out.name("HeightPixels").value(displayMetrics.heightPixels);
                    out.name("ScaledDensity").value(displayMetrics.scaledDensity);
                    out.name("WidthPixels").value(displayMetrics.widthPixels);
                    out.name("xDPI").value(displayMetrics.xdpi);
                    out.name("yDPI").value(displayMetrics.ydpi);
                    out.endObject();

                    out.endObject();
                }
                out.endArray();
            }
        });
    }
}
