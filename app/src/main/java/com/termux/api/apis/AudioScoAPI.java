package com.termux.api.apis;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.util.JsonWriter;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.IOException;
import java.util.List;

/**
 * API for managing Bluetooth SCO audio channel independently of recording.
 *
 * Provides two commands via the "command" string extra:
 *   "enable"  — activate SCO and switch AudioManager to MODE_IN_COMMUNICATION
 *   "disable" — deactivate SCO and restore AudioManager mode
 *   (default) — return current SCO state as JSON
 *
 * Shell usage:
 *   termux-audio-sco           # query state
 *   termux-audio-sco enable    # start SCO
 *   termux-audio-sco disable   # stop SCO
 *
 * After "enable" succeeds, any subsequent termux-microphone-record call with
 * --ei source 7 (VOICE_COMMUNICATION) will capture from the Bluetooth microphone
 * because the SCO channel is already established.
 *
 * Note: on Android 12+ "enable" uses setCommunicationDevice(); on older versions
 * it uses the deprecated startBluetoothSco() with an async state broadcast.
 */
public class AudioScoAPI {

    private static final String LOG_TAG = "AudioScoAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context,
                                 final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        String command = intent.hasExtra("command") ? intent.getStringExtra("command") : "status";

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            returnError(apiReceiver, intent, "AudioManager unavailable");
            return;
        }

        switch (command == null ? "status" : command) {
            case "enable":
                handleEnable(apiReceiver, context, intent, am);
                break;
            case "disable":
                handleDisable(apiReceiver, intent, am);
                break;
            default:
                handleStatus(apiReceiver, intent, am);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // enable
    // -------------------------------------------------------------------------

    private static void handleEnable(final TermuxApiReceiver apiReceiver, final Context context,
                                     final Intent intent, final AudioManager am) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: getProfileProxy is async — return immediately, set up SCO in background.
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                returnError(apiReceiver, intent, "Bluetooth is not enabled");
                return;
            }

            // Reply to caller right away so the socket doesn't hang.
            returnJson(apiReceiver, intent, false, "SCO enable initiated, check status with termux-audio-sco");

            adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    BluetoothHeadset headset = (BluetoothHeadset) proxy;
                    List<BluetoothDevice> devices = headset.getConnectedDevices();
                    adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy);

                    if (devices.isEmpty()) {
                        Logger.logError(LOG_TAG, "SCO enable: no Bluetooth headset connected");
                        return;
                    }

                    AudioDeviceInfo scoDevice = findScoDevice(am);
                    if (scoDevice == null) {
                        Logger.logError(LOG_TAG, "SCO enable: no Bluetooth SCO audio device available");
                        return;
                    }

                    boolean ok = am.setCommunicationDevice(scoDevice);
                    if (!ok) {
                        Logger.logError(LOG_TAG, "SCO enable: setCommunicationDevice failed");
                        return;
                    }
                    Logger.logInfo(LOG_TAG, "SCO enabled via setCommunicationDevice");
                }

                @Override
                public void onServiceDisconnected(int profile) { }
            }, BluetoothProfile.HEADSET);

        } else {
            // Android < 12: startBluetoothSco is async — return immediately.
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            am.startBluetoothSco();

            // Reply to caller right away.
            returnJson(apiReceiver, intent, false, "SCO enable initiated, check status with termux-audio-sco");

            final BroadcastReceiver[] receiverHolder = new BroadcastReceiver[1];
            receiverHolder[0] = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent scoIntent) {
                    int state = scoIntent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_ERROR);

                    if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                        context.unregisterReceiver(receiverHolder[0]);
                        Logger.logInfo(LOG_TAG, "SCO enabled via startBluetoothSco");
                    } else if (state == AudioManager.SCO_AUDIO_STATE_ERROR
                               || state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                        context.unregisterReceiver(receiverHolder[0]);
                        am.stopBluetoothSco();
                        am.setMode(AudioManager.MODE_NORMAL);
                        Logger.logError(LOG_TAG, "SCO connection failed (state=" + state + ")");
                    }
                    // SCO_AUDIO_STATE_CONNECTING — keep waiting
                }
            };
            context.registerReceiver(receiverHolder[0],
                new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
        }
    }

    // -------------------------------------------------------------------------
    // disable
    // -------------------------------------------------------------------------

    private static void handleDisable(final TermuxApiReceiver apiReceiver, final Intent intent,
                                      final AudioManager am) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.clearCommunicationDevice();
        } else {
            am.stopBluetoothSco();
            am.setMode(AudioManager.MODE_NORMAL);
        }
        returnJson(apiReceiver, intent, false, "SCO disabled");
    }

    // -------------------------------------------------------------------------
    // status
    // -------------------------------------------------------------------------

    private static void handleStatus(final TermuxApiReceiver apiReceiver, final Intent intent,
                                     final AudioManager am) {
        boolean scoOn;
        String deviceName = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioDeviceInfo dev = am.getCommunicationDevice();
            scoOn = (dev != null && dev.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
            if (dev != null) deviceName = dev.getProductName() != null
                ? dev.getProductName().toString() : null;
        } else {
            scoOn = am.isBluetoothScoOn();
        }

        final boolean finalScoOn = scoOn;
        final String finalDeviceName = deviceName;

        ResultReturner.returnData(apiReceiver, intent, out -> {
            JsonWriter writer = new JsonWriter(out);
            try {
                writer.beginObject();
                writer.name("sco_active").value(finalScoOn);
                writer.name("audio_mode").value(modeToString(am.getMode()));
                if (finalDeviceName != null)
                    writer.name("device").value(finalDeviceName);
                writer.endObject();
                writer.flush();
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "JSON write error", e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AudioDeviceInfo findScoDevice(AudioManager am) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (AudioDeviceInfo dev : (java.util.List<AudioDeviceInfo>) am.getAvailableCommunicationDevices()) {
                if (dev.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
                    return dev;
            }
        }
        return null;
    }

    private static void returnJson(final TermuxApiReceiver apiReceiver, final Intent intent,
                                   final boolean scoActive, final String message) {
        ResultReturner.returnData(apiReceiver, intent, out -> {
            JsonWriter writer = new JsonWriter(out);
            try {
                writer.beginObject();
                writer.name("sco_active").value(scoActive);
                writer.name("message").value(message);
                writer.endObject();
                writer.flush();
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "JSON write error", e);
            }
        });
    }

    private static void returnError(final TermuxApiReceiver apiReceiver, final Intent intent,
                                    final String error) {
        ResultReturner.returnData(apiReceiver, intent, out -> {
            JsonWriter writer = new JsonWriter(out);
            try {
                writer.beginObject();
                writer.name("error").value(error);
                writer.endObject();
                writer.flush();
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "JSON write error", e);
            }
        });
    }

    private static String modeToString(int mode) {
        switch (mode) {
            case AudioManager.MODE_NORMAL:            return "NORMAL";
            case AudioManager.MODE_RINGTONE:          return "RINGTONE";
            case AudioManager.MODE_IN_CALL:           return "IN_CALL";
            case AudioManager.MODE_IN_COMMUNICATION:  return "IN_COMMUNICATION";
            default:                                  return "UNKNOWN(" + mode + ")";
        }
    }
}
