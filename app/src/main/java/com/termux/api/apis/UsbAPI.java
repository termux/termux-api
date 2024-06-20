package com.termux.api.apis;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.JsonWriter;
import android.util.SparseArray;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;

public class UsbAPI {

    protected static final String LOG_TAG = "UsbAPI";

    protected static SparseArray<UsbDeviceConnection> openDevices = new SparseArray<>();

    protected static final String ACTION_USB_PERMISSION = TermuxConstants.TERMUX_API_PACKAGE_NAME + ".USB_PERMISSION";

    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        Intent serviceIntent = new Intent(context, UsbService.class);
        serviceIntent.setAction(intent.getAction());
        Bundle extras = intent.getExtras();
        if (extras != null)
            serviceIntent.putExtras(extras);
        context.startService(serviceIntent);
    }

    public static class UsbService extends Service {

        protected static final String LOG_TAG = "UsbService";

        private final ThreadPoolExecutor mThreadPoolExecutor;

        public UsbService() {
            super();
            mThreadPoolExecutor = new ThreadPoolExecutor(1, 1,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>());
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

            String action = intent.getAction();
            if (action == null) {
                Logger.logError(LOG_TAG, "No action passed");
                ResultReturner.returnData(this, intent, out -> out.append("Missing action\n"));
            }

            if (action != null) {
                switch (action) {
                    case "list":
                        runListAction(intent);
                        break;
                    case "permission":
                        runPermissionAction(intent);
                        break;
                    case "open":
                        runOpenAction(intent);
                        break;
                    default:
                        Logger.logError(LOG_TAG, "Invalid action: \"" + action + "\"");
                        ResultReturner.returnData(this, intent, out -> out.append("Invalid action: \"" + action + "\"\n"));
                }
            }

            return Service.START_NOT_STICKY;
        }

        @Override
        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            super.onDestroy();
        }



        protected void runListAction(Intent intent) {
            Logger.logVerbose(LOG_TAG,"Running 'list' usb devices action");

            ResultReturner.returnData(this, intent, new ResultReturner.ResultJsonWriter() {
                @Override
                public void writeJson(JsonWriter out) throws Exception {
                    listDevices(out);
                }
            });
        }

        protected void listDevices(JsonWriter out) throws IOException {
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            out.beginArray();
            for (String deviceName : deviceList.keySet()) {
                out.value(deviceName);
            }
            out.endArray();
        }



        protected void runPermissionAction(Intent intent) {
            mThreadPoolExecutor.submit(() -> {
                String deviceName = intent.getStringExtra("device");

                Logger.logVerbose(LOG_TAG,"Running 'permission' action for device \"" + deviceName + "\"");

                UsbDevice device = getDevice(intent, deviceName);
                if (device == null) return;

                int status = checkAndRequestUsbDevicePermission(intent, device);
                ResultReturner.returnData(this, intent, out -> {
                    if (status == 0) {
                        Logger.logVerbose(LOG_TAG, "Permission granted for device \"" + device.getDeviceName() + "\"");
                        out.append("Permission granted.\n" );
                    } else if (status == 1) {
                        Logger.logVerbose(LOG_TAG, "Permission denied for device \"" + device.getDeviceName() + "\"");
                        out.append("Permission denied.\n" );
                    } else if (status == -1) {
                        out.append("Permission request timeout.\n" );
                    }
                });
            });
        }



        protected void runOpenAction(Intent intent) {
            mThreadPoolExecutor.submit(() -> {
                String deviceName = intent.getStringExtra("device");

                Logger.logVerbose(LOG_TAG,"Running 'open' action for device \"" + deviceName + "\"");

                UsbDevice device = getDevice(intent, deviceName);
                if (device == null) return;

                int status = checkAndRequestUsbDevicePermission(intent, device);
                ResultReturner.returnData(this, intent, new ResultReturner.WithAncillaryFd() {
                    @Override
                    public void writeResult(PrintWriter out) {
                        if (status == 0) {
                            int fd = open(device);
                            if (fd < 0) {
                                Logger.logVerbose(LOG_TAG, "Failed to open device \"" + device.getDeviceName() + "\": " + fd);
                                out.append("Open device failed.\n");
                            } else {
                                Logger.logVerbose(LOG_TAG, "Open device \"" + device.getDeviceName() + "\" successful");
                                this.sendFd(out, fd);
                            }
                        } else if (status == 1) {
                            Logger.logVerbose(LOG_TAG, "Permission denied to open device \"" + device.getDeviceName() + "\"");
                            out.append("Permission denied.\n" );
                        } else if (status == -1) {
                            out.append("Permission request timeout.\n" );
                        }
                    }
                });
            });
        }

        protected int open(@NonNull UsbDevice device) {
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection == null) return -2;

            int fd = connection.getFileDescriptor();
            if (fd == -1) {
                connection.close();
                return -1;
            }

            openDevices.put(fd, connection);
            return fd;
        }



        protected UsbDevice getDevice(Intent intent, String deviceName) {
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            UsbDevice device = deviceList.get(deviceName);
            if (device == null) {
                Logger.logVerbose(LOG_TAG, "Failed to find device \"" + deviceName + "\"");
                ResultReturner.returnData(this, intent, out -> out.append("No such device.\n"));
            }

            return device;
        }



        protected boolean checkUsbDevicePermission(@NonNull UsbDevice device) {
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            return usbManager.hasPermission(device);
        }

        protected int checkAndRequestUsbDevicePermission(Intent intent, @NonNull UsbDevice device) {
            boolean checkResult = checkUsbDevicePermission(device);
            Logger.logVerbose(LOG_TAG, "Permission check result for device \"" + device.getDeviceName() + "\": " + checkResult);
            if (checkResult) {
                return 0;
            }

            if(!intent.getBooleanExtra("request", false)) {
                return 1;
            }

            Logger.logVerbose(LOG_TAG, "Requesting permission for device \"" + device.getDeviceName() + "\"");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Boolean> result = new AtomicReference<>();

            BroadcastReceiver usbReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent usbIntent) {
                    if (ACTION_USB_PERMISSION.equals(usbIntent.getAction())) {
                        boolean requestResult = usbIntent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                        Logger.logVerbose(LOG_TAG, "Permission request result for device \"" + device.getDeviceName() + "\": " + requestResult);
                        result.set(requestResult);
                    }
                    context.unregisterReceiver(this);
                    latch.countDown();
                }
            };

            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            Intent usbIntent = new Intent(ACTION_USB_PERMISSION);
            // Use explicit intent, otherwise permission request intent will be blocked if intent is
            // mutable and app uses `targetSdkVersion` `>= 34`, or following exception will be logged
            // to logcat if app uses `targetSdkVersion` `< 34`.
            // > `android.app.StackTrace: New mutable implicit PendingIntent: pkg=com.termux.api,
            // > action=com.termux.api.USB_PERMISSION, featureId=null. This will be blocked once the
            // > app targets U+ for security reasons.`
            // - https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
            usbIntent.setPackage(getPackageName());

            // Use mutable intent, otherwise permission request intent will be blocked if app
            // uses `targetSdkVersion` `>= 31` and following exception may be logged to logcat.
            // > java.lang.IllegalArgumentException: com.termux.api: Targeting S+ (version 31 and above)
            // > requires that one of FLAG_IMMUTABLE or FLAG_MUTABLE be specified when creating a PendingIntent.
            // > Strongly consider using FLAG_IMMUTABLE, only use FLAG_MUTABLE if some functionality
            // > depends on the PendingIntent being mutable, e.g. if it needs to be used with inline
            // > replies or bubbles.
            // The intent must not be immutable as the `EXTRA_PERMISSION_GRANTED` extra needs to be
            // returned by the Android framework. Otherwise, if requesting permission after
            // reattaching device, and user presses `OK` to grant permission, the
            // `EXTRA_PERMISSION_GRANTED` extra would not exist in the intent, and default `false`
            // value would get used, and `No permission` condition of the open request would get
            // triggered, even though permission was granted and it won't need to be requested for
            // next open request.
            // - https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability
            //noinspection ObsoleteSdkInt
            int pendingIntentFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, usbIntent, pendingIntentFlags);

            try {
                // Specify flag to not export receiver, otherwise permission request intent will be
                // blocked if app uses `targetSdkVersion` `>= 34`.
                // - https://developer.android.com/about/versions/14/behavior-changes-14#runtime-receivers-exported
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION),
                            Context.RECEIVER_NOT_EXPORTED);
                } else {
                    //noinspection UnspecifiedRegisterReceiverFlag
                    registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
                }

                // Request permission and wait.
                usbManager.requestPermission(device, permissionIntent);

                try {
                    if (!latch.await(30L, TimeUnit.SECONDS)) {
                        Logger.logVerbose(LOG_TAG, "Permission request time out for device \"" + device.getDeviceName() + "\" after 30s");
                        return -1;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                Boolean requestResult = result.get();
                if (requestResult != null) {
                    usbReceiver = null;
                    return requestResult ? 0 : 1;
                } else {
                    return 1;
                }
            } finally {
                try {
                    if (usbReceiver != null) {
                        unregisterReceiver(usbReceiver);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

}
