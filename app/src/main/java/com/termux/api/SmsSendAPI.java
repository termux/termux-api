package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.io.PrintWriter;
import java.util.ArrayList;

public class SmsSendAPI {

    static void onReceive(TermuxApiReceiver apiReceiver, Context context, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
                final SmsManager smsManager = getSmsManager(context,intent);
                if(smsManager == null) return;

                String[] recipients = intent.getStringArrayExtra("recipients");

                if (recipients == null) {
                    // Used by old versions of termux-send-sms.
                    String recipient = intent.getStringExtra("recipient");
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

    static SmsManager getSmsManager(Context context, final Intent intent) {
        int slot = intent.getIntExtra("slot", -1);
        if(slot == -1) {
            return SmsManager.getDefault();
        } else {
            SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
            if(sm == null) {
                TermuxApiLogger.error("SubscriptionManager not supported");
                return null;
            }
            for(SubscriptionInfo si: sm.getActiveSubscriptionInfoList()) {
                if(si.getSimSlotIndex() == slot) {
                    return SmsManager.getSmsManagerForSubscriptionId(si.getSubscriptionId());
                }
            }
            TermuxApiLogger.error("Sim slot "+slot+" not found");
            return null;
        }
    }

}
