package com.termux.api;

import java.io.PrintWriter;

import android.content.Intent;
import android.telephony.SmsManager;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

public class SmsSendAPI {

	static void onReceive(TermuxApiReceiver apiReceiver, final Intent intent) {
		ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithStringInput() {
			@Override
			public void writeResult(PrintWriter out) throws Exception {
				final SmsManager smsManager = SmsManager.getDefault();
				String recipientExtra = intent.getStringExtra("recipient");
				if (recipientExtra == null) {
					TermuxApiLogger.error("No 'recipient' extra");
				} else {
					smsManager.sendTextMessage(recipientExtra, null, inputString, null, null);
				}
			}
		});
	}

}
