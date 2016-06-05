package com.termux.api;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Telephony;
import android.provider.Telephony.TextBasedSmsColumns;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Call with
 * <p/>
 * <pre>
 * $ am broadcast --user 0 -n net.aterm.extras/.SmsLister
 *
 * Broadcasting: Intent { cmp=net.aterm.extras/.SmsLister }
 * Broadcast completed: result=13, data="http://fornwall.net"
 * </pre>
 */
public class SmsInboxAPI {

    private static final String[] DISPLAY_NAME_PROJECTION = {PhoneLookup.DISPLAY_NAME};

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        final int offset = intent.getIntExtra("offset", 0);
        final int limit = intent.getIntExtra("limit", 50);

        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                getAllSms(context, out, offset, limit);
            }
        });
    }

    @SuppressLint("SimpleDateFormat")
    public static void getAllSms(Context context, JsonWriter out, int offset, int limit) throws IOException {
        ContentResolver cr = context.getContentResolver();
        String sortOrder = "date DESC LIMIT + " + limit + " OFFSET " + offset;
        try (Cursor c = cr.query(Telephony.Sms.Inbox.CONTENT_URI, null, null, null, sortOrder)) {

            c.moveToLast();

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm");
            Map<String, String> nameCache = new HashMap<>();

            out.beginArray();
            for (int i = 0, count = c.getCount(); i < count; i++) {
                // String smsId = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.Inbox._ID));
                String smsAddress = c.getString(c.getColumnIndexOrThrow(TextBasedSmsColumns.ADDRESS));
                String smsBody = c.getString(c.getColumnIndexOrThrow(TextBasedSmsColumns.BODY));
                boolean read = (c.getInt(c.getColumnIndex(TextBasedSmsColumns.READ)) != 0);
                long smsReceivedDate = c.getLong(c.getColumnIndexOrThrow(TextBasedSmsColumns.DATE));
                // long smsSentDate = c.getLong(c.getColumnIndexOrThrow(TextBasedSmsColumns.DATE_SENT));

                String smsSenderName = getContactNameFromNumber(nameCache, context, smsAddress);

                out.beginObject();
                out.name("read").value(read);

                if (smsSenderName != null) {
                    out.name("sender").value(smsSenderName);
                }
                out.name("number").value(smsAddress);

                out.name("received").value(dateFormat.format(new Date(smsReceivedDate)));
                // if (Math.abs(smsReceivedDate - smsSentDate) >= 60000) {
                // out.write(" (sent ");
                // out.write(dateFormat.format(new Date(smsSentDate)));
                // out.write(")");
                // }
                out.name("body").value(smsBody);

                c.moveToPrevious();
                out.endObject();
            }
            out.endArray();
        }
    }

    private static String getContactNameFromNumber(Map<String, String> cache, Context context, String number) {
        if (cache.containsKey(number))
            return cache.get(number);
        Uri contactUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor c = context.getContentResolver().query(contactUri, DISPLAY_NAME_PROJECTION, null, null, null)) {
            String name = c.moveToFirst() ? c.getString(c.getColumnIndex(PhoneLookup.DISPLAY_NAME)) : null;
            cache.put(number, name);
            return name;
        }
    }

}
