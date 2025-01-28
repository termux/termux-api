package com.termux.api.apis;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class BluetoothScanAPI {

    private static final ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private static final String LOG_TAG = "BluetoothScanAPI";
    private static BluetoothAdapter adapter;
    private static boolean scanning = false;

    private static final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    devices.add(device);
                }
            }
        }
    };

    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                String mode = intent.getStringExtra("mode");
                if (mode == null) return;
                out.beginObject();
                switch (mode.toLowerCase()) {
                    case "start":
                        if (start(context)) {
                            out.name("error").value(false);
                        } else {
                            out.name("error").value(true);
                            out.name("reason").value("Already running!");
                        }
                        break;
                    case "stop":
                        if (stop(context)) {
                            out.name("error").value(false);
                            printList(out, true);
                        } else {
                            out.name("error").value(true);
                            out.name("reason").value("Already stopped!");
                        }
                        break;
                    case "info":
                        if (scanning) {
                            out.name("error").value(false);
                            printList(out, false);
                        } else {
                            out.name("error").value(true);
                            out.name("reason").value("Scan is not running!");
                        }
                        break;
                    default:
                        out.name("error").value(true);
                        out.name("reason").value("Invalid option! Choose one from [start, stop, info]");
                        break;
                }
                out.endObject();
            }
        });


    }

    private static void printList(JsonWriter out, boolean clear) {
        try {
            out.name("devices");
            out.beginArray();
            for (BluetoothDevice device : devices) {
                out.beginObject();
                out.name("name").value(device.getName());
                out.name("address").value(device.getAddress());
                out.endObject();
            }
            out.endArray();
            if (clear) {
                devices.clear();
            }
        } catch (IOException ex) {
            Logger.logError(Arrays.toString(ex.getStackTrace()));
        }
    }

    private static boolean start(Context context) {
        if (scanning) return false;
        context.getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        adapter = BluetoothAdapter.getDefaultAdapter();
        adapter.startDiscovery();
        scanning = true;
        return true;
    }

    private static boolean stop(Context context) {
        if (!scanning) return false;
        adapter.cancelDiscovery();
        context.getApplicationContext().unregisterReceiver(receiver);
        scanning = false;
        return true;
    }

}
