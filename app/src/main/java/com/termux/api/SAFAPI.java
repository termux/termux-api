package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.FileUtils;
import android.util.JsonWriter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

public class SAFAPI
{
    public static class SAFActivity extends AppCompatActivity {
        private boolean resultReturned = false;
        
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i, 0);
        }
        
        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (! resultReturned) {
                ResultReturner.returnData(this, getIntent(), out -> out.write(""));
            }
        }
        
        @Override
        protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    resultReturned = true;
                    ResultReturner.returnData(this, getIntent(), out -> out.write(data.getDataString()));
                }
            }
            finish();
        }
    }
    
    static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String method = intent.getStringExtra("safmethod");
        if (method == null) {
            TermuxApiLogger.error("safmethod extra null");
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
                    TermuxApiLogger.error("Unrecognized safmethod: " + "'" + method + "'");
            }
        } catch (Exception e) {
            TermuxApiLogger.error("Error in SAFAPI", e);
        }
    }
    
    private static void getManagedDocumentTrees(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter()
        {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginArray();
                for (UriPermission p : context.getContentResolver().getPersistedUriPermissions()) {
                    out.value(p.getUri().toString());
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
            TermuxApiLogger.error("uri extra null");
            return;
        }
        DocumentFile f = DocumentFile.fromSingleUri(context, Uri.parse(uri));
        if (f == null) {
            return;
        }
        writeDocumentFile(apiReceiver, context, intent, f);
    }
    
    private static void createDocument(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String treeuri = intent.getStringExtra("treeuri");
        if (treeuri == null) {
            TermuxApiLogger.error("treeuri extra null");
            return;
        }
        String mime = intent.getStringExtra("mimetype");
        if (mime == null) {
            TermuxApiLogger.error("mimetype extra null");
            return;
        }
        String name = intent.getStringExtra("filename");
        if (name == null) {
            TermuxApiLogger.error("filename extra null");
            return;
        }
        DocumentFile tree = DocumentFile.fromTreeUri(context, Uri.parse(treeuri));
        if (tree == null) {
            return;
        }
        DocumentFile f = tree.createFile(mime, name);
        if (f == null) {
            return;
        }
        ResultReturner.returnData(apiReceiver, intent, out -> out.write(f.getUri().toString()));
    }
    
    private static void readDocument(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String uri = intent.getStringExtra("uri");
        if (uri == null) {
            TermuxApiLogger.error("uri extra null");
            return;
        }
        DocumentFile f = DocumentFile.fromSingleUri(context, Uri.parse(uri));
        if (f == null) {
            return;
        }
        returnDocumentFile(apiReceiver, context, intent, f);
    }
    
    private static void listDirectory(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String treeuri = intent.getStringExtra("treeuri");
        if (treeuri == null) {
            TermuxApiLogger.error("treeuri extra null");
            return;
        }
        DocumentFile tree = DocumentFile.fromSingleUri(context, Uri.parse(treeuri));
        if (tree == null) {
            return;
        }
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter()
        {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginArray();
                try {
                    for (DocumentFile f : tree.listFiles()) {
                        statDocumentFile(out, f);
                    }
                } catch (UnsupportedOperationException ignored) { }
                out.endArray();
            }
        });
    }
    
    private static void statURI(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String uri = intent.getStringExtra("uri");
        if (uri == null) {
            TermuxApiLogger.error("uri extra null");
            return;
        }
        DocumentFile f = DocumentFile.fromSingleUri(context, Uri.parse(uri));
        if (f == null) {
            return;
        }
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter()
        {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                statDocumentFile(out, f);
            }
        });
    }
    
    
    private static void statDocumentFile(JsonWriter out, DocumentFile f) throws Exception {
        out.beginObject();
        out.name("name");
        out.value(f.getName());
        out.name("type");
        out.value(f.getType());
        out.name("uri");
        out.value(f.getUri().toString());
        if (! f.isDirectory()) {
            out.name("length");
            out.value(f.length());
        }
        out.endObject();
    }
    
    private static void removeDocument(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        String uri = intent.getStringExtra("uri");
        if (uri == null) {
            TermuxApiLogger.error("uri extra null");
            return;
        }
        DocumentFile f = DocumentFile.fromTreeUri(context, Uri.parse(uri));
        if (f == null) {
            return;
        }
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter()
        {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.value(f.delete());
            }
        });
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
