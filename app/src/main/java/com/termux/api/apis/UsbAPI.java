package com.termux.api.apis;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonWriter;
import android.util.SparseArray;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;

import androidx.annotation.NonNull;

public class UsbAPI {

    private static SparseArray<UsbDeviceConnection> openDevices = new SparseArray<>();
    private static final String LOG_TAG = "UsbAPI";
    private static final String ACTION_USB_PERMISSION = TermuxConstants.TERMUX_API_PACKAGE_NAME + ".USB_PERMISSION";

    public static void onReceive(final TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        String action = intent.getAction();
        if (action == null) {
            ResultReturner.returnData(apiReceiver, intent, out -> out.append("Missing action\n"));
            return;
        }

        switch (action) {
            case "list":
                ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
                    @Override
                    public void writeJson(JsonWriter out) throws Exception {
                        listDevices(context, out);
                    }
                });
                break;
            case "permission":
                handlePermissionRequest(apiReceiver, context, intent);
                break;
            case "open":
                handleOpenRequest(apiReceiver, context, intent);
                break;
            default:
                ResultReturner.returnData(apiReceiver, intent, out -> out.append("Invalid action\n"));
        }
    }

    private static void listDevices(final Context context, JsonWriter out) throws IOException {
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        out.beginArray();
        for (String deviceName : deviceList.keySet()) {
            out.value(deviceName);
        }
        out.endArray();
    }

    private static UsbDevice getDevice(final TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        String deviceName = intent.getStringExtra("device");
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        UsbDevice device = deviceList.get(deviceName);
        if (device == null) {
            ResultReturner.returnData(apiReceiver, intent, out -> out.append("No such device\n"));
        }
        return device;
    }

    private static void handlePermissionRequest(final TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        UsbDevice device = getDevice(apiReceiver, context, intent);
        if (device == null) return;

        requestPermissionAsync(device, context, granted -> {
            ResultReturner.returnData(apiReceiver, intent, out -> {
                out.append(granted ? "yes\n" : "no\n");
            });
        });
    }

    private static void handleOpenRequest(final TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        UsbDevice device = getDevice(apiReceiver, context, intent);
        if (device == null) return;

        requestPermissionAsync(device, context, granted -> {
            ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithAncillaryFd() {
                @Override
                public void writeResult(PrintWriter out) {
                    if (granted) {
                        int result = open(device, context);
                        if (result < 0) {
                            out.append("Failed to open device\n");
                        } else {
                            this.sendFd(out, result);
                        }
                    } else {
                        out.append("No permission\n");
                    }
                }
            });
        });
    }

    private static boolean hasPermission(final @NonNull UsbDevice device, final Context context) {
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        return usbManager.hasPermission(device);
    }

    private static void requestPermissionAsync(final @NonNull UsbDevice device, final Context context, final PermissionCallback callback) {
        if (hasPermission(device, context)) {
            callback.onPermissionResult(true);
            return;
        }

        final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context usbContext, final Intent usbIntent) {
                if (ACTION_USB_PERMISSION.equals(usbIntent.getAction())) {
                    boolean granted = usbIntent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    Logger.logDebug(LOG_TAG, "Permission result: " + granted);
                    callback.onPermissionResult(granted);
                    usbContext.unregisterReceiver(this);
                }
            }
        };

        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        context.getApplicationContext().registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        Logger.logDebug(LOG_TAG, "Requesting permission for device: " + device.getDeviceName());
        usbManager.requestPermission(device, permissionIntent);
    }

    private static int open(final @NonNull UsbDevice device, final Context context) {
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
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

    private interface PermissionCallback {
        void onPermissionResult(boolean granted);
    }
}
