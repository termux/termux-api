package com.termux.api.apis;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class InstallAPI {

    private static final String LOG_TAG = "InstallAPI";

    /** Action used for the internal commit-status callback broadcast. */
    private static final String INSTALL_COMMIT_ACTION = "com.termux.api.INSTALL_COMMIT_RESULT";

    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final String fileExtra = intent.getStringExtra("file");

        ResultReturner.returnData(apiReceiver, intent, out -> {
            if (fileExtra == null || fileExtra.isEmpty()) {
                out.println("ERROR: No file specified (pass the path with --es file <path>)");
                return;
            }

            File input = new File(fileExtra);
            if (!input.exists() || !input.canRead()) {
                out.println("ERROR: Not a readable path: '" + input.getAbsolutePath() + "'");
                return;
            }

            List<File> apks = new ArrayList<>();
            File tempDir = null;
            try {
                if (input.isDirectory()) {
                    collectApks(input, apks);
                } else if (isApk(input)) {
                    apks.add(input);
                } else {
                    // Treat anything else as a zip based bundle (.apkm/.apks/.xapk/.zip).
                    tempDir = extractApks(context, input, apks);
                }

                if (apks.isEmpty()) {
                    out.println("ERROR: No .apk files found in '" + input.getName() + "'");
                    return;
                }

                int sessionId = createAndCommitSession(context, apks);
                out.println("Install session " + sessionId + " committed with " + apks.size()
                        + " APK(s). Confirm the system prompt to finish installation.");
            } catch (IOException e) {
                out.println("ERROR: " + e.getMessage());
                Logger.logStackTraceWithMessage(LOG_TAG, "Install failed", e);
            } finally {
                // The session keeps its own copy of the bytes after openWrite()/fsync(), so the
                // extracted temp files are no longer needed once the session has been committed.
                if (tempDir != null) deleteRecursive(tempDir);
            }
        });
    }

    private static boolean isApk(File file) {
        return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    private static void collectApks(File dir, List<File> apks) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (isApk(file)) apks.add(file);
        }
    }

    private static File extractApks(Context context, File bundle, List<File> apks) throws IOException {
        File tempDir = new File(context.getCacheDir(), "install_" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new IOException("Could not create temporary directory for extraction");
        }

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(bundle)))) {
            ZipEntry entry;
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                // Use only the basename to guard against zip path traversal ("zip slip").
                String name = new File(entry.getName()).getName();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;

                File outFile = new File(tempDir, name);
                try (OutputStream os = new FileOutputStream(outFile)) {
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }
                apks.add(outFile);
            }
        }
        return tempDir;
    }

    private static int createAndCommitSession(Context context, List<File> apks) throws IOException {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);
        try {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            for (File apk : apks) {
                try (InputStream in = new FileInputStream(apk);
                     OutputStream out = session.openWrite(apk.getName(), 0, apk.length())) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    session.fsync(out);
                }
            }
            session.commit(createStatusSender(context, sessionId));
        } finally {
            session.close();
        }
        return sessionId;
    }

    private static IntentSender createStatusSender(Context context, int sessionId) {
        Intent intent = new Intent(context, InstallStatusReceiver.class).setAction(INSTALL_COMMIT_ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The system fills in the result extras, so the PendingIntent must be mutable on 31+.
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags);
        return pendingIntent.getIntentSender();
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        if (!file.delete()) {
            Logger.logWarn(LOG_TAG, "Could not delete temporary file: " + file.getAbsolutePath());
        }
    }

    /**
     * Receives the commit status. When the system needs the user to confirm the install it returns
     * {@link PackageInstaller#STATUS_PENDING_USER_ACTION} together with the confirmation intent,
     * which must be launched as an activity; the success/failure result arrives in a later callback.
     */
    public static class InstallStatusReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmIntent != null) {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            context.startActivity(confirmIntent);
                        } catch (Exception e) {
                            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to launch install confirmation", e);
                        }
                    } else {
                        Logger.logError(LOG_TAG, "Pending user action with no confirmation intent");
                    }
                    break;
                case PackageInstaller.STATUS_SUCCESS:
                    String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
                    Logger.logInfo(LOG_TAG, "Installed " + packageName);
                    Toast.makeText(context, "Installed " + (packageName != null ? packageName : "package"),
                            Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Logger.logError(LOG_TAG, "Install failed, status=" + status + ", message=" + message);
                    Toast.makeText(context, "Install failed: " + (message != null ? message : "status " + status),
                            Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

}
