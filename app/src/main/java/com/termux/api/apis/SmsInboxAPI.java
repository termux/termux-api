package com.termux.api.apis;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Conversations;
import android.provider.Telephony.TextBasedSmsColumns;
import android.util.JsonWriter;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.shared.logger.Logger;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static android.provider.Telephony.TextBasedSmsColumns.ADDRESS;
import static android.provider.Telephony.TextBasedSmsColumns.BODY;
import static android.provider.Telephony.TextBasedSmsColumns.DATE;
import static android.provider.Telephony.TextBasedSmsColumns.READ;
import static android.provider.Telephony.TextBasedSmsColumns.THREAD_ID;
import static android.provider.Telephony.TextBasedSmsColumns.TYPE;

public class SmsInboxAPI {

    private static final String[] DISPLAY_NAME_PROJECTION = {PhoneLookup.DISPLAY_NAME};

    private static final String LOG_TAG = "SmsInboxAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final int offset = intent.getIntExtra("offset", 0);
        final int limit = intent.getIntExtra("limit", 10);
        final String number = intent.hasExtra("from") ? intent.getStringExtra("from"):"";
        final boolean conversation_list = intent.getBooleanExtra("conversation-list", false);
        final Uri contentURI = conversation_list ? typeToContentURI(0) :
                typeToContentURI(number==null || number.isEmpty() ?
                        intent.getIntExtra("type", TextBasedSmsColumns.MESSAGE_TYPE_INBOX): 0);

        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                if (conversation_list) getConversations(context, out, offset, limit);
                else getAllSms(context, out, offset, limit, number, contentURI);
            }
        });
    }

    @SuppressLint("SimpleDateFormat")
    public static void getConversations(Context context, JsonWriter out, int offset, int limit) throws IOException {
        ContentResolver cr = context.getContentResolver();
        String sortOrder = "date DESC";
        try (Cursor c = cr.query(Conversations.CONTENT_URI, null, null, null , sortOrder)) {
            c.moveToLast();

            Map<String, String> nameCache = new HashMap<>();

            out.beginArray();
            for (int i = 0, count = c.getCount(); i < count; i++) {
                int id = c.getInt(c.getColumnIndex(THREAD_ID));

                Cursor cc = cr.query(Sms.CONTENT_URI, null,
                        THREAD_ID + " == '" + id +"'",
                        null, "date DESC");
                if (cc.getCount() == 0) {
                    c.moveToNext();
                    continue;
                }
                cc.moveToFirst();
                writeElement(cc, out, nameCache, context);
                cc.close();
                c.moveToPrevious();
            }
            out.endArray();
        }
    }

    @SuppressLint("SimpleDateFormat")
    private static void writeElement(Cursor c, JsonWriter out, Map<String, String> nameCache, Context context) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        int threadID = c.getInt(c.getColumnIndexOrThrow(THREAD_ID));
        String smsAddress = c.getString(c.getColumnIndexOrThrow(ADDRESS));
        String smsBody = c.getString(c.getColumnIndexOrThrow(BODY));
        boolean read = (c.getInt(c.getColumnIndex(READ)) != 0);
        long smsReceivedDate = c.getLong(c.getColumnIndexOrThrow(DATE));
        // long smsSentDate = c.getLong(c.getColumnIndexOrThrow(TextBasedSmsColumns.DATE_SENT));
        int smsID = c.getInt(c.getColumnIndexOrThrow("_id"));

        String smsSenderName = getContactNameFromNumber(nameCache, context, smsAddress);
        String messageType = getMessageType(c.getInt(c.getColumnIndexOrThrow(TYPE)));

        out.beginObject();
        out.name("threadid").value(threadID);
        out.name("type").value(messageType);
        out.name("read").value(read);

        if (smsSenderName != null) {
            if (messageType.equals("inbox")) {
                out.name("sender").value(smsSenderName);
            } else {
                out.name("sender").value("You");
            }
        }

        out.name("number").value(smsAddress);

        out.name("received").value(dateFormat.format(new Date(smsReceivedDate)));
        // if (Math.abs(smsReceivedDate - smsSentDate) >= 60000) {
        // out.write(" (sent ");
        // out.write(dateFormat.format(new Date(smsSentDate)));
        // out.write(")");
        // }
        out.name("body").value(smsBody);
        out.name("_id").value(smsID);

        out.endObject();
    }


    @SuppressLint("SimpleDateFormat")
    public static void getAllSms(Context context, JsonWriter out, int offset, int limit, String number, Uri contentURI) throws IOException {
        ContentResolver cr = context.getContentResolver();
        String sortOrder = "date DESC LIMIT + " + limit + " OFFSET " + offset;
        try (Cursor c = cr.query(contentURI, null,
                ADDRESS + " LIKE '%" + number + "%'", null, sortOrder)) {
            c.moveToLast();

            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Map<String, String> nameCache = new HashMap<>();

            out.beginArray();
            for (int i = 0, count = c.getCount(); i < count; i++) {
                writeElement(c, out, nameCache, context);
                c.moveToPrevious();
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
            case TextBasedSmsColumns.MESSAGE_TYPE_SENT:
                return Sms.Sent.CONTENT_URI;
            case TextBasedSmsColumns.MESSAGE_TYPE_DRAFT:
                return Sms.Draft.CONTENT_URI;
            case TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX:
                return Sms.Outbox.CONTENT_URI;
            case TextBasedSmsColumns.MESSAGE_TYPE_INBOX:
                return Sms.Inbox.CONTENT_URI;
            case TextBasedSmsColumns.MESSAGE_TYPE_ALL:
            default:
                return Sms.CONTENT_URI;
        }
    }

}
