package com.termux.api;

import java.util.UUID;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.termux.api.util.ResultReturner;

/** Shows a notification. Driven by the termux-show-notification script. */
public class NotificationAPI {

	static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
		String content = intent.getStringExtra("content");
		String notificationId = intent.getStringExtra("id");
		if (notificationId == null) {
			notificationId = UUID.randomUUID().toString();
		}
		String title = intent.getStringExtra("title");

		String url = intent.getStringExtra("url");
		PendingIntent pendingIntent = null;
		if (url != null) {
			Intent urlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			pendingIntent = PendingIntent.getActivity(context, 0, urlIntent, 0);
		}

		Notification.Builder notification = new Notification.Builder(context).setSmallIcon(android.R.drawable.ic_popup_reminder).setColor(0xFF000000)
				.setContentTitle(title).setContentText(content);
		if (pendingIntent != null)
			notification.setContentIntent(pendingIntent);

		NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		manager.notify(notificationId, 0, notification.build());

		ResultReturner.noteDone(apiReceiver, intent);
	}

}
