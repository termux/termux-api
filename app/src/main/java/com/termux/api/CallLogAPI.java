package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;

/**
 * API that allows you to get call log history information
 */
public class CallLogAPI {

    static void onReceive(final Context context, final Intent intent) {
        ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("error").value("Call log is no longer permitted by Google");
                out.endObject();
            }
        });

    }

}
