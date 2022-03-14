package com.termux.api.apis;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.JsonWriter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

public class NfcAPI {

    private static final String LOG_TAG = "NfcAPI";

    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        context.startActivity(new Intent(context, NfcActivity.class).putExtras(intent.getExtras()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }



    public static class NfcActivity extends AppCompatActivity {

        private NfcAdapter adapter;
        static String socket_input;
        static String socket_output;
        String mode;
        String param;
        String value;

        private static final String LOG_TAG = "NfcActivity";

        //Check for NFC
        protected void errorNfc(final Context context, Intent intent, String error) {
            ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
                @Override
                public void writeJson(JsonWriter out) throws Exception {
                    NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
                    out.beginObject();
                    if (error.length() > 0)
                        out.name("error").value(error);
                    out.name("nfcPresent").value(null != adapter);
                    if(null!=adapter)
                        out.name("nfcActive").value(adapter.isEnabled());
                    out.endObject();
                }
            });
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            Logger.logDebug(LOG_TAG, "onCreate");

            super.onCreate(savedInstanceState);
            Intent intent = this.getIntent();
            if (intent != null) {
                mode = intent.getStringExtra("mode");
                if (null == mode)
                    mode = "noData";
                param =intent.getStringExtra("param");
                if (null == param)
                    param = "noData";
                value=intent.getStringExtra("value");
                if (null == socket_input) socket_input = intent.getStringExtra("socket_input");
                if (null == socket_output) socket_output = intent.getStringExtra("socket_output");
                if (mode.equals("noData")) {
                    errorNfc(this, intent,"");
                    finish();
                }
            }

            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
            if((null==adapter)||(!adapter.isEnabled())){
                errorNfc(this,intent,"");
                finish();
            }
        }

