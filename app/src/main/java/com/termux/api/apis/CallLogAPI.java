package com.termux.api.apis;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.CallLog;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * API that allows you to get call log history information
 */
public class CallLogAPI {

    private static final String LOG_TAG = "CallLogAPI";

    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final int offset = intent.getIntExtra("offset", 0);
        final int limit = intent.getIntExtra("limit", 50);

        ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
            public void writeJson(JsonWriter out) throws Exception {
                getCallLogs(context, out, offset, limit);
            }
        });

    }

    private static void getCallLogs(Context context, JsonWriter out, int offset, int limit) throws IOException {
        ContentResolver contentResolver = context.getContentResolver();

        try (Cursor cur = contentResolver.query(CallLog.Calls.CONTENT_URI.buildUpon().
                appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, String.valueOf(limit)).
                appendQueryParameter(CallLog.Calls.OFFSET_PARAM_KEY, String.valueOf(offset))
                .build(), null, null, null, "date DESC")) {
            cur.moveToLast();

            int nameIndex = cur.getColumnIndex(CallLog.Calls.CACHED_NAME);
            int numberIndex = cur.getColumnIndex(CallLog.Calls.NUMBER);
            int dateIndex = cur.getColumnIndex(CallLog.Calls.DATE);
            int durationIndex = cur.getColumnIndex(CallLog.Calls.DURATION);
            int callTypeIndex = cur.getColumnIndex(CallLog.Calls.TYPE);
            int simTypeIndex = cur.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID);

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            out.beginArray();

            for (int j = 0, count = cur.getCount(); j < count; ++j) {
                out.beginObject();

                out.name("name").value(getCallerNameString(cur.getString(nameIndex)));
                out.name("phone_number").value(cur.getString(numberIndex));
                out.name("type").value(getCallTypeString(cur.getInt(callTypeIndex)));
                out.name("date").value(getDateString(cur.getLong(dateIndex), dateFormat));
                out.name("duration").value(getTimeString(cur.getInt(durationIndex)));
                out.name("sim_id").value(cur.getString(simTypeIndex));

                cur.moveToPrevious();
                out.endObject();
            }
            out.endArray();
        }
    }

    private static String getCallTypeString(int type) {
        switch (type) {
            case CallLog.Calls.BLOCKED_TYPE:    return "BLOCKED";
            case CallLog.Calls.INCOMING_TYPE:   return "INCOMING";
            case CallLog.Calls.MISSED_TYPE:     return "MISSED";
            case CallLog.Calls.OUTGOING_TYPE:   return "OUTGOING";
            case CallLog.Calls.REJECTED_TYPE:   return "REJECTED";
            case CallLog.Calls.VOICEMAIL_TYPE:  return "VOICEMAIL";
            default: return "UNKNOWN_TYPE";
        }
    }

    private static String getCallerNameString(String name) {
        return name == null ? "UNKNOWN_CALLER" : name;
    }

    private static String getDateString(Long date, DateFormat dateFormat) {
        return dateFormat.format(new Date(date));
    }

    private static String getTimeString(int totalSeconds) {
        int hours = (totalSeconds / 3600);
        int mins = (totalSeconds % 3600) / 60;
        int secs = (totalSeconds % 60);

        String result = "";

        // only show hours if we have them
        if (hours > 0) {
            result += String.format(Locale.getDefault(), "%02d:", hours);
        }
        result += String.format(Locale.getDefault(), "%02d:%02d", mins, secs);
        return result;
    }
}
