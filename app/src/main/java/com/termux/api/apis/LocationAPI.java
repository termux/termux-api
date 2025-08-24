package com.termux.api.apis;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.JsonWriter;

import androidx.annotation.RequiresPermission;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.shared.logger.Logger;

import java.io.IOException;

public class LocationAPI {

    private static final String LOG_TAG = "LocationAPI";

    private static final String REQUEST_LAST_KNOWN = "last";
    private static final String REQUEST_ONCE = "once";
    private static final String REQUEST_UPDATES = "updates";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            @Override
            public void writeJson(final JsonWriter out) throws Exception {
                LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

                String provider = intent.getStringExtra("provider");
                if (provider == null)
                    provider = LocationManager.GPS_PROVIDER;
                if (!(provider.equals(LocationManager.GPS_PROVIDER) || provider.equals(LocationManager.NETWORK_PROVIDER) || provider
                        .equals(LocationManager.PASSIVE_PROVIDER))) {
                    out.beginObject()
                            .name("API_ERROR")
                            .value("Unsupported provider '" + provider + "' - only '" + LocationManager.GPS_PROVIDER + "', '"
                                    + LocationManager.NETWORK_PROVIDER + "' and '" + LocationManager.PASSIVE_PROVIDER + "' supported").endObject();
                    return;
                }

                String request = intent.getStringExtra("request");
                if (request == null)
                    request = REQUEST_ONCE;
                switch (request) {
                    case REQUEST_LAST_KNOWN:
                        Location lastKnownLocation = manager.getLastKnownLocation(provider);
                        locationToJson(lastKnownLocation, out);
                        break;
                    case REQUEST_ONCE:
                        Looper.prepare();
                        manager.requestSingleUpdate(provider, new LocationListener() {

                            @Override
                            public void onStatusChanged(String changedProvider, int status, Bundle extras) {
                                // TODO Auto-generated method stub
                            }

                            @Override
                            public void onProviderEnabled(String changedProvider) {
                                // TODO Auto-generated method stub
                            }

                            @Override
                            public void onProviderDisabled(String changedProvider) {
                                // TODO Auto-generated method stub
                            }

                            @Override
                            public void onLocationChanged(Location location) {
                                try {
                                    locationToJson(location, out);
                                } catch (IOException e) {
                                    Logger.logStackTraceWithMessage(LOG_TAG, "Writing json", e);
                                } finally {
                                    Looper.myLooper().quit();
                                }
                            }
                        }, null);
                        Looper.loop();
                        break;
                    case REQUEST_UPDATES:
                        Looper.prepare();
                        manager.requestLocationUpdates(provider, 5000, 50.f, new LocationListener() {

                            @Override
                            public void onStatusChanged(String changedProvider, int status, Bundle extras) {
                                // Do nothing.
                            }

                            @Override
                            public void onProviderEnabled(String changedProvider) {
                                // Do nothing.
                            }

                            @Override
                            public void onProviderDisabled(String changedProvider) {
                                // Do nothing.
                            }

                            @Override
                            public void onLocationChanged(Location location) {
                                try {
                                    locationToJson(location, out);
                                    out.flush();
                                } catch (IOException e) {
                                    Logger.logStackTraceWithMessage(LOG_TAG, "Writing json", e);
                                }
                            }
                        }, null);
                        final Looper looper = Looper.myLooper();
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(30 * 1000);
                                } catch (InterruptedException e) {
                                    Logger.logStackTraceWithMessage(LOG_TAG, "INTER", e);
                                }
                                looper.quit();
                            }
                        }.start();
                        Looper.loop();
                        break;
                    default:
                        out.beginObject()
                                .name("API_ERROR")
                                .value("Unsupported request '" + request + "' - only '" + REQUEST_LAST_KNOWN + "', '" + REQUEST_ONCE + "' and '" + REQUEST_UPDATES
                                        + "' supported").endObject();
                }
            }
        });
    }

    static void locationToJson(Location lastKnownLocation, JsonWriter out) throws IOException {
        if (lastKnownLocation == null) {
            out.beginObject().name("API_ERROR").value("Failed to get location").endObject();
            return;
        }
        out.beginObject();
        out.name("latitude").value(lastKnownLocation.getLatitude());
        out.name("longitude").value(lastKnownLocation.getLongitude());
        out.name("altitude").value(lastKnownLocation.getAltitude());
        out.name("accuracy").value(lastKnownLocation.getAccuracy());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            out.name("vertical_accuracy").value(lastKnownLocation.getVerticalAccuracyMeters());
        }
        out.name("bearing").value(lastKnownLocation.getBearing());
        out.name("speed").value(lastKnownLocation.getSpeed());
        long elapsedMs = (SystemClock.elapsedRealtimeNanos() - lastKnownLocation.getElapsedRealtimeNanos()) / 1000000;
        out.name("elapsedMs").value(elapsedMs);
        out.name("provider").value(lastKnownLocation.getProvider());
        boolean isMocked = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? lastKnownLocation.isMock() : lastKnownLocation.isFromMockProvider());
        out.name("mocked").value(isMocked);
        out.endObject();
    }
}
