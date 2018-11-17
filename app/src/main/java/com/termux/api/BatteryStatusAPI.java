package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.api.util.TermuxApiLogger;

public class BatteryStatusAPI {

    public static void onReceive(final Context context) {
        ResultReturner.returnData(context, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                final int batteryPercentage = (level * 100) / scale;

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
                        TermuxApiLogger.error("Invalid BatteryManager.EXTRA_STATUS value: " + status);
                        batteryStatusString = "UNKNOWN";
                }

                out.beginObject();
                out.name("health").value(batteryHealth);
                out.name("percentage").value(batteryPercentage);
                out.name("plugged").value(batteryPlugged);
                out.name("status").value(batteryStatusString);
                out.name("temperature").value(batteryTemperature);
                out.endObject();
            }
        });

    }
}
