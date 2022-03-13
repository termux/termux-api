package com.termux.api;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.JsonWriter;

import androidx.annotation.Nullable;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StorageGetAPI {

    private static final String INTENT_EXTRA = "com.termux.api.storage.intent";
    private static final int ERROR_COPY_FILE = 1;
    private static final int ERROR_INITIATE_INTENT = 2;

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        try {
            Intent intent1 = new Intent(context, StorageActivity.class);
            intent1.putExtra(INTENT_EXTRA, intent);
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent1);
        } catch (Exception e) {
            if (intent.getBooleanExtra("json", true)) {
                ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
                    @Override
                    public void writeJson(JsonWriter out) throws Exception {
                        out.beginObject();
                        out.name("error").value(ERROR_INITIATE_INTENT);
                        out.name("uri").beginArray();
                        out.endArray();
                        out.endObject();
                    }
                });
            }
            return;
        }
        if (!intent.getBooleanExtra("wait", false)) {
            ResultReturner.returnData(apiReceiver, intent, out -> { });
        }
    }

    public static class StorageActivity extends Activity {

        private String outputFile;
        private Intent mIntent;
        private boolean wait;
        private boolean folder;
        private boolean persist;
        private boolean multiple;
        private boolean json;
        private String mimeType;
        private Uri[] data;
        private int error;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Intent intent = this.getIntent();
            mIntent = intent.getParcelableExtra(INTENT_EXTRA);
            outputFile = mIntent.getStringExtra("file");
            mimeType = mIntent.getStringExtra("type");
            wait = mIntent.getBooleanExtra("wait", false);
            folder = mIntent.getBooleanExtra("folder", false);
            persist = mIntent.getBooleanExtra("persist", false);
            multiple = mIntent.getBooleanExtra("multiple", false);
            json = mIntent.getBooleanExtra("json", true);
            data = null;
            error = 0;
        }

        @Override
        public void onResume() {
            super.onResume();
            try {
                // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser.
                Intent intent = new Intent(folder ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_OPEN_DOCUMENT);

                if (multiple && !folder) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }

                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                if (!folder) {
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    if (mimeType != null) {
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeType.split(","));
                    }
                }
                int flags = folder ? Intent.FLAG_GRANT_PREFIX_URI_PERMISSION : 0;
                if (persist) {
                    flags |= Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                }
                intent.setFlags(flags);

                startActivityForResult(intent, 42);
            } catch (Exception e) {
                if (json) {
                    ResultReturner.returnData(this, mIntent, new ResultReturner.ResultJsonWriter() {
                        @Override
                        public void writeJson(JsonWriter out) throws Exception {
                            out.beginObject();
                            out.name("error").value(ERROR_INITIATE_INTENT);
                            out.name("uri").beginArray();
                            out.endArray();
                            out.endObject();
                        }
                    });
                }
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
            InputStream in = null;
            OutputStream out = null;
            super.onActivityResult(requestCode, resultCode, resultData);
            if (resultCode == RESULT_OK) {
                try {
                    if(resultData.getClipData() != null) { // checking multiple selection or not
                        data = new Uri[resultData.getClipData().getItemCount()];
                        for(int i = 0; i < resultData.getClipData().getItemCount(); i++) {
                            data[i] = resultData.getClipData().getItemAt(i).getUri();
                        }
                    } else {
                        data = new Uri[1];
                        data[0] = resultData.getData();
                    }
                    if (!folder && outputFile != null) {
                        for (int i = 0; i < data.length; i++) {
                            in = getContentResolver().openInputStream(data[i]);
                            out = new FileOutputStream(multiple ? String.format(outputFile, i) : outputFile);
                            byte[] buffer = new byte[8192];
                            while (true) {
                                int read = in.read(buffer);
                                if (read <= 0) {
                                    break;
                                } else {
                                    out.write(buffer, 0, read);
                                }
                            }
                            in.close();
                            out.close();
                        }
                    }

                } catch (Exception e) {
                    TermuxApiLogger.error("Error copying file(s). ");
                    error = ERROR_COPY_FILE;
                } finally {
                    try { if (in != null) { in.close(); } } catch (Exception e) { }
                    try { if (out != null) { out.close(); } } catch (Exception e) { }
                }
            }
            if (wait) {
                if (json) {
                    ResultReturner.returnData(this, mIntent, new ResultReturner.ResultJsonWriter() {
                        @Override
                        public void writeJson(JsonWriter out) throws Exception {
                            out.beginObject();
                            out.name("error").value(error);
                            out.name("uri").beginArray();
                            if (data != null) {
                                for (int i = 0; i < data.length; i++) {
                                    out.value(data[i].toString());
                                }
                            }
                            out.endArray();
                            out.endObject();
                        }
                    });
                } else {
                    ResultReturner.returnData(this, mIntent, output -> {
                        if (data != null) {
                            for (int i = 0; i < data.length; i++) {
                                output.println(data[i].toString());
                            }
                        }
                    });
                }
            }
            finishAndRemoveTask();
        }
    }

}
