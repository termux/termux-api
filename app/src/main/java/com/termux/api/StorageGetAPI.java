package com.termux.api;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

public class StorageGetAPI {

    private static final String FILE_EXTRA = "com.termux.api.storage.file";

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultWriter() {
            @Override
            public void writeResult(PrintWriter out) {
                final String fileExtra = intent.getStringExtra("file");
                if (fileExtra == null || !new File(fileExtra).getParentFile().canWrite()) {
                    out.println("ERROR: Not a writable folder: " + fileExtra);
                    return;
                }

                Intent intent = new Intent(context, StorageActivity.class);
                intent.putExtra(FILE_EXTRA, fileExtra);
                context.startActivity(intent);
            }
        });
    }

    public static class StorageActivity extends Activity {

        private String outputFile;

        @Override
        public void onResume() {
            super.onResume();
            outputFile = getIntent().getStringExtra(FILE_EXTRA);

            // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser.
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

            // Filter to only show results that can be "opened", such as a
            // file (as opposed to a list of contacts or timezones)
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            intent.setType("*/*");

            startActivityForResult(intent, 42);
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
            super.onActivityResult(requestCode, resultCode, resultData);
            if (resultCode == RESULT_OK) {
                Uri data = resultData.getData();
                try {
                    try (InputStream in = getContentResolver().openInputStream(data)) {
                        try (OutputStream out = new FileOutputStream(outputFile)) {
                            byte[] buffer = new byte[8192];
                            while (true) {
                                int read = in.read(buffer);
                                if (read <= 0) {
                                    break;
                                } else {
                                    out.write(buffer, 0, read);
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    TermuxApiLogger.error("Error copying " + data + " to " + outputFile);
                }
            }
            finish();
        }

    }

}
