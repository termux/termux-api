package com.termux.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;

import org.json.JSONObject;

import java.util.List;

public class WifiAPI {

    static void onReceiveWifiConnectionInfo(final Context context) {
        ResultReturner.returnData(context, new ResultReturner.ResultJsonWriter() {
            @SuppressLint("HardwareIds")
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = manager.getConnectionInfo();
                out.beginObject();
                if (info == null) {
                    out.name("API_ERROR").value("No current connection");
                } else {
                    out.name("bssid").value(info.getBSSID());
                    out.name("frequency_mhz").value(info.getFrequency());
                    //noinspection deprecation - formatIpAddress is deprecated, but we only have a ipv4 address here:
                    out.name("ip").value(Formatter.formatIpAddress(info.getIpAddress()));
                    out.name("link_speed_mbps").value(info.getLinkSpeed());
                    out.name("mac_address").value(info.getMacAddress());
                    out.name("network_id").value(info.getNetworkId());
                    out.name("rssi").value(info.getRssi());
                    out.name("ssid").value(info.getSSID().replaceAll("\"", ""));
                    out.name("ssid_hidden").value(info.getHiddenSSID());
                    out.name("supplicant_state").value(info.getSupplicantState().toString());
                }
                out.endObject();
            }
        });
    }

    static boolean isLocationEnabled(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    static void onReceiveWifiScanInfo(final Context context) {
        ResultReturner.returnData(context, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                List<ScanResult> scans = manager.getScanResults();
                if (scans == null) {
                    out.beginObject().name("API_ERROR").value("Failed getting scan results").endObject();
                } else if (scans.isEmpty() && !isLocationEnabled(context)) {
                    // https://issuetracker.google.com/issues/37060483:
                    // "WifiManager#getScanResults() returns an empty array list if GPS is turned off"
                    String errorMessage = "Location needs to be enabled on the device";
                    out.beginObject().name("API_ERROR").value(errorMessage).endObject();
                } else {
                    out.beginArray();
                    for (ScanResult scan : scans) {
                        out.beginObject();
                        out.name("bssid").value(scan.BSSID);
                        out.name("frequency_mhz").value(scan.frequency);
                        out.name("rssi").value(scan.level);
                        out.name("ssid").value(scan.SSID);
                        out.name("timestamp").value(scan.timestamp);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            int channelWidth = scan.channelWidth;
                            String channelWidthMhz = "???";
                            switch (channelWidth) {
                                case ScanResult.CHANNEL_WIDTH_20MHZ:
                                    channelWidthMhz = "20";
                                    break;
                                case ScanResult.CHANNEL_WIDTH_40MHZ:
                                    channelWidthMhz = "40";
                                    break;
                                case ScanResult.CHANNEL_WIDTH_80MHZ:
                                    channelWidthMhz = "80";
                                    break;
                                case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                                    channelWidthMhz = "80+80";
                                    break;
                                case ScanResult.CHANNEL_WIDTH_160MHZ:
                                    channelWidthMhz = "160";
                                    break;
                            }
                            out.name("channel_bandwidth_mhz").value(channelWidthMhz);
                            if (channelWidth != ScanResult.CHANNEL_WIDTH_20MHZ) {
                                // centerFreq0 says "Not used if the AP bandwidth is 20 MHz".
                                out.name("center_frequency_mhz").value(scan.centerFreq0);
                            }
                            if (!TextUtils.isEmpty(scan.operatorFriendlyName)) {
                                out.name("operator_name").value(scan.operatorFriendlyName.toString());
                            }
                            if (!TextUtils.isEmpty(scan.venueName)) {
                                out.name("venue_name").value(scan.venueName.toString());
                            }
                        }
                        out.endObject();
                    }
                    out.endArray();
                }
            }
        });
    }

    static void onReceiveWifiEnable(final Context context, final JSONObject opts) {
        ResultReturner.returnData(context, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) {
                WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                boolean state = opts.optBoolean("enabled", false);
                manager.setWifiEnabled(state);
            }
        });
    }

}