        @Override
        protected void onResume() {
            Logger.logVerbose(LOG_TAG, "onResume");

            super.onResume();
            adapter = NfcAdapter.getDefaultAdapter(this);
            Intent intentNew = new Intent(this, NfcActivity.class).addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intentNew, 0);
            IntentFilter[] intentFilter = new IntentFilter[]{
                    new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                    new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                    new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)};
            adapter.enableForegroundDispatch(this, pendingIntent, intentFilter, null);
        }

        @Override
        protected void onNewIntent(Intent intent) {
            Logger.logDebug(LOG_TAG, "onNewIntent");

            intent.putExtra("socket_input", socket_input);
            intent.putExtra("socket_output", socket_output);

            if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
                try {
                    postResult(this, intent);
                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Error posting result" ,e);
                }
                finish();
            }
            super.onNewIntent(intent);
        }

        @Override
        protected void onPause() {
            Logger.logDebug(LOG_TAG, "onPause");

            adapter.disableForegroundDispatch(this);
            super.onPause();
        }

        @Override
        protected void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            socket_input = null;
            socket_output = null;
            super.onDestroy();
        }

        protected void postResult(final Context context, Intent intent) {
            ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
                @Override
                public void writeJson(JsonWriter out) throws Exception {
                    Logger.logDebug(LOG_TAG, "postResult");
                    try {
                        switch (mode) {
                            case "write":
                                switch (param) {
                                    case "text":
                                        Logger.logVerbose(LOG_TAG, "Write start");
                                        onReceiveNfcWrite(context, intent);
                                        Logger.logVerbose(LOG_TAG, "Write end");
                                        break;
                                    default:
                                        onUnexpectedAction(out, "Wrong Params", "Should be text for TAG");
                                        break;
                                }
                                break;
                            case "read":
                                switch (param){
                                    case "short":
                                        readNDEFTag(intent,out);
                                        break;
                                    case "full":
                                        readFullNDEFTag(intent,out);
                                        break;
                                    case "noData":
                                        readNDEFTag(intent,out);
                                        break;
                                    default:
                                        onUnexpectedAction(out, "Wrong Params", "Should be correct param value");
                                        break;
                                }
                                break;
                            default:
                                onUnexpectedAction(out, "Wrong Params", "Should be correct mode value ");
                                break;
                        }
                    } catch (Exception e){
                        onUnexpectedAction(out, "exception", e.getMessage());
                    }
                }
            });
        }

        public void onReceiveNfcWrite( final Context context, Intent intent) throws Exception {
            Logger.logVerbose(LOG_TAG, "onReceiveNfcWrite");

            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            NdefRecord record = NdefRecord.createTextRecord("en", value);
            NdefMessage msg = new NdefMessage(new NdefRecord[]{record});
            Ndef ndef = Ndef.get(tag);
            ndef.connect();
            ndef.writeNdefMessage(msg);
            ndef.close();
        }


        public void readNDEFTag(Intent intent, JsonWriter out) throws Exception {
            Logger.logVerbose(LOG_TAG, "readNDEFTag");

            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
            Parcelable[] msgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Ndef ndefTag = Ndef.get(tag);
            boolean bNdefPresent = false;
            String[] strs = tag.getTechList();
            for (String s: strs){
                if (s.equals("android.nfc.tech.Ndef")) {
                    bNdefPresent = true;
                    break;
                }
            }
            if (!bNdefPresent){
                onUnexpectedAction(out, "Wrong Technology","termux API support only NFEF Tag");
                return;
            }
            NdefMessage[] nmsgs = new NdefMessage[msgs.length];
            if (msgs.length == 1) {
                nmsgs[0] = (NdefMessage) msgs[0];
                NdefRecord[] records = nmsgs[0].getRecords();
                out.beginObject();
                if (records.length >0 ) {
                    {
                        out.name("Record");
                        if (records.length > 1)
                            out.beginArray();
                        for (NdefRecord record: records){
                            out.beginObject();
                            int pos = 1 + record.getPayload()[0];
                            pos = (NdefRecord.TNF_WELL_KNOWN==record.getTnf())?(int)record.getPayload()[0]+1:0;
                            int len = record.getPayload().length - pos;
                            byte[] msg = new byte[len];
                            System.arraycopy(record.getPayload(), pos, msg, 0, len);
                            out.name("Payload").value(new String(msg));
                            out.endObject();
                        }
                        if (records.length > 1)
                            out.endArray();
                    }
                }
                out.endObject();
            }
        }

        public void readFullNDEFTag(Intent intent, JsonWriter out) throws Exception {
            Logger.logVerbose(LOG_TAG, "readFullNDEFTag");

            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Ndef ndefTag = Ndef.get(tag);
            Parcelable[] msgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

            String[] strs = tag.getTechList();
            boolean bNdefPresent = false;
            for (String s: strs){
                if (s.equals("android.nfc.tech.Ndef")) {
                    bNdefPresent = true;
                    break;
                }
            }
            if (!bNdefPresent){
                onUnexpectedAction(out, "Wrong Technology","termux API support only NFEF Tag");
                return;
            }
            NdefMessage[] nmsgs = new NdefMessage[msgs.length];
            out.beginObject();
            {
                byte[] tagID = tag.getId();
                StringBuilder sp = new StringBuilder();
                for (byte tagIDpart : tagID) { sp.append(String.format("%02x", tagIDpart)); }
                out.name("id").value(sp.toString());
                out.name("typeTag").value(ndefTag.getType());
                out.name("maxSize").value(ndefTag.getMaxSize());
                out.name("techList");
                {
                    out.beginArray();
                    String[] tlist = tag.getTechList();
                    for (String str : tlist) {
                        out.value(str);
                    }
                    out.endArray();
                }
                if (msgs.length == 1) {
                    Logger.logInfo(LOG_TAG, "-->> readFullNDEFTag - 06");
                    nmsgs[0] = (NdefMessage) msgs[0];
                    NdefRecord[] records = nmsgs[0].getRecords();
                    {
                        out.name("record");
                        if (records.length > 1)
                            out.beginArray();
                        for (NdefRecord record : records) {
                            out.beginObject();
                            out.name("type").value(new String(record.getType()));
                            out.name("tnf").value(record.getTnf());
                            if (records[0].toUri() != null) out.name("URI").value(record.toUri().toString());
                            out.name("mime").value(record.toMimeType());
                            int pos = 1 + record.getPayload()[0];
                            pos = (NdefRecord.TNF_WELL_KNOWN==record.getTnf())?(int)record.getPayload()[0]+1:0;
                            int len = record.getPayload().length - pos;
                            byte[] msg = new byte[len];
                            System.arraycopy(record.getPayload(), pos, msg, 0, len);
                            out.name("payload").value(new String(msg));
                            out.endObject();
                        }
                        if (records.length > 1) out.endArray();
                    }
                }

            }
            out.endObject();
        }

        protected void onUnexpectedAction(JsonWriter out,String error, String description) throws Exception {
            out.beginObject();
            out.name("error").value(error);
            out.name("description").value(description);
            out.endObject();
            out.flush();
        }
    }

}
