package com.termux.api.apis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.JsonWriter;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.util.List;

public class WifiAPI {

    private static final String LOG_TAG = "WifiAPI";

    public static void onReceiveWifiConnectionInfo(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveWifiConnectionInfo");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
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

    public static void onReceiveWifiScanInfo(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveWifiScanInfo");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
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
                        if (!TextUtils.isEmpty(scan.capabilities)) {
                            out.name("capabilities").value(scan.capabilities);
                        }
                        if (!TextUtils.isEmpty(scan.operatorFriendlyName)) {
                            out.name("operator_name").value(scan.operatorFriendlyName.toString());
                        }
                        if (!TextUtils.isEmpty(scan.venueName)) {
                            out.name("venue_name").value(scan.venueName.toString());
                        }
                        out.endObject();
                    }
                    out.endArray();
                }
            }
        });
    }

    public static void onReceiveWifiEnable(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveWifiEnable");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) {
                WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                boolean state = intent.getBooleanExtra("enabled", false);
                manager.setWifiEnabled(state);
            }
        });
    }

    public static void onReceiveWifiConnect(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveWifiConnect");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                String ssid = intent.getStringExtra("ssid");
                String password = intent.getStringExtra("password");

                out.beginObject();

                if (TextUtils.isEmpty(ssid)) {
                    out.name("API_ERROR").value("SSID is required");
                    out.endObject();
                    return;
                }

                try {
                    WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                    // Remove quotes if present
                    String cleanSSID = ssid.replaceAll("^\"|\"$", "");
                    String cleanPassword = (password != null) ? password : "";

                    // Create WifiConfiguration
                    android.net.wifi.WifiConfiguration conf = new android.net.wifi.WifiConfiguration();
                    conf.SSID = "\"" + cleanSSID + "\"";
                    conf.status = android.net.wifi.WifiConfiguration.Status.DISABLED;

                    // Configure based on security
                    if (TextUtils.isEmpty(cleanPassword)) {
                        // Open network (no security)
                        conf.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE);
                    } else {
                        // WPA2/WPA network
                        conf.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK);
                        conf.preSharedKey = "\"" + cleanPassword + "\"";
                    }

                    // Disable all auth algorithms
                    conf.allowedAuthAlgorithms.clear();
                    conf.allowedAuthAlgorithms.set(android.net.wifi.WifiConfiguration.AuthAlgorithm.OPEN);

                    // Set protocols
                    conf.allowedProtocols.set(android.net.wifi.WifiConfiguration.Protocol.RSN);
                    conf.allowedProtocols.set(android.net.wifi.WifiConfiguration.Protocol.WPA);

                    // Set pairwise cipher
                    conf.allowedPairwiseCiphers.set(android.net.wifi.WifiConfiguration.PairwiseCipher.CCMP);
                    conf.allowedPairwiseCiphers.set(android.net.wifi.WifiConfiguration.PairwiseCipher.TKIP);

                    // Set group cipher
                    conf.allowedGroupCiphers.set(android.net.wifi.WifiConfiguration.GroupCipher.CCMP);
                    conf.allowedGroupCiphers.set(android.net.wifi.WifiConfiguration.GroupCipher.TKIP);

                    // Add or update the network
                    int networkId = manager.addNetwork(conf);

                    if (networkId == -1) {
                        // Try to update if network exists
                        networkId = findNetworkId(manager, cleanSSID);
                        if (networkId == -1) {
                            out.name("API_ERROR").value("Failed to add WiFi network");
                            out.endObject();
                            return;
                        }
                        manager.updateNetwork(conf);
                    }

                    // Connect to the network
                    boolean enableResult = manager.enableNetwork(networkId, true);

                    if (enableResult) {
                        out.name("status").value("success");
                        out.name("message").value("Connected to WiFi: " + cleanSSID);
                        out.name("network_id").value(networkId);
                    } else {
                        out.name("API_ERROR").value("Failed to enable network");
                    }

                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Error connecting to WiFi", e);
                    out.name("API_ERROR").value("Exception: " + e.getMessage());
                }

                out.endObject();
            }
        });
    }

    private static int findNetworkId(WifiManager manager, String ssid) {
        android.net.wifi.WifiConfiguration[] configs = manager.getConfiguredNetworks();
        if (configs != null) {
            for (android.net.wifi.WifiConfiguration config : configs) {
                if (config.SSID.equals("\"" + ssid + "\"")) {
                    return config.networkId;
                }
            }
        }
        return -1;
    }

}
