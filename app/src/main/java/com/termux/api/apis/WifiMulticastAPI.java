package com.termux.api.apis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.JsonWriter;

import androidx.annotation.Nullable;

import com.termux.api.R;
import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.shared.logger.Logger;

/**
 * Holds an Android {@link WifiManager.MulticastLock} on behalf of the user, so
 * that other processes (e.g. mDNS/Bonjour/SSDP daemons running inside Termux)
 * can receive inbound multicast traffic.
 *
 * <p>Android drops all inbound multicast frames at the Wi-Fi firmware level
 * unless at least one process holds a multicast lock. Only an app process can
 * acquire one, so a plain shell process cannot do it on its own.
 *
 * <p>A lock is tied to the process that acquired it, therefore it has to be
 * held by a service living in this app. The lock is not reference counted: a
 * single {@code acquire()} keeps multicast reception enabled until either
 * {@code release()} is called or the process dies, and Android re-applies its
 * filter on process death. The service therefore runs in the foreground with a
 * {@code START_STICKY} return value so that the system restarts it after
 * having killed the app.
 */
public class WifiMulticastAPI {

    private static final String LOG_TAG = "WifiMulticastAPI";

    /**
     * Get or create the singleton lock holder for this process.
     *
     * <p>The lock is created lazily and never released implicitly. Callers that
     * want to drop the lock have to go through {@link #release(Context)}.
     */
    private static WifiManager.MulticastLock getOrCreateLock(Context context) {
        synchronized (WifiMulticastAPI.class) {
            if (sLock == null) {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                if (wifiManager == null) {
                    throw new IllegalStateException("Wi-Fi service is not available");
                }
                sLock = wifiManager.createMulticastLock(LOG_TAG);
                // A non reference counted lock is what the app really wants:
                // one acquire() enables multicast for the whole process and it
                // stays enabled until release(), no matter how many players
                // are involved.
                sLock.setReferenceCounted(false);
            }
            return sLock;
        }
    }

    /** The process wide lock, or {@code null} if it was never needed. */
    private static WifiManager.MulticastLock sLock;

    private static boolean isLockHeld() {
        synchronized (WifiMulticastAPI.class) {
            return sLock != null && sLock.isHeld();
        }
    }

    /** Acquire the lock, without starting any service. */
    private static void acquireLock(Context context) {
        synchronized (WifiMulticastAPI.class) {
            WifiManager.MulticastLock lock = getOrCreateLock(context);
            if (!lock.isHeld()) {
                lock.acquire();
                Logger.logInfo(LOG_TAG, "Multicast lock acquired");
            }
        }
    }

    /** Release the lock, if it is currently held. */
    private static void releaseLock() {
        synchronized (WifiMulticastAPI.class) {
            if (sLock != null && sLock.isHeld()) {
                sLock.release();
                Logger.logInfo(LOG_TAG, "Multicast lock released");
            }
            sLock = null;
        }
    }

    public static void onReceiveAcquire(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveAcquire");

        Context applicationContext = context.getApplicationContext();

        // Acquire in this process as well: the broadcast receiver runs in the
        // app process, and holding the lock here makes the request effective
        // even if the service cannot be started (e.g. background restrictions).
        try {
            acquireLock(applicationContext);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to acquire lock", e);
            ResultReturner.returnData(apiReceiver, intent, errorWriter(e.getMessage()));
            return;
        }

        // Also keep a foreground service alive so that the process is not
        // killed by the system while the lock is supposed to be held.
        Intent serviceIntent = new Intent(applicationContext, WifiMulticastService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent);
            } else {
                applicationContext.startService(serviceIntent);
            }
        } catch (Exception e) {
            // Starting a foreground service from the background may be
            // blocked depending on the OS version and on whether the app has
            // an exemption. A plain service still works in most cases and is
            // only less likely to survive low memory situations.
            Logger.logStackTraceWithMessage(LOG_TAG,
                    "Failed to start foreground lock service, falling back to a plain service", e);
            try {
                applicationContext.startService(serviceIntent);
            } catch (Exception fallbackError) {
                // Not fatal: the lock is already held by this process, it
                // will simply be dropped if the process gets killed.
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start lock service", fallbackError);
            }
        }

        ResultReturner.returnData(apiReceiver, intent, statusWriter(applicationContext, "acquired"));
    }

    public static void onReceiveRelease(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveRelease");

        Context applicationContext = context.getApplicationContext();

        try {
            applicationContext.stopService(new Intent(applicationContext, WifiMulticastService.class));
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to stop lock service", e);
        }
        releaseLock();

        ResultReturner.returnData(apiReceiver, intent, statusWriter(applicationContext, "released"));
    }

    public static void onReceiveStatus(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveStatus");

        ResultReturner.returnData(apiReceiver, intent, statusWriter(context.getApplicationContext(), null));
    }

    private static ResultJsonWriter statusWriter(Context context, @Nullable String requestedAction) {
        return new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                boolean held = isLockHeld();
                out.beginObject();
                out.name("acquired").value(held);
                if (requestedAction != null) {
                    out.name("action").value(requestedAction);
                }
                if (!held && "acquired".equals(requestedAction)) {
                    out.name("API_ERROR").value("Multicast lock could not be acquired");
                }
                out.endObject();
            }
        };
    }

    private static ResultJsonWriter errorWriter(@Nullable String message) {
        return new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("acquired").value(false);
                out.name("API_ERROR").value(message == null ? "unknown error" : message);
                out.endObject();
            }
        };
    }

    /**
     * Foreground service that owns the multicast lock and keeps the app
     * process alive for as long as the lock is supposed to be held.
     */
    public static class WifiMulticastService extends Service {

        private static final String LOG_TAG = "WifiMulticastService";
        private static final String CHANNEL_ID = "termux-api-multicast";
        private static final int NOTIFICATION_ID = 0x4D43; // "MC"

        @Override
        public void onCreate() {
            Logger.logDebug(LOG_TAG, "onCreate");
            super.onCreate();

            startForeground(NOTIFICATION_ID, buildNotification());
            acquireLock(this);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Logger.logDebug(LOG_TAG, "onStartCommand");

            // Make sure the lock survived a service restart without a new
            // process (onCreate() is not called in that case).
            acquireLock(this);

            // START_STICKY: the lock is dropped when the process dies, so the
            // system must bring the service (and therefore the lock) back.
            return Service.START_STICKY;
        }

        @Override
        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");
            releaseLock();
            super.onDestroy();
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        private Notification buildNotification() {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager manager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) {
                    NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                            "Multicast lock", NotificationManager.IMPORTANCE_MIN);
                    channel.setShowBadge(false);
                    manager.createNotificationChannel(channel);
                }
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            return builder
                    .setContentTitle("Multicast lock held")
                    .setContentText("Inbound multicast traffic is no longer filtered")
                    .setSmallIcon(R.drawable.ic_termux_prompt)
                    .setOngoing(true)
                    .build();
        }
    }
}
