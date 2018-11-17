package com.termux.api;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.termux.api.util.TermuxApiLogger;
import com.termux.api.util.TermuxApiPermissionActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TermuxApiService extends Service {
    private static final String CHANNEL_ID = "termux-notification";
    private static final String CHANNEL_TITLE = "Termux API notification channel";

    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        final String SOCKET_INPUT_ADDRESS = "termux-input";


        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                String recievedData;
                while (true) {
                    try (LocalServerSocket inputSocket = new LocalServerSocket(SOCKET_INPUT_ADDRESS)) {
                        LocalSocket receiver = inputSocket.accept();
                        if (receiver != null) {
                            InputStream input = receiver.getInputStream();

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];
                            int l;
                            while ((l = input.read(buffer)) > 0) {
                                baos.write(buffer, 0, l);
                            }
                            recievedData = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                            receiver.close();
                            JSONObject data = new JSONObject(recievedData);
                            runApiMethod(getApplicationContext(), data);
                        }
                    } catch(IOException e){
                        e.printStackTrace();
                    } catch(JSONException e){
                        TermuxApiLogger.error("Not valid JSON");
                    }
                }
            }
        };

        new Thread(runnable).start();
        super.onCreate();
    }

    public void runApiMethod(final Context context, JSONObject data) {
        String apiMethod = data.optString("api_method");

        if (apiMethod == null) {
            TermuxApiLogger.error("Missing 'api_method' extra");
            return;
        }

        switch (apiMethod) {
            case "AudioInfo":
                AudioAPI.onReceive(context);
                break;
            case "BatteryStatus":
                BatteryStatusAPI.onReceive(context);
                break;
            case "Brightness":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.System.canWrite(context)) {
                        TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.WRITE_SETTINGS);
                        Toast.makeText(context, "Please enable permission for Termux:API", Toast.LENGTH_LONG).show();

                        //user must enable WRITE_SETTINGS permission this special way
                        Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        context.startActivity(settingsIntent);
                        return;
                    }
                }
                BrightnessAPI.onReceive(context, data);
                break;
            case "CameraInfo":
                CameraInfoAPI.onReceive(context);
                break;
            case "CameraPhoto":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.CAMERA)) {
                    PhotoAPI.onReceive(context, data);
                }
                break;
            case "CallLog":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.READ_CALL_LOG)) {
                    CallLogAPI.onReceive(context, data);
                }
                break;
            case "Clipboard":
                ClipboardAPI.onReceive(context, data);
                break;
            case "ContactList":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.READ_CONTACTS)) {
                    ContactListAPI.onReceive(context);
                }
                break;
            case "Dialog":
                context.startActivity(new Intent(context, DialogActivity.class).putExtra("data", data.toString()).addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
                break;
            case "Download":
                DownloadAPI.onReceive(context, data);
                break;
            case "Fingerprint":
                FingerprintAPI.onReceive(context, data);
                break;
            case "InfraredFrequencies":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.TRANSMIT_IR)) {
                    InfraredAPI.onReceiveCarrierFrequency(context);
                }
                break;
            case "InfraredTransmit":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.TRANSMIT_IR)) {
                    InfraredAPI.onReceiveTransmit(context, data);
                }
                break;
            case "Keystore":
                KeystoreAPI.onReceive(this, data);
                break;
            case "Location":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                    LocationAPI.onReceive(context, data);
                }
                break;
            case "MediaPlayer":
                MediaPlayerAPI.onReceive(context, data);
                break;
            case "MediaScanner":
                MediaScannerAPI.onReceive(context, data);
                break;
            case "MicRecorder":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.RECORD_AUDIO)) {
                    MicRecorderAPI.onReceive(context, data);
                }
                break;
            case "NotificationList":
                ComponentName cn = new ComponentName(context, NotificationService.class);
                String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
                final boolean NotificationServiceEnabled = flat != null && flat.contains(cn.flattenToString());
                if (!NotificationServiceEnabled) {
                    Toast.makeText(context, "Please give Termux:API Notification Access", Toast.LENGTH_LONG).show();
                    context.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                } else {
                    NotificationListAPI.onReceive(context);
                }
                break;
            case "Notification":
                NotificationAPI.onReceiveShowNotification(context, data);
                break;
            case "NotificationRemove":
                NotificationAPI.onReceiveRemoveNotification(context, data);
                break;
            case "Sensor":
                SensorAPI.onReceive(context, data);
                break;
            case "Share":
                ShareAPI.onReceive(context, data);
                break;
            case "SmsInbox":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.READ_SMS, android.Manifest.permission.READ_CONTACTS)) {
                    SmsInboxAPI.onReceive(context, data);
                }
                break;
            case "SmsSend":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.SEND_SMS)) {
                    SmsSendAPI.onReceive(context, data);
                }
                break;
            case "StorageGet":
                StorageGetAPI.onReceive(context, data);
                break;
            case "SpeechToText":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.RECORD_AUDIO)) {
                    SpeechToTextAPI.onReceive(context, data);
                }
                break;
            case "TelephonyCall":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.CALL_PHONE)) {
                    TelephonyAPI.onReceiveTelephonyCall(context, data);
                }
                break;
            case "TelephonyCellInfo":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    TelephonyAPI.onReceiveTelephonyCellInfo(context);
                }
                break;
            case "TelephonyDeviceInfo":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, android.Manifest.permission.READ_PHONE_STATE)) {
                    TelephonyAPI.onReceiveTelephonyDeviceInfo(context);
                }
                break;
            case "TextToSpeech":
                TextToSpeechAPI.onReceive(context, data);
                break;
            case "Toast":
                ToastAPI.onReceive(context, data);
                break;
            case "Torch":
                TorchAPI.onReceive(context, data);
                break;
            case "Vibrate":
                VibrateAPI.onReceive(context, data);
                break;
            case "Volume":
                VolumeAPI.onReceive(context, data);
                break;
            case "Wallpaper":
                WallpaperAPI.onReceive(context, data);
                break;
            case "WifiConnectionInfo":
                WifiAPI.onReceiveWifiConnectionInfo(context);
                break;
            case "WifiScanInfo":
                if (TermuxApiPermissionActivity.checkAndRequestPermissions(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    WifiAPI.onReceiveWifiScanInfo(context);
                }
                break;
            case "WifiEnable":
                WifiAPI.onReceiveWifiEnable(context, data);
                break;
            default:
                TermuxApiLogger.error("Unrecognized 'api_method' extra: '" + apiMethod + "'");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String NOTIFICATION_CHANNEL_ID = "Termux-Notification";
            String channelName = "Termux";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);

            Notification.Builder builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setAutoCancel(true);

            Notification notification = builder.build();
            startForeground(1, notification);
        } else {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("TERNYX")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);
            Notification notification = builder.build();
            startForeground(1, notification);
        }
        return super.onStartCommand(intent, flags, startId);
    }

}
