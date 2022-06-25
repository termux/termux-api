package com.termux.api.apis;

import android.app.Service;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
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
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        protected static final Pattern CROP_PATTERN = Pattern.compile(
                "^(\\d+)?" // width
                        + "(?:([x:])(\\d+))?" // height
                        + "(?:\\+(\\d+)\\+(\\d+))?" // offset
                        + "(%?)$" // relative
        );
        protected enum CROP_GROUPS { // in accordance with above arrangement
            WIDTH,
            SEPARATOR, HEIGHT,
            XOFFSET, YOFFSET,
            PERCENT
        }

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
                    int which = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
                    if (intent.hasExtra("lockscreen")) {
                        which = intent.getBooleanExtra("lockscreen", false) ? WallpaperManager.FLAG_LOCK : WallpaperManager.FLAG_SYSTEM;
                    }
                    Rect crop = null;
                    try {
                        crop = calculateWallpaperCrop(result.wallpaper, intent.getStringExtra("crop"));
                    }  catch (IllegalArgumentException e) {
                        result.error = "-c ignored: " + e.getMessage();
                    }
                    wallpaperManager.setBitmap(result.wallpaper, crop, true, which);
                    result.message = "Wallpaper set successfully!";
                } catch (IOException | IllegalArgumentException e) {
                    result.error = "Error setting wallpaper: " + e.getMessage();
                }
            }
            postWallpaperResult(context, intent, result);
        }

        private Rect calculateWallpaperCrop(Bitmap wallpaper, String cropString) throws IllegalArgumentException {
            if (cropString == null) {
                return null;
            }
            Matcher cropMatch = CROP_PATTERN.matcher(cropString);
            if (cropMatch.matches()) {
                MatchResult cropResult = cropMatch.toMatchResult();
                int oldWidth = wallpaper.getWidth();
                int oldHeight = wallpaper.getHeight();
                double oldRatio = (double) oldWidth / (double) oldHeight;
                Rect crop = new Rect();
                String w = cropResult.group(CROP_GROUPS.WIDTH.ordinal());
                String sep = cropResult.group(CROP_GROUPS.SEPARATOR.ordinal());
                String h = cropResult.group(CROP_GROUPS.HEIGHT.ordinal());
                String x = cropResult.group(CROP_GROUPS.XOFFSET.ordinal());
                String y = cropResult.group(CROP_GROUPS.YOFFSET.ordinal());
                boolean relative = "%".equals(cropResult.group(CROP_GROUPS.PERCENT.ordinal()));
                if (":".equals(sep)) {
                    if (w == null) {
                        throw new IllegalArgumentException("Aspect ratio needs both width and height!");
                    }
                    int width = Integer.parseInt(w);
                    int height = Integer.parseInt(h);
                    double ratio = (double) width / (double) height;
                    if (ratio > oldRatio) {
                        crop.bottom = (int) ((double) oldWidth / ratio);
                        crop.right = oldWidth;
                    } else {
                        crop.bottom = oldHeight;
                        crop.right = (int) ((double) oldHeight * ratio);
                    }
                } else if ("x".equals(sep) || w != null) {
                    double width = 0;
                    double height = 0;
                    if (w != null) {
                        width = Double.parseDouble(w);
                        height = width / oldRatio;
                    }
                    if (h != null) {
                        height = Double.parseDouble(h);
                        if (w == null) {
                            width = height * oldRatio;
                        }
                    }
                    if (relative) {
                        crop.right = (int) (oldWidth * width / 100);
                        crop.bottom = (int) (oldHeight * height / 100);
                    } else {
                        crop.right = (int) width;
                        crop.bottom = (int) height;
                    }
                } else {
                    throw new IllegalArgumentException("No size or aspect ratio given!");
                }
                if (x != null) {
                    double xOff = Double.parseDouble(x);
                    double yOff = Double.parseDouble(y);
                    if (relative) {
                        xOff = (oldWidth-crop.width()) * xOff / 100;
                        yOff = (oldHeight-crop.height()) * yOff / 100;
                    }
                    crop.offset((int) xOff, (int) yOff);
                } else {
                    crop.offset((oldWidth - crop.width())/2, (oldHeight - crop.height())/2);
                }
                return crop;
            } else {
                throw new IllegalArgumentException("Cannot parse crop geometry!");
            }
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

