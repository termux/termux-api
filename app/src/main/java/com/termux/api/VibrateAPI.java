package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.os.Vibrator;

import com.termux.api.util.ResultReturner;

public class VibrateAPI {

	static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
		Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
		int milliseconds = intent.getIntExtra("duration_ms", 1000);
		vibrator.vibrate(milliseconds);
		ResultReturner.noteDone(apiReceiver, intent);
	}

}
