package com.termux.api.apis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.JsonWriter;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.shared.logger.Logger;

public class BatteryStatusAPI {

    private static final String LOG_TAG = "BatteryStatusAPI";

    private static int sTargetSdkVersion;

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        sTargetSdkVersion = context.getApplicationContext().getApplicationInfo().targetSdkVersion;

        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @SuppressLint("DefaultLocale")
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                // - https://cs.android.com/android/platform/superproject/+/android-15.0.0_r1:frameworks/base/services/core/java/com/android/server/BatteryService.java;l=745
                Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

                int batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int batteryScale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                String batteryHealth;
                switch (health) {
                    case BatteryManager.BATTERY_HEALTH_COLD:
                        batteryHealth = "COLD";
                        break;
                    case BatteryManager.BATTERY_HEALTH_DEAD:
                        batteryHealth = "DEAD";
                        break;
                    case BatteryManager.BATTERY_HEALTH_GOOD:
                        batteryHealth = "GOOD";
                        break;
                    case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                        batteryHealth = "OVERHEAT";
                        break;
                    case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                        batteryHealth = "OVER_VOLTAGE";
                        break;
                    case BatteryManager.BATTERY_HEALTH_UNKNOWN:
                        batteryHealth = "UNKNOWN";
                        break;
                    case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                        batteryHealth = "UNSPECIFIED_FAILURE";
                        break;
                    default:
                        batteryHealth = Integer.toString(health);
                }

                // BatteryManager.EXTRA_PLUGGED: "Extra for ACTION_BATTERY_CHANGED: integer indicating whether the
                // device is plugged in to a power source; 0 means it is on battery, other constants are different types
                // of power sources."
                int pluggedInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                String batteryPlugged;
                switch (pluggedInt) {
                    case 0:
                        batteryPlugged = "UNPLUGGED";
                        break;
                    case BatteryManager.BATTERY_PLUGGED_AC:
                        batteryPlugged = "PLUGGED_AC";
                        break;
                    case BatteryManager.BATTERY_PLUGGED_DOCK:
                        batteryPlugged = "PLUGGED_DOCK";
                        break;
                    case BatteryManager.BATTERY_PLUGGED_USB:
                        batteryPlugged = "PLUGGED_USB";
                        break;
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        batteryPlugged = "PLUGGED_WIRELESS";
                        break;
                    default:
                        batteryPlugged = "PLUGGED_" + pluggedInt;
                }

                double batteryTemperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.f;

                String batteryStatusString;
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                switch (status) {
                    case BatteryManager.BATTERY_STATUS_CHARGING:
                        batteryStatusString = "CHARGING";
                        break;
                    case BatteryManager.BATTERY_STATUS_DISCHARGING:
                        batteryStatusString = "DISCHARGING";
                        break;
                    case BatteryManager.BATTERY_STATUS_FULL:
                        batteryStatusString = "FULL";
                        break;
                    case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                        batteryStatusString = "NOT_CHARGING";
                        break;
                    case BatteryManager.BATTERY_STATUS_UNKNOWN:
                        batteryStatusString = "UNKNOWN";
                        break;
                    default:
                        Logger.logError(LOG_TAG, "Invalid BatteryManager.EXTRA_STATUS value: " + status);
                        batteryStatusString = "UNKNOWN";
                }

                // - https://stackoverflow.com/questions/24500795/android-battery-voltage-unit-discrepancies
                int batteryVoltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                // If in V, convert to mV.
                if (batteryVoltage < 100) {
                    Logger.logVerbose(LOG_TAG, "Fixing voltage from " + batteryVoltage + " to " + (batteryVoltage * 1000));
                    batteryVoltage = batteryVoltage * 1000;
                }

                BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

                // > Instantaneous battery current in microamperes, as an integer.
                // > Positive values indicate net current entering the battery from a charge source,
                // > negative values indicate net current discharging from the battery.
                // However, some devices may return negative values while charging, and positive
                // values while discharging. Inverting sign based on charging state is not a
                // possibility as charging current may be lower than current being used by device if
                // charger does not output enough current, and will result in false inversions.
                // - https://developer.android.com/reference/android/os/BatteryManager#BATTERY_PROPERTY_CURRENT_NOW
                // - https://issuetracker.google.com/issues/37131318
                int batteryCurrentNow = getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);

                // - https://stackoverflow.com/questions/64532112/batterymanagers-battery-property-current-now-returning-0-or-incorrect-current-v
                if (Math.abs(batteryCurrentNow / 1000) < 1.0) {
                    Logger.logVerbose(LOG_TAG, "Fixing current_now from " + batteryCurrentNow + " to " + (batteryCurrentNow * 1000));
                    batteryCurrentNow = batteryCurrentNow * 1000;
                }

                out.beginObject();
                out.name("present").value(batteryStatus.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false));
                out.name("technology").value(batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
                out.name("health").value(batteryHealth);
                out.name("plugged").value(batteryPlugged);
                out.name("status").value(batteryStatusString);
                out.name("temperature").value(String.format("%.1f", batteryTemperature));
                out.name("voltage").value(batteryVoltage);
                out.name("current").value(batteryCurrentNow);
                out.name("current_average").value(getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE));
                out.name("percentage").value(getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CAPACITY));
                out.name("level").value(batteryLevel);
                out.name("scale").value(batteryScale);
                out.name("charge_counter").value(getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
                out.name("energy").value(getLongProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    int batteryCycle = batteryStatus.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
                    out.name("cycle").value(batteryCycle != -1 ? batteryCycle : null);
                }
                out.endObject();
            }
        });
    }

    /**
     * - https://developer.android.com/reference/android/os/BatteryManager.html#getIntProperty(int)
     */
    private static Integer getIntProperty(BatteryManager batteryManager, int id) {
        if (batteryManager == null) return null;
        int value = batteryManager.getIntProperty(id);
        if (sTargetSdkVersion < Build.VERSION_CODES.P)
            return value != 0 ? value : null;
        else
            return value != Integer.MIN_VALUE ? value : null;
    }

    /**
     * - https://developer.android.com/reference/android/os/BatteryManager.html#getLongProperty(int)
     */
    private static Long getLongProperty(BatteryManager batteryManager, int id) {
        if (batteryManager == null) return null;
        long value = batteryManager.getLongProperty(id);
        return value != Long.MIN_VALUE ? value : null;
    }
}
