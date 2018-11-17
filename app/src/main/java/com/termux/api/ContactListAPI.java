package com.termux.api;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.JsonWriter;
import android.util.SparseArray;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;

public class ContactListAPI {

    static void onReceive(final Context context) {
        ResultReturner.returnData(context, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                listContacts(context, out);
            }
        });
    }

    static void listContacts(Context context, JsonWriter out) throws Exception {
        ContentResolver cr = context.getContentResolver();

        SparseArray<String> contactIdToNumberMap = new SparseArray<>();
        String[] projection = {Phone.NUMBER, Phone.CONTACT_ID};
        String selection = Phone.CONTACT_ID + " IS NOT NULL AND " + Phone.NUMBER + " IS NOT NULL";
        try (Cursor phones = cr.query(Phone.CONTENT_URI, projection, selection, null, null)) {
            int phoneNumberIdx = phones.getColumnIndexOrThrow(Phone.NUMBER);
            int phoneContactIdIdx = phones.getColumnIndexOrThrow(Phone.CONTACT_ID);
            while (phones.moveToNext()) {
                String number = phones.getString(phoneNumberIdx);
                int contactId = phones.getInt(phoneContactIdIdx);
                // int type = phones.getInt(phones.getColumnIndex(Phone.TYPE));
                contactIdToNumberMap.put(contactId, number);
            }
        }

        out.beginArray();
        try (Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, ContactsContract.Contacts.DISPLAY_NAME)) {
            int contactDisplayNameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME);
            int contactIdIdx = cursor.getColumnIndex(BaseColumns._ID);

            while (cursor.moveToNext()) {
                int contactId = cursor.getInt(contactIdIdx);
                String number = contactIdToNumberMap.get(contactId);
                if (number != null) {
                    String contactName = cursor.getString(contactDisplayNameIdx);
                    out.beginObject().name("name").value(contactName).name("number").value(number).endObject();
                }
            }
        } finally {
            out.endArray();
        }
    }
}
