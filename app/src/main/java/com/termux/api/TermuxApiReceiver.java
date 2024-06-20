package com.termux.api;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import com.termux.api.apis.AudioAPI;
import com.termux.api.apis.BatteryStatusAPI;
import com.termux.api.apis.BrightnessAPI;
import com.termux.api.apis.CallLogAPI;
import com.termux.api.apis.CameraInfoAPI;
import com.termux.api.apis.CameraPhotoAPI;
import com.termux.api.apis.ClipboardAPI;
import com.termux.api.apis.ContactListAPI;
import com.termux.api.apis.DialogAPI;
import com.termux.api.apis.DownloadAPI;
import com.termux.api.apis.FingerprintAPI;
import com.termux.api.apis.InfraredAPI;
import com.termux.api.apis.JobSchedulerAPI;
import com.termux.api.apis.KeystoreAPI;
import com.termux.api.apis.LocationAPI;
import com.termux.api.apis.MediaPlayerAPI;
import com.termux.api.apis.MediaScannerAPI;
import com.termux.api.apis.MicRecorderAPI;
import com.termux.api.apis.NfcAPI;
import com.termux.api.apis.NotificationAPI;
import com.termux.api.apis.NotificationListAPI;
import com.termux.api.apis.SAFAPI;
import com.termux.api.apis.SensorAPI;
import com.termux.api.apis.ShareAPI;
import com.termux.api.apis.SmsInboxAPI;
import com.termux.api.apis.SmsSendAPI;
import com.termux.api.apis.SpeechToTextAPI;
import com.termux.api.apis.StorageGetAPI;
import com.termux.api.apis.TelephonyAPI;
import com.termux.api.apis.TextToSpeechAPI;
import com.termux.api.apis.ToastAPI;
import com.termux.api.apis.TorchAPI;
import com.termux.api.apis.UsbAPI;
import com.termux.api.apis.VibrateAPI;
import com.termux.api.apis.VolumeAPI;
import com.termux.api.apis.WallpaperAPI;
import com.termux.api.apis.WifiAPI;
import com.termux.api.activities.TermuxApiPermissionActivity;
import com.termux.api.util.ResultReturner;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.plugins.TermuxPluginUtils;

