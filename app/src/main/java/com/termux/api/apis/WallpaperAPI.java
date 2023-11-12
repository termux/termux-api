package com.termux.api.apis;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.annotation.NonNull;
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

    public static void onReceive(final Context context, @NonNull final Intent intent) {
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
            Bitmap originalImage = result.wallpaper;
            Context context = getApplicationContext();
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            int flag = intent.hasExtra("lockscreen") ? WallpaperManager.FLAG_LOCK : WallpaperManager.FLAG_SYSTEM;
            if (originalImage != null) {
                if (intent.hasExtra("nocrop") || intent.hasExtra("center")) {
                    Bitmap resizedImage = resizeImage(intent, originalImage);
                    try {
                        wallpaperManager.setBitmap(resizedImage, null, true, flag);
                    }
                    catch(IOException e) {
                        result.error = "Error setting wallpaper: " + e.getMessage();
                    }
                }
                else {
                    try {
                        wallpaperManager.setBitmap(originalImage, null, true, flag);
                    }
                    catch(IOException e) {
                        result.error = "Error setting wallpaper: " + e.getMessage();
                    }
                }
                result.message = "Wallpaper set successfully!";
                }
            postWallpaperResult(context, intent, result);
        }

        private Bitmap resizeImage(final Intent intent, Bitmap originalImage) {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            int height = metrics.heightPixels;
            int width = metrics.widthPixels;
            Bitmap newImage = Bitmap.createBitmap(width, height, originalImage.getConfig());
            Canvas canvas = new Canvas(newImage);
            float imageWidth = originalImage.getWidth();
            float imageHeight = originalImage.getHeight();
            float scaleX = width / imageWidth;
            float scaleY = height / imageHeight;
            if (intent.hasExtra("center")) {
                float scale = Math.max(scaleY, scaleX);
                float scaledWidth = scale * imageWidth;
                float scaledHeight = scale * imageHeight;
                float left = (width - scaledWidth) / 2;
                float top = (height - scaledHeight) / 2;
                RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
                Paint paint = new Paint();
                paint.setFilterBitmap(true);
                paint.setAntiAlias(true);
                canvas.drawBitmap(originalImage, null, targetRect, paint);
            } else {
                float scale = Math.min(scaleY, scaleX);
                float xTranslation = (width - imageWidth * scale) / 2.0f;
                float yTranslation = (height - imageHeight * scale) / 2.0f;
                Matrix matrix = new Matrix();
                matrix.postTranslate(xTranslation, yTranslation);
                matrix.preScale(scale, scale);
                Paint paint = new Paint();
                paint.setFilterBitmap(true);
                paint.setAntiAlias(true);
                canvas.drawBitmap(originalImage, matrix, paint);
            }
            return (newImage);
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

