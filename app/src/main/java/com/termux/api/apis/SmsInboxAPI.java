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

import static android.provider.Telephony.TextBasedSmsColumns.*;

import androidx.annotation.Nullable;

/**
 * **See Also:**
 * - https://developer.android.com/reference/android/provider/Telephony
 * - https://developer.android.com/reference/android/provider/Telephony.Sms.Conversations
 * - https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns
 * - https://developer.android.com/reference/android/provider/BaseColumns
 */
public class SmsInboxAPI {

    private static final String[] DISPLAY_NAME_PROJECTION = {PhoneLookup.DISPLAY_NAME};

    private static final String LOG_TAG = "SmsInboxAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        String value;

        final boolean conversationList = intent.getBooleanExtra("conversation-list", false);

        final boolean conversationReturnMultipleMessages = intent.getBooleanExtra("conversation-return-multiple-messages", false);
        final boolean conversationReturnNestedView = intent.getBooleanExtra("conversation-return-nested-view", false);
        final boolean conversationReturnNoOrderReverse = intent.getBooleanExtra("conversation-return-no-order-reverse", false);

        final int conversationOffset = intent.getIntExtra("conversation-offset", -1);
        final int conversationLimit = intent.getIntExtra("conversation-limit", -1);
        final String conversationSelection = intent.getStringExtra("conversation-selection");

        /*
           NOTE: When conversation or messages are queried from the Android database, first the
           sort order is applied, and then any offset and limit values are used to filter the
           entries. Since the default sort order is 'date DESC', Android returns the latest dated
           conversations or messages first, but the API reverses the order by default (with
           `Cursor.moveToLast()`/`Cursor.moveToPrevious()`) so that  the latest entries are printed
           at the end. If the order should not be reversed, then pass the respective
           `*-return-no-order-reverse` extras.
         */
        value = intent.getStringExtra("conversation-sort-order");
        if (value == null || value.isEmpty()) {
            value = "date DESC";
        }
        final String conversationSortOrder = value;


        final int messageOffset = intent.getIntExtra("offset", 0);
        final int messageLimit = intent.getIntExtra("limit", 10);
        final int messageTypeColumn = intent.getIntExtra("type", TextBasedSmsColumns.MESSAGE_TYPE_INBOX);
        final String messageSelection = intent.getStringExtra("message-selection");

        value = intent.getStringExtra("from");
        if (value == null || value.isEmpty()) {
            value = null;
        }
        final String messageAddress = value;

        value = intent.getStringExtra("message-sort-order");
        if (value == null || value.isEmpty()) {
            value = "date DESC";
        }
        final String messageSortOrder = value;

        final boolean messageReturnNoOrderReverse = intent.getBooleanExtra("message-return-no-order-reverse", false);

