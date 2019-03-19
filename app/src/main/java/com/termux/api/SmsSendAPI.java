package com.termux.api;

import android.content.Intent;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;

public class SmsSendAPI {

    static void onReceive(TermuxApiReceiver apiReceiver, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("error").value("Sending SMS is no longer permitted by Google");
                out.endObject();
            }
        });
    }

}
