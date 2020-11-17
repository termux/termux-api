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

public class SmsInboxAPI {

    private static final String[] DISPLAY_NAME_PROJECTION = {PhoneLookup.DISPLAY_NAME};

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        final int offset = intent.getIntExtra("offset", 0);
        final int limit = intent.getIntExtra("limit", 50);
        final Uri contentURI = typeToContentURI(intent.getIntExtra("type", TextBasedSmsColumns.MESSAGE_TYPE_INBOX));

        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                getAllSms(context, out, offset, limit, contentURI);
            }
        });
    }

    @SuppressLint("SimpleDateFormat")
    public static void getAllSms(Context context, JsonWriter out, int offset, int limit, Uri contentURI) throws IOException {
        ContentResolver cr = context.getContentResolver();
        String sortOrder = "date DESC LIMIT + " + limit + " OFFSET " + offset;
        try (Cursor c = cr.query(contentURI, null, null, null, sortOrder)) {

            c.moveToLast();

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm");
            Map<String, String> nameCache = new HashMap<>();

            out.beginArray();
            for (int i = 0, count = c.getCount(); i < count; i++) {
                int threadID = c.getInt(c.getColumnIndexOrThrow(TextBasedSmsColumns.THREAD_ID));
                String smsAddress = c.getString(c.getColumnIndexOrThrow(TextBasedSmsColumns.ADDRESS));
                String smsBody = c.getString(c.getColumnIndexOrThrow(TextBasedSmsColumns.BODY));
                boolean read = (c.getInt(c.getColumnIndex(TextBasedSmsColumns.READ)) != 0);
                long smsReceivedDate = c.getLong(c.getColumnIndexOrThrow(TextBasedSmsColumns.DATE));
                // long smsSentDate = c.getLong(c.getColumnIndexOrThrow(TextBasedSmsColumns.DATE_SENT));

                String smsSenderName = getContactNameFromNumber(nameCache, context, smsAddress);
                String messageType = getMessageType(c.getInt(c.getColumnIndexOrThrow(TextBasedSmsColumns.TYPE)));

                out.beginObject();
                out.name("threadid").value(threadID);
                out.name("type").value(messageType);
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

    private static String getMessageType(int type) {
        switch (type)
        {
            case TextBasedSmsColumns.MESSAGE_TYPE_INBOX:
                return "inbox";
            case TextBasedSmsColumns.MESSAGE_TYPE_SENT:
                return "sent";
            case TextBasedSmsColumns.MESSAGE_TYPE_DRAFT:
                return "draft";
            case TextBasedSmsColumns.MESSAGE_TYPE_FAILED:
                return "failed";
            case TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX:
                return "outbox";
            default:
                return "";
        }
    }

    private static Uri typeToContentURI(int type) {
        switch (type) {
            case TextBasedSmsColumns.MESSAGE_TYPE_ALL:
                return Telephony.Sms.CONTENT_URI;
            case TextBasedSmsColumns.MESSAGE_TYPE_SENT:
                return Telephony.Sms.Sent.CONTENT_URI;
            case TextBasedSmsColumns.MESSAGE_TYPE_DRAFT:
                return Telephony.Sms.Draft.CONTENT_URI;
            case TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX:
                return Telephony.Sms.Outbox.CONTENT_URI;
            case TextBasedSmsColumns.MESSAGE_TYPE_INBOX:
            default:
                return Telephony.Sms.Inbox.CONTENT_URI;
        }
    }


}