        Uri contentURI;
        if (conversationList) {
            contentURI = typeToContentURI(TextBasedSmsColumns.MESSAGE_TYPE_ALL);
        } else {
            contentURI = typeToContentURI(messageAddress == null ?
                    messageTypeColumn : TextBasedSmsColumns.MESSAGE_TYPE_ALL);
        }

        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                if (conversationList) {
                    getConversations(context, out,
                            conversationOffset, conversationLimit,
                            conversationSelection,
                            conversationSortOrder,
                            conversationReturnMultipleMessages,conversationReturnNestedView,
                            conversationReturnNoOrderReverse,
                            messageOffset, messageLimit,
                            messageSelection,
                            messageSortOrder,
                            messageReturnNoOrderReverse);
                } else {
                    getAllSms(context, out, contentURI,
                            messageOffset, messageLimit,
                            messageSelection, messageAddress,
                            messageSortOrder,
                            messageReturnNoOrderReverse);
                }
            }
        });
    }

    @SuppressLint("SimpleDateFormat")
    public static void getConversations(Context context, JsonWriter out,
                                        int conversationOffset, int conversationLimit,
                                        String conversationSelection,
                                        String conversationSortOrder,
                                        boolean conversationReturnMultipleMessages, boolean conversationReturnNestedView,
                                        boolean conversationReturnNoOrderReverse,
                                        int messageOffset, int messageLimit,
                                        String messageSelection,
                                        String messageSortOrder,
                                        boolean messageReturnNoOrderReverse) throws IOException {
        ContentResolver cr = context.getContentResolver();

        // `THREAD_ID` is used to select messages for a conversation, so do not allow caller to pass it.
        if (messageSelection != null && messageSelection.matches("^(.*[ \t\n])?" + THREAD_ID + "[ \t\n].*$")) {
            throw new IllegalArgumentException(
                    "The 'conversation-selection' cannot contain '" + THREAD_ID + "': `" + messageSelection + "`");
        }

        conversationSortOrder = getSortOrder(conversationSortOrder, conversationOffset, conversationLimit);
        messageSortOrder = getSortOrder(messageSortOrder, messageOffset, messageLimit);

        int index;
        try (Cursor conversationCursor = cr.query(Conversations.CONTENT_URI,
                null, conversationSelection, null , conversationSortOrder)) {
            int conversationCount = conversationCursor.getCount();
            if (conversationReturnNoOrderReverse) {
                conversationCursor.moveToFirst();
            } else {
                conversationCursor.moveToLast();
            }

            Map<String, String> nameCache = new HashMap<>();

            if (conversationReturnNestedView) {
                out.beginObject();
            } else {
                out.beginArray();
            }
            for (int i = 0; i < conversationCount; i++) {
                index = conversationCursor.getColumnIndex(THREAD_ID);
                if (index < 0) {
                    conversationCursor.moveToPrevious();
                    continue;
                }

                int id = conversationCursor.getInt(index);

                if (conversationReturnNestedView) {
                    out.name(String.valueOf(id));
                    out.beginArray();
                }

                String[] messageSelectionArgs = null;
                if (messageSelection == null || messageSelection.isEmpty()) {
                    messageSelection = "";
                } else {
                    messageSelection += " ";
                }

                Cursor messageCursor = cr.query(Sms.CONTENT_URI, null,
                        messageSelection + THREAD_ID + " == '" + id +"'", messageSelectionArgs,
                        messageSortOrder);

                int messageCount = messageCursor.getCount();
                if (messageCount > 0) {
                    if (conversationReturnMultipleMessages) {
                        if (messageReturnNoOrderReverse) {
                            messageCursor.moveToFirst();
                        } else {
                            messageCursor.moveToLast();
                        }

                        for (int j = 0; j < messageCount; j++) {
                            writeElement(messageCursor, out, nameCache, context);

                            if (messageReturnNoOrderReverse) {
                                messageCursor.moveToNext();
                            } else {
                                messageCursor.moveToPrevious();
                            }
                        }
                    } else {
                        messageCursor.moveToFirst();
                        writeElement(messageCursor, out, nameCache, context);
                    }
                }

                messageCursor.close();

                if (conversationReturnNestedView) {
                    out.endArray();
                }

                if (conversationReturnNoOrderReverse) {
                    conversationCursor.moveToNext();
                } else {
                    conversationCursor.moveToPrevious();
                }
            }
            if (conversationReturnNestedView) {
                out.endObject();
            } else {
                out.endArray();
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private static void writeElement(Cursor c, JsonWriter out, Map<String, String> nameCache, Context context) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        int index;
        int threadID = c.getInt(c.getColumnIndexOrThrow(THREAD_ID));
        String smsAddress = c.getString(c.getColumnIndexOrThrow(ADDRESS));
        String smsBody = c.getString(c.getColumnIndexOrThrow(BODY));
        long smsReceivedDate = c.getLong(c.getColumnIndexOrThrow(DATE));
        // long smsSentDate = c.getLong(c.getColumnIndexOrThrow(TextBasedSmsColumns.DATE_SENT));
        int smsID = c.getInt(c.getColumnIndexOrThrow("_id"));

        String smsSenderName = getContactNameFromNumber(nameCache, context, smsAddress);
        String messageType = getMessageType(c.getInt(c.getColumnIndexOrThrow(TYPE)));

        out.beginObject();
        out.name("threadid").value(threadID);
        out.name("type").value(messageType);

        index = c.getColumnIndex(READ);
        if (index >= 0) {
            out.name("read").value(c.getInt(index) != 0);
        }

        if (smsSenderName != null) {
            if (messageType.equals("inbox")) {
                out.name("sender").value(smsSenderName);
            } else {
                out.name("sender").value("You");
            }
        }

        out.name("address").value(smsAddress);
        // Deprecated: Address can be a name like service provider instead of a number.
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
    public static void getAllSms(Context context, JsonWriter out,
                                 Uri contentURI,
                                 int messageOffset, int messageLimit,
                                 String messageSelection, String messageAddress,
                                 String messageSortOrder,
                                 boolean messageReturnNoOrderReverse) throws IOException {
        ContentResolver cr = context.getContentResolver();

        String[] messageSelectionArgs = null;
        if (messageSelection == null || messageSelection.isEmpty()) {
            if (messageAddress != null && !messageAddress.isEmpty()) {
                messageSelection = ADDRESS + " LIKE ?";
                messageSelectionArgs = new String[]{messageAddress};
            }
        }

        messageSortOrder = getSortOrder(messageSortOrder, messageOffset, messageLimit);

        try (Cursor messageCursor = cr.query(contentURI, null,
                messageSelection, messageSelectionArgs,
                messageSortOrder)) {
            int messageCount = messageCursor.getCount();
            if (messageReturnNoOrderReverse) {
                messageCursor.moveToFirst();
            } else {
                messageCursor.moveToLast();
            }

            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Map<String, String> nameCache = new HashMap<>();

            out.beginArray();
            for (int i = 0; i < messageCount; i++) {
                writeElement(messageCursor, out, nameCache, context);

                if (messageReturnNoOrderReverse) {
                    messageCursor.moveToNext();
                } else {
                    messageCursor.moveToPrevious();
                }
            }
            out.endArray();
        }
    }

    private static String getContactNameFromNumber(Map<String, String> cache, Context context, String number) {
        if (cache.containsKey(number)) {
            return cache.get(number);
        }

        int index;
        Uri contactUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor c = context.getContentResolver().query(contactUri, DISPLAY_NAME_PROJECTION, null, null, null)) {
            String name = null;
            if (c.moveToFirst()) {
                index = c.getColumnIndex(PhoneLookup.DISPLAY_NAME);
                if (index >= 0) {
                    name = c.getString(index);
                }
            }

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

    @Nullable
    private static String getSortOrder(String sortOrder, int offset, int limit) {
        if (sortOrder == null) {
            sortOrder = "";
        }
        if (limit >= 0) {
            sortOrder += " LIMIT " + limit;
        }
        if (offset >= 0) {
            sortOrder += " OFFSET " + offset;
        }
        if (sortOrder.isEmpty()) {
            sortOrder = null;
        }
        return sortOrder;
    }

}
