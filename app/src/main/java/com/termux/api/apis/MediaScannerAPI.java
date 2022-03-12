package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Stack;

public class MediaScannerAPI {

    private static final String LOG_TAG = "MediaScannerAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final String[] filePaths = intent.getStringArrayExtra("paths");
        final boolean recursive = intent.getBooleanExtra("recursive", false);
        final Integer[] totalScanned = {0};
        final boolean verbose = intent.getBooleanExtra("verbose", false);
        for (int i = 0; i < filePaths.length; i++) {
            filePaths[i] = filePaths[i].replace("\\,", ",");
        }

        ResultReturner.returnData(apiReceiver, intent, out -> {
            scanFiles(out, context, filePaths, totalScanned, verbose);
            if (recursive) scanFilesRecursively(out, context, filePaths, totalScanned, verbose);
            out.println(String.format(Locale.ENGLISH, "Finished scanning %d file(s)", totalScanned[0]));
        });
    }

    private static void scanFiles(PrintWriter out, Context context, String[] filePaths, Integer[] totalScanned, final Boolean verbose) {
        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                filePaths,
                null,
                (path, uri) -> Logger.logInfo(LOG_TAG, "'" + path + "'" + (uri != null ? " -> '" + uri + "'" : "")));

        if (verbose) for (String path : filePaths) {
                out.println(path);
        }

        totalScanned[0] += filePaths.length;
    }

    private static void scanFilesRecursively(PrintWriter out, Context context, String[] filePaths, Integer[] totalScanned, Boolean verbose) {
        for (String filePath : filePaths) {
            Stack<File> subDirs = new Stack<>();
            File currentPath = new File(filePath);
            while (currentPath != null && currentPath.isDirectory() && currentPath.canRead()) {
                File[] fileList = null;

                try {
                    fileList = currentPath.listFiles();
                } catch (SecurityException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, String.format("Failed to open '%s'", currentPath.toString()), e);
                }

                if (fileList != null && fileList.length > 0) {
                    String[] filesToScan = new String[fileList.length];
                    for (int i = 0; i < fileList.length; i++) {
                        filesToScan[i] = fileList[i].toString();
                        if (fileList[i].isDirectory()) subDirs.push(fileList[i]);
                    }
                    scanFiles(out, context, filesToScan, totalScanned, verbose);
                }

                if (!subDirs.isEmpty()) {
                    currentPath = new File(subDirs.pop().toString());
                } else {
                    currentPath = null;
                }
            }
        }
    }
}