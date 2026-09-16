package com.termux.api.apis;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.JsonWriter;

import androidx.annotation.RequiresPermission;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LocationAPI {

    private static final String LOG_TAG = "LocationAPI";

    private static final String REQUEST_LAST_KNOWN = "last";
    private static final String REQUEST_ONCE = "once";
    private static final String REQUEST_UPDATES = "updates";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        Intent serviceIntent = new Intent(context, LocationService.class);
        serviceIntent.setAction(intent.getAction());
        Bundle extras = intent.getExtras();
        if (extras != null)
            serviceIntent.putExtras(extras);
        context.startService(serviceIntent);
    }


    public static class LocationService extends Service {

        protected static final String LOG_TAG = "LocationService";

        public LocationService() {
            super();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        public void onCreate() {
            Logger.logDebug(LOG_TAG, "onCreate");

            super.onCreate();
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Logger.logDebug(LOG_TAG, "onStartCommand");

            ResultReturner.returnData(this, intent, new ResultJsonWriter() {
            @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            @Override
            public void writeJson(final JsonWriter out) throws Exception {
                LocationManager manager = (LocationManager) LocationService.this.getSystemService(Context.LOCATION_SERVICE);

                // If on Android `>= 11` and background location permission is missing, ask user to grant it.
                // - https://developer.android.com/reference/android/Manifest.permission#ACCESS_BACKGROUND_LOCATION
                // - https://developer.android.com/develop/sensors-and-location/location/permissions/background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !PermissionUtils.checkPermission(LocationService.this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    out.beginObject()
                        .name("API_ERROR")
                        .value("Background location permission not granted." +
                                " Grant it manually from Android Settings -> Apps -> " + TermuxConstants.TERMUX_API_APP_NAME + " -> Permissions -> Location -> Allow all the time").endObject();
                    return;
                }

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

                String requestParam = intent.getStringExtra("request");
                if (requestParam == null)
                    requestParam = REQUEST_ONCE;

                final String request = requestParam;
                switch (request) {
                    case REQUEST_LAST_KNOWN:
                        Location lastKnownLocation = manager.getLastKnownLocation(provider);
                        locationToJson(lastKnownLocation, out);
                        break;
                    case REQUEST_ONCE:
                    case REQUEST_UPDATES:
                        LocationMonitorStore locationMonitorStore = new LocationMonitorStore();

                        CountDownLatch latch = new CountDownLatch(1);

                        LocationListener locationListener = new LocationListener() {
                            @Override
                            public void onStatusChanged(String changedProvider, int status, Bundle extras) {}

                            @Override
                            public void onProviderEnabled(String changedProvider) {}

                            @Override
                            public void onProviderDisabled(String changedProvider) {}

                            @Override
                            public void onLocationChanged(Location location) {
                                try {
                                    synchronized (out) {
                                        if (REQUEST_UPDATES.equals(request) && !locationMonitorStore.responseReceived) {
                                            locationMonitorStore.responseReceived = true;
                                            out.beginArray();
                                        }

                                        locationToJson(location, out);
                                        out.flush();
                                    }
                                } catch (Exception e) {
                                    Logger.logStackTraceWithMessage(LOG_TAG, "Writing json", e);
                                } finally {
                                    if (REQUEST_ONCE.equals(request)) {
                                        // End wait.
                                        latch.countDown();
                                    }
                                }
                            }
                        };
                        locationMonitorStore.locationListener = locationListener;

                        HandlerThread locationThread = new HandlerThread("BackgroundLocationThread");
                        locationThread.start();

                        try {
                            if (REQUEST_ONCE.equals(request)) {
                                manager.requestSingleUpdate(provider, locationListener, locationThread.getLooper());
                            } else {
                                manager.requestLocationUpdates(provider, 5000, 1.f, locationListener, locationThread.getLooper());
                            }
                        } catch (Throwable t) {
                            out.beginObject().name("API_ERROR").value("Failed to request location:\n" + Logger.getStackTraceString(t)).endObject();
                            return;
                        }

                        try {
                            // Wait up to 30 seconds for `updates` request, or if `once` request
                            // received a response and counted down from initial 1 to 0.
                            latch.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                        } finally {
                            synchronized (out) {
                                if (REQUEST_UPDATES.equals(request) && locationMonitorStore.responseReceived) {
                                    try {
                                        out.endArray();
                                    } catch (IOException e) {
                                        Logger.logStackTraceWithMessage(LOG_TAG, "Failed to end output location updates array", e);
                                    }
                                }

                                cleanUpLocationUpdates(manager, locationMonitorStore);
                            }

                            try {
                                locationThread.quitSafely();
                            } catch (Throwable t) {
                                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to quit background location thread", t);
                            }
                        }

                        break;
                    default:
                        out.beginObject()
                            .name("API_ERROR")
                            .value("Unsupported request '" + request + "' - only '" + REQUEST_LAST_KNOWN + "', '" + REQUEST_ONCE + "' and '" + REQUEST_UPDATES
                                    + "' supported").endObject();
                }
            }
        });

            return Service.START_NOT_STICKY;
        }

        public class LocationMonitorStore {
            public LocationListener locationListener;
            public boolean responseReceived;
        }

        protected void cleanUpLocationUpdates(LocationManager manager, LocationMonitorStore locationMonitorStore) {
            synchronized (locationMonitorStore) {
                try {
                    if (locationMonitorStore.locationListener != null) {
                        manager.removeUpdates(locationMonitorStore.locationListener);
                    }
                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to remove location updates", e);
                }
                locationMonitorStore.locationListener = null;
            }
        }

        @Override
        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            super.onDestroy();
        }

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
        out.endObject();
    }

}
