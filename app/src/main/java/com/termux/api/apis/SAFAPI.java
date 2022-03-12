package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.FileUtils;
import android.provider.DocumentsContract;
import android.util.JsonWriter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

public class SAFAPI {

    private static final String LOG_TAG = "SAFAPI";

    public static class SAFActivity extends AppCompatActivity {

        private boolean resultReturned = false;

        private static final String LOG_TAG = "SAFActivity";

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            Logger.logDebug(LOG_TAG, "onCreate");

            super.onCreate(savedInstanceState);
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i, 0);
        }
        
        @Override
        protected void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            super.onDestroy();
            finishAndRemoveTask();
            if (! resultReturned) {
                ResultReturner.returnData(this, getIntent(), out -> out.write(""));
                resultReturned = true;
            }
        }
        
        @Override
        protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));

            super.onActivityResult(requestCode, resultCode, data);
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    resultReturned = true;
                    ResultReturner.returnData(this, getIntent(), out -> out.println(data.getDataString()));
                }
            }
            finish();
        }
    }

    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        String method = intent.getStringExtra("safmethod");
        if (method == null) {
            Logger.logError(LOG_TAG, "safmethod extra null");
            return;
        }
        try {
            switch (method) {
                case "getManagedDocumentTrees":
                    getManagedDocumentTrees(apiReceiver, context, intent);
                    break;
                case "manageDocumentTree":
                    manageDocumentTree(context, intent);
                    break;
                case "writeDocument":
                    writeDocument(apiReceiver, context, intent);
                    break;
                case "createDocument":
                    createDocument(apiReceiver, context, intent);
                    break;
                case "readDocument":
                    readDocument(apiReceiver, context, intent);
                    break;
                case "listDirectory":
                    listDirectory(apiReceiver, context, intent);
                    break;
                case "removeDocument":
                    removeDocument(apiReceiver, context, intent);
                    break;
                case "statURI":
                    statURI(apiReceiver, context, intent);
                    break;
                default:
                    Logger.logError(LOG_TAG, "Unrecognized safmethod: " + "'" + method + "'");
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in SAFAPI", e);
        }
    }
    
    private static void getManagedDocumentTrees(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter()
        {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginArray();
                for (UriPermission p : context.getContentResolver().getPersistedUriPermissions()) {
                    statDocument(out, context, treeUriToDocumentUri(p.getUri()));
                }
                out.endArray();
            }
        });
    }
    
    private static void manageDocumentTree(Context context, Intent intent) {
        Intent i = new Intent(context, SAFActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResultReturner.copyIntentExtras(intent, i);
        context.startActivity(i);
    }
    
    private static void writeDocument(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String uri = intent.getStringExtra("uri");
        if (uri == null) {
            Logger.logError(LOG_TAG, "uri extra null");
            return;
        }
        DocumentFile f = DocumentFile.fromSingleUri(context, Uri.parse(uri));
        if (f == null) {
            return;
        }
        writeDocumentFile(apiReceiver, context, intent, f);
    }
    
    private static void createDocument(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String treeURIString = intent.getStringExtra("treeuri");
        if (treeURIString == null) {
            Logger.logError(LOG_TAG, "treeuri extra null");
            return;
        }
        String name = intent.getStringExtra("filename");
        if (name == null) {
            Logger.logError(LOG_TAG, "filename extra null");
            return;
        }
        String mime = intent.getStringExtra("mimetype");
        if (mime == null) {
            mime = "application/octet-stream";
        }
        Uri treeURI = Uri.parse(treeURIString);
        String id = DocumentsContract.getTreeDocumentId(treeURI);
        try {
            id = DocumentsContract.getDocumentId(Uri.parse(treeURIString));
        } catch (IllegalArgumentException ignored) {}
        final String finalMime = mime;
        final String finalId = id;
        ResultReturner.returnData(apiReceiver, intent, out -> 
                out.println(DocumentsContract.createDocument(context.getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(treeURI, finalId), finalMime, name).toString())
        );
    }
    
    private static void readDocument(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String uri = intent.getStringExtra("uri");
        if (uri == null) {
            Logger.logError(LOG_TAG, "uri extra null");
            return;
        }
        DocumentFile f = DocumentFile.fromSingleUri(context, Uri.parse(uri));
        if (f == null) {
            return;
        }
        returnDocumentFile(apiReceiver, context, intent, f);
    }
    
    private static void listDirectory(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String treeURIString = intent.getStringExtra("treeuri");
        if (treeURIString == null) {
            Logger.logError(LOG_TAG, "treeuri extra null");
            return;
        }
        Uri treeURI = Uri.parse(treeURIString);
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter()
        {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginArray();
                String id = DocumentsContract.getTreeDocumentId(treeURI);
                try {
                    id = DocumentsContract.getDocumentId(Uri.parse(treeURIString));
                } catch (IllegalArgumentException ignored) {}
                try (Cursor c = context.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(treeURIString), id), new String[] {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID }, null, null, null)) {
                    while (c.moveToNext()) {
                        String documentId = c.getString(0);
                        Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeURI, documentId);
                        statDocument(out, context, documentUri);
                    }
                } catch (UnsupportedOperationException ignored) { }
                out.endArray();
            }
        });
    }
    
    private static void statURI(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String uriString = intent.getStringExtra("uri");
        if (uriString == null) {
            Logger.logError(LOG_TAG, "uri extra null");
            return;
        }
        Uri docUri = treeUriToDocumentUri(Uri.parse(uriString));
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter()
        {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                statDocument(out, context, Uri.parse(docUri.toString()));
            }
        });
    }
    
    
    private static void removeDocument(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String uri = intent.getStringExtra("uri");
        if (uri == null) {
            Logger.logError(LOG_TAG, "uri extra null");
            return;
        }
        ResultReturner.returnData(apiReceiver, intent, out -> {
            try {
                if (DocumentsContract.deleteDocument(context.getContentResolver(), Uri.parse(uri))) {
                    out.println(0);
                } else {
                    out.println(1);
                }
            } catch (FileNotFoundException | IllegalArgumentException e ) {
                out.println(2);
            }
        });
    }
    
    
    private static Uri treeUriToDocumentUri(Uri tree) {
        String id = DocumentsContract.getTreeDocumentId(tree);
        try {
            id = DocumentsContract.getDocumentId(tree);
        } catch (IllegalArgumentException ignored) {}
        return DocumentsContract.buildDocumentUriUsingTree(tree, id);
    }
    
    private static void statDocument(JsonWriter out, Context context, Uri uri) throws Exception {
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c == null || c.getCount() == 0) {
                return;
            }
            c.moveToNext();
            out.beginObject();
            out.name("name");
            out.value(c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)));
            out.name("type");
            String mime = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
            out.value(mime);
            out.name("uri");
            out.value(uri.toString());
            if (! DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                out.name("length");
                out.value(c.getInt(c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)));
            }
            out.endObject();
        }
    }
    
    private static void returnDocumentFile(TermuxApiReceiver apiReceiver, Context context, Intent intent, DocumentFile f) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.BinaryOutput()
        {
            @Override
            public void writeResult(OutputStream out) throws Exception {
                try (InputStream in = context.getContentResolver().openInputStream(f.getUri())) {
                    writeInputStreamToOutputStream(in, out);
                }
            }
        });
    }
    
    private static void writeDocumentFile(TermuxApiReceiver apiReceiver, Context context, Intent intent, DocumentFile f) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.WithInput()
        {
            @Override
            public void writeResult(PrintWriter unused) throws Exception {
                try (OutputStream out = context.getContentResolver().openOutputStream(f.getUri(), "rwt")) {
                    writeInputStreamToOutputStream(in, out);
                }
            }
        });
    }
    
    private static void writeInputStreamToOutputStream(InputStream in, OutputStream out) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            FileUtils.copy(in, out);
        }
        else {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
    
}
