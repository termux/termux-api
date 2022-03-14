package com.termux.api.apis;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;

import androidx.annotation.RequiresPermission;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.PrintWriter;
import java.util.ArrayList;

public class SmsSendAPI {

    private static final String LOG_TAG = "SmsSendAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithStringInput() {
            @RequiresPermission(allOf = { Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS })
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
                    Logger.logError(LOG_TAG, "No recipient given");
                } else {
                    final ArrayList<String> messages = smsManager.divideMessage(inputString);
                    for (String recipient : recipients) {
                        smsManager.sendMultipartTextMessage(recipient, null, messages, null, null);
                    }
                }
            }
        });
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    static SmsManager getSmsManager(Context context, final Intent intent) {
        int slot = intent.getIntExtra("slot", -1);
        if(slot == -1) {
            return SmsManager.getDefault();
        } else {
            SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
            if(sm == null) {
                Logger.logError(LOG_TAG, "SubscriptionManager not supported");
                return null;
            }
            for(SubscriptionInfo si: sm.getActiveSubscriptionInfoList()) {
                if(si.getSimSlotIndex() == slot) {
                    return SmsManager.getSmsManagerForSubscriptionId(si.getSubscriptionId());
                }
            }
            Logger.logError(LOG_TAG, "Sim slot "+slot+" not found");
            return null;
        }
    }

}
