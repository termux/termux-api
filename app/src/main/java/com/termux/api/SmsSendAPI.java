package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.util.ArrayList;

public class SmsSendAPI {

    static void onReceive(Context context, final JSONObject opts) {
        ResultReturner.returnData(context, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
                final SmsManager smsManager = SmsManager.getDefault();
                JSONArray recipientsJson = opts.optJSONArray("recipients");
                String[] recipients = new String[recipientsJson.length()];
                for(int i = 0; i < recipientsJson.length(); i++){
                    recipients[i] = recipientsJson.optString(i);
                }

                if (recipients == null) {
                    // Used by old versions of termux-send-sms.
                    String recipient = opts.optString("recipient");
                    if (recipient != null) recipients = new String[]{recipient};
                }

                if (recipients == null || recipients.length == 0) {
                    TermuxApiLogger.error("No recipient given");
                } else {
                    final ArrayList<String> messages = smsManager.divideMessage(inputString);
                    for (String recipient : recipients) {
                        smsManager.sendMultipartTextMessage(recipient, null, messages, null, null);
                    }
                }
            }
        });
    }

}