public class TermuxApiReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "TermuxApiReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        TermuxAPIApplication.setLogConfig(context, false);
        Logger.logDebug(LOG_TAG, "Intent Received:\n" + IntentUtils.getIntentString(intent));

        try {
            doWork(context, intent);
        } catch (Throwable t) {
            String message = "Error in " + LOG_TAG;
            // Make sure never to throw exception from BroadCastReceiver to avoid "process is bad"
            // behaviour from the Android system.
            Logger.logStackTraceWithMessage(LOG_TAG, message, t);

            TermuxPluginUtils.sendPluginCommandErrorNotification(context, LOG_TAG,
                    TermuxConstants.TERMUX_API_APP_NAME + " Error", message, t);

            ResultReturner.noteDone(this, intent);
        }
    }

    private void doWork(Context context, Intent intent) {
        String apiMethod = intent.getStringExtra("api_method");
        if (apiMethod == null) {
            Logger.logError(LOG_TAG, "Missing 'api_method' extra");
            return;
        }

        switch (apiMethod) {
            case "AudioInfo":
                AudioAPI.onReceive(this, context, intent);
                break;
            case "BatteryStatus":
                BatteryStatusAPI.onReceive(this, context, intent);
                break;
            case "Brightness":
                if (!Settings.System.canWrite(context)) {
                    TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.WRITE_SETTINGS);
                    Toast.makeText(context, "Please enable permission for Termux:API", Toast.LENGTH_LONG).show();

                    // user must enable WRITE_SETTINGS permission this special way
                    Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    context.startActivity(settingsIntent);
                    return;
                }
                BrightnessAPI.onReceive(this, context, intent);
                break;
            case "CameraInfo":
                CameraInfoAPI.onReceive(this, context, intent);
                break;
            case "CameraPhoto":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.CAMERA)) {
                    CameraPhotoAPI.onReceive(this, context, intent);
                }
                break;
            case "CallLog":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.READ_CALL_LOG)) {
                    CallLogAPI.onReceive(context, intent);
                }
                break;
            case "Clipboard":
                ClipboardAPI.onReceive(this, context, intent);
                break;
            case "ContactList":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.READ_CONTACTS)) {
                    ContactListAPI.onReceive(this, context, intent);
                }
                break;
            case "Dialog":
                DialogAPI.onReceive(context, intent);
                break;
            case "Download":
                DownloadAPI.onReceive(this, context, intent);
                break;
            case "Fingerprint":
                FingerprintAPI.onReceive(context, intent);
                break;
            case "InfraredFrequencies":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.TRANSMIT_IR)) {
                    InfraredAPI.onReceiveCarrierFrequency(this, context, intent);
                }
                break;
            case "InfraredTransmit":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.TRANSMIT_IR)) {
                    InfraredAPI.onReceiveTransmit(this, context, intent);
                }
                break;
            case "JobScheduler":
                JobSchedulerAPI.onReceive(this, context, intent);
                break;
            case "Keystore":
                KeystoreAPI.onReceive(this, intent);
                break;
            case "Location":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    LocationAPI.onReceive(this, context, intent);
                }
                break;
            case "MediaPlayer":
                MediaPlayerAPI.onReceive(context, intent);
                break;
            case "MediaScanner":
                MediaScannerAPI.onReceive(this, context, intent);
                break;
            case "MicRecorder":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.RECORD_AUDIO)) {
                    MicRecorderAPI.onReceive(context, intent);
                }
                break;
            case "Nfc":
                NfcAPI.onReceive(context, intent);
                break;
            case "NotificationList":
                ComponentName cn = new ComponentName(context, NotificationListAPI.NotificationService.class);
                String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
                final boolean NotificationServiceEnabled = flat != null && flat.contains(cn.flattenToString());
                if (!NotificationServiceEnabled) {
                    Toast.makeText(context,"Please give Termux:API Notification Access", Toast.LENGTH_LONG).show();
                    context.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    NotificationListAPI.onReceive(this, context, intent);
                }
                break;
            case "Notification":
                NotificationAPI.onReceiveShowNotification(this, context, intent);
                break;
            case "NotificationChannel":
                NotificationAPI.onReceiveChannel(this, context, intent);
                break;
            case "NotificationRemove":
                NotificationAPI.onReceiveRemoveNotification(this, context, intent);
                break;
            case "NotificationReply":
                NotificationAPI.onReceiveReplyToNotification(this, context, intent);
                break;
            case "SAF":
                SAFAPI.onReceive(this, context, intent);
                break;
            case "Sensor":
                SensorAPI.onReceive(context, intent);
                break;
            case "Share":
                ShareAPI.onReceive(this, context, intent);
                break;
            case "SmsInbox":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)) {
                    SmsInboxAPI.onReceive(this, context, intent);
                }
                break;
            case "SmsSend":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS)) {
                    SmsSendAPI.onReceive(this, context, intent);
                }
                break;
            case "StorageGet":
                StorageGetAPI.onReceive(this, context, intent);
                break;
            case "SpeechToText":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.RECORD_AUDIO)) {
                    SpeechToTextAPI.onReceive(context, intent);
                }
                break;
            case "TelephonyCall":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.CALL_PHONE)) {
                    TelephonyAPI.onReceiveTelephonyCall(this, context, intent);
                }
                break;
            case "TelephonyCellInfo":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    TelephonyAPI.onReceiveTelephonyCellInfo(this, context, intent);
                }
                break;
            case "TelephonyDeviceInfo":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.READ_PHONE_STATE)) {
                    TelephonyAPI.onReceiveTelephonyDeviceInfo(this, context, intent);
                }
                break;
            case "TextToSpeech":
                TextToSpeechAPI.onReceive(context, intent);
                break;
            case "Toast":
                ToastAPI.onReceive(context, intent);
                break;
            case "Torch":
                TorchAPI.onReceive(this, context, intent);
                break;
            case "Usb":
                UsbAPI.onReceive(context, intent);
                break;
            case "Vibrate":
                VibrateAPI.onReceive(this, context, intent);
                break;
            case "Volume":
                VolumeAPI.onReceive(this, context, intent);
                break;
            case "Wallpaper":
                WallpaperAPI.onReceive(context, intent);
                break;
            case "WifiConnectionInfo":
                WifiAPI.onReceiveWifiConnectionInfo(this, context, intent);
                break;
            case "WifiScanInfo":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, intent, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    WifiAPI.onReceiveWifiScanInfo(this, context, intent);
                }
                break;
            case "WifiEnable":
                WifiAPI.onReceiveWifiEnable(this, context, intent);
                break;
            default:
                Logger.logError(LOG_TAG, "Unrecognized 'api_method' extra: '" + apiMethod + "'");
        }
    }

}
