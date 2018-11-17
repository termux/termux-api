package com.termux.api;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultWriter;

import org.json.JSONObject;

import java.io.PrintWriter;

public class DownloadAPI {

    static void onReceive(final Context context, final JSONObject opts) {
        ResultReturner.returnData(context, new ResultWriter() {
            @Override
            public void writeResult(PrintWriter out) {
                final Uri downloadUri = Uri.parse(opts.optString("url"));
                if (downloadUri == null) {
                    out.println("No download URI specified");
                    return;
                }

                String title = opts.optString("title");
                String description = opts.optString("description");

                DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                Request req = new Request(downloadUri);
                req.setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setVisibleInDownloadsUi(true);

                if (title != null)
                    req.setTitle(title);

                if (description != null)
                    req.setDescription(description);

                manager.enqueue(req);
            }
        });
    }
}
