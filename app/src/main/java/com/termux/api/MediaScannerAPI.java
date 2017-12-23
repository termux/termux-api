package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.io.File;
import java.io.PrintWriter;
import java.util.Stack;

public class MediaScannerAPI {

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        final String[] filePaths = intent.getStringArrayExtra("paths");
        final Boolean recursive = intent.getBooleanExtra("recursive", false);
        final Integer[] totalScanned = {0};
        final Boolean verbose = intent.getBooleanExtra("verbose", false);
        for (int i = 0; i < filePaths.length; i++) {
            filePaths[i] = filePaths[i].replace("\\,", ",");
        }

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultWriter() {
            @Override
            public void writeResult(PrintWriter out) {
                scanFiles(out, context, filePaths, totalScanned, verbose);
                if (recursive) scanFilesRecursively(out, context, filePaths, totalScanned, verbose);
                out.println(String.format("Finished scanning %d file(s)", totalScanned[0]));
            }
        });
    }

    private static void scanFiles(PrintWriter out, Context context, String[] filePaths, Integer[] totalScanned, final Boolean verbose) {
        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                filePaths,
                null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        TermuxApiLogger.info("'" + path + "'" + (uri != null ? " -> '" + uri + "'" : ""));
                    }
                });

        if (verbose) for (String path : filePaths) {
                out.println(path);
        }

        totalScanned[0] += filePaths.length;
    }

    private static void scanFilesRecursively(PrintWriter out, Context context, String[] filePaths, Integer[] totalScanned, Boolean verbose) {
        for (String filePath : filePaths) {
            Stack subDirs = new Stack();
            File currentPath = new File(filePath);
            while (currentPath != null && currentPath.isDirectory() && currentPath.canRead()) {
                File[] fileList = null;

                try {
                    fileList = currentPath.listFiles();
                } catch (SecurityException e) {
                    TermuxApiLogger.error(String.format("Failed to open '%s'", currentPath.toString()), e);
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