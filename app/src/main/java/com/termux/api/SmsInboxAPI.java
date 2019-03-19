package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;

public class SmsInboxAPI {

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("error").value("Reading SMS is no longer permitted by Google");
                out.endObject();
            }
        });
    }

}
