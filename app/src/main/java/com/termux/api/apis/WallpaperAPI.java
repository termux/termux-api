package com.termux.api.apis;

import android.app.Service;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WallpaperAPI {

    private static final String LOG_TAG = "WallpaperAPI";

    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        Intent wallpaperService = new Intent(context, WallpaperService.class);
        wallpaperService.putExtras(intent.getExtras());
        context.startService(wallpaperService);
    }


    /**
     * Wallpaper setting functionality via file or downloading exists in
     * this background service
     */
    public static class WallpaperService extends Service {
        protected static final int DOWNLOAD_TIMEOUT = 30;

        private static final String LOG_TAG = "WallpaperService";

        public int onStartCommand(Intent intent, int flags, int startId) {
            Logger.logDebug(LOG_TAG, "onStartCommand");

            if (intent.hasExtra("file")) {
                getWallpaperFromFile(intent);
            } else if (intent.hasExtra("url")) {
                getWallpaperFromUrl(intent);
            } else {
                WallpaperResult result = new WallpaperResult();
                result.error = "No args supplied for WallpaperAPI!";
                postWallpaperResult(getApplicationContext(), intent, result);
            }

            return Service.START_NOT_STICKY;
        }

        protected void getWallpaperFromFile(final Intent intent) {
            WallpaperResult wallpaperResult = new WallpaperResult();
            String file = intent.getStringExtra("file");
            wallpaperResult.wallpaper = BitmapFactory.decodeFile(file);
            if (wallpaperResult.wallpaper == null) {
                wallpaperResult.error = "Error: Invalid image file!";
            }
            onWallpaperResult(intent, wallpaperResult);
        }

        protected void getWallpaperFromUrl(final Intent intent) {
            final String url = intent.getStringExtra("url");
            Future<WallpaperResult> wallpaperDownload = getWallpaperDownloader(url);

            WallpaperResult result = new WallpaperResult();

            try {
                result = wallpaperDownload.get(DOWNLOAD_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Logger.logInfo(LOG_TAG, "Wallpaper download interrupted");
            } catch (ExecutionException e) {
                result.error = "Unknown host!";
            } catch (TimeoutException e) {
                result.error = "Connection timed out!";
            } finally {
                onWallpaperResult(intent, result);
            }
        }

        protected Future<WallpaperResult> getWallpaperDownloader(final String url) {
            return Executors.newSingleThreadExecutor().submit(() -> {
                WallpaperResult wallpaperResult = new WallpaperResult();
                String contentUrl = url;

                if (!contentUrl.startsWith("http://") && !contentUrl.startsWith("https://")) {
                    contentUrl = "http://" + url;
                }
                HttpURLConnection connection = (HttpURLConnection) new URL(contentUrl).openConnection();
                connection.connect();

                String contentType = "" + connection.getHeaderField("Content-Type");

                // prevent downloading invalid resource
                if (!contentType.startsWith("image/")) {
                    wallpaperResult.error = "Invalid mime type! Must be an image resource!";
                } else {
                    InputStream inputStream = connection.getInputStream();
                    wallpaperResult.wallpaper = BitmapFactory.decodeStream(inputStream);

                    if (inputStream != null) {
                        inputStream.close();
                    }
                }
                return wallpaperResult;
            });
        }

        protected void onWallpaperResult(final Intent intent, WallpaperResult result) {
            Context context = getApplicationContext();
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);

            if (result.wallpaper != null) {
                try {
                    int flag = intent.hasExtra("lockscreen") ? WallpaperManager.FLAG_LOCK : WallpaperManager.FLAG_SYSTEM;
                    wallpaperManager.setBitmap(result.wallpaper, null, true, flag);
                    result.message = "Wallpaper set successfully!";
                } catch (IOException e) {
                    result.error = "Error setting wallpaper: " + e.getMessage();
                }
            }
            postWallpaperResult(context, intent, result);
        }

        protected void postWallpaperResult(final Context context, final Intent intent, final WallpaperResult result) {
            ResultReturner.returnData(context, intent, out -> {
                out.append(result.message).append("\n");
                if (result.error != null) {
                    out.append(result.error).append("\n");
                }
                out.flush();
                out.close();
            });
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    /**
     * POJO to store wallpaper result info
     */
    static class WallpaperResult {
        public String message = "";
        public String error;
        public Bitmap wallpaper;
    }
}

