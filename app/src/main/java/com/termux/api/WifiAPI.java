package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;

import java.util.List;

public class WifiAPI {

    static void onReceiveWifiConnectionInfo(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
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
                    out.name("ssid").value(info.getSSID().replaceAll("\\\"", ""));
                    out.name("ssid_hidden").value(info.getHiddenSSID());
                    out.name("supplicant_state").value(info.getSupplicantState().toString());
                }
                out.endObject();
            }
        });
    }

    static void onReceiveWifiScanInfo(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                List<ScanResult> scans = manager.getScanResults();
                if (scans == null) {
                    out.beginObject().name("API_ERROR").value("Failed getting scan results").endObject();
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

}
