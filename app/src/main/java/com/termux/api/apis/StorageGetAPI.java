package com.termux.api.apis;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.file.TermuxFileUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StorageGetAPI {

    private static final String FILE_EXTRA = TermuxConstants.TERMUX_API_PACKAGE_NAME + ".storage.file";

    private static final String LOG_TAG = "StorageGetAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        ResultReturner.returnData(apiReceiver, intent, out -> {
            final String fileExtra = intent.getStringExtra("file");
            if (fileExtra == null || fileExtra.isEmpty()) {
                out.println("ERROR: " + "File path not passed");

                return;
            }

            // Get canonical path of fileExtra
            String filePath = TermuxFileUtils.getCanonicalPath(fileExtra, null, true);
            String fileParentDirPath = FileUtils.getFileDirname(filePath);
            Logger.logVerbose(LOG_TAG, "filePath=\"" + filePath + "\", fileParentDirPath=\"" + fileParentDirPath + "\"");

            Error error = FileUtils.checkMissingFilePermissions("file parent directory", fileParentDirPath, "rw-", true);
            if (error != null) {
                out.println("ERROR: " + error.getErrorLogString());
                return;
            }

            Intent intent1 = new Intent(context, StorageActivity.class);
            intent1.putExtra(FILE_EXTRA, filePath);
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent1);
        });
    }

    public static class StorageActivity extends Activity {

        private String outputFile;

        private static final String LOG_TAG = "StorageActivity";

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            Logger.logDebug(LOG_TAG, "onCreate");

            super.onCreate(savedInstanceState);
        }

        @Override
        public void onResume() {
            Logger.logVerbose(LOG_TAG, "onResume");

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
            Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(resultData));

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
                    Logger.logStackTraceWithMessage(LOG_TAG, "Error copying " + data + " to " + outputFile, e);
                }
            }
            finish();
        }

    }

}
