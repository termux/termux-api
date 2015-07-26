package com.termux.api;

import com.termux.api.util.TermuxApiLogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TermuxApiReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String apiMethod = intent.getStringExtra("api_method");
		if (apiMethod == null) {
			TermuxApiLogger.error("Missing 'api_method' extra");
			return;
		}

		switch (apiMethod) {
		case "BatteryStatus":
			BatteryStatusAPI.onReceive(this, context, intent);
			break;
		case "CameraInfo":
			CameraInfoAPI.onReceive(this, context, intent);
			break;
		case "Clipboard":
			ClipboardAPI.onReceive(this, context, intent);
			break;
		case "ContactList":
			ContactListAPI.onReceive(this, context, intent);
			break;
		case "Dialog":
			context.startActivity(new Intent(context, DialogActivity.class).putExtras(intent.getExtras()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			break;
		case "Download":
			DownloadAPI.onReceive(this, context, intent);
			break;
		case "Location":
			LocationAPI.onReceive(this, context, intent);
			break;
		case "Notification":
			NotificationAPI.onReceive(this, context, intent);
			break;
		case "SmsInbox":
			SmsInboxAPI.onReceive(this, context, intent);
			break;
		case "SmsSend":
			SmsSendAPI.onReceive(this, intent);
			break;
		case "SpeechToText":
			SpeechToTextAPI.onReceive(context, intent);
			break;
		case "TextToSpeech":
			TextToSpeechAPI.onReceive(context, intent);
			break;
		case "Vibrate":
			VibrateAPI.onReceive(this, context, intent);
			break;
		default:
			TermuxApiLogger.error("Unrecognized 'api_method' extra: '" + apiMethod + "'");
		}
	}

}
