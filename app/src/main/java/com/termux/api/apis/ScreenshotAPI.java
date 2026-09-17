package com.termux.api.apis;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.file.TermuxFileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Capture the device display via {@link MediaProjection} and write a PNG.
 * Requires a one-time system consent dialog (not Accessibility / key injection).
 */
public class ScreenshotAPI {

    private static final String LOG_TAG = "ScreenshotAPI";

    private static final String FILE_EXTRA =
        TermuxConstants.TERMUX_API_PACKAGE_NAME + ".screenshot.file";
    private static final String DELAY_EXTRA =
        TermuxConstants.TERMUX_API_PACKAGE_NAME + ".screenshot.delay_ms";

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int DEFAULT_DELAY_MS = 800;
    private static final int CAPTURE_TIMEOUT_MS = 8000;

    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        String fileExtra = intent.getStringExtra("file");
        if (fileExtra == null || fileExtra.isEmpty()) {
            fileExtra = TermuxConstants.TERMUX_HOME_DIR_PATH + "/repos/screen-ocr/latest.png";
        }

        String filePath = TermuxFileUtils.getCanonicalPath(fileExtra, null, true);
        String parentDir = FileUtils.getFileDirname(filePath);
        Error error = TermuxFileUtils.validateDirectoryFileExistenceAndPermissions(
            "screenshot directory", parentDir,
            true, true, true,
            false, true);
        if (error != null) {
            ResultReturner.returnData(apiReceiver, intent,
                out -> out.println("ERROR: " + error.getErrorLogString()));
            return;
        }

        int delayMs = intent.getIntExtra("delay_ms", DEFAULT_DELAY_MS);
        if (delayMs < 0) {
            delayMs = 0;
        }
        if (delayMs > 15000) {
            delayMs = 15000;
        }

        Intent activityIntent = new Intent(context, ScreenshotActivity.class);
        ResultReturner.copyIntentExtras(intent, activityIntent);
        activityIntent.putExtra(FILE_EXTRA, filePath);
        activityIntent.putExtra(DELAY_EXTRA, delayMs);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activityIntent);
    }

    public static class ScreenshotActivity extends Activity {

        private static final String LOG_TAG = "ScreenshotActivity";

        private MediaProjectionManager mProjectionManager;
        private MediaProjection mMediaProjection;
        private VirtualDisplay mVirtualDisplay;
        private ImageReader mImageReader;
        private HandlerThread mCaptureThread;
        private Handler mCaptureHandler;
        private final Handler mMainHandler = new Handler(Looper.getMainLooper());

        private String mOutputFile;
        private int mDelayMs = DEFAULT_DELAY_MS;
        private volatile boolean mResultReturned = false;
        private volatile boolean mCaptureStarted = false;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            Logger.logDebug(LOG_TAG, "onCreate");
            super.onCreate(savedInstanceState);

            Intent intent = getIntent();
            mOutputFile = intent != null ? intent.getStringExtra(FILE_EXTRA) : null;
            if (intent != null) {
                mDelayMs = intent.getIntExtra(DELAY_EXTRA, DEFAULT_DELAY_MS);
            }
            if (mOutputFile == null || mOutputFile.isEmpty()) {
                returnError("Missing output file path");
                return;
            }

            mProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mProjectionManager == null) {
                returnError("MediaProjectionManager unavailable");
                return;
            }

            mCaptureThread = new HandlerThread("termux-screenshot");
            mCaptureThread.start();
            mCaptureHandler = new Handler(mCaptureThread.getLooper());

            startActivityForResult(
                mProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode=" + requestCode
                + " resultCode=" + resultCode + " data=" + IntentUtils.getIntentString(data));
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode != REQUEST_MEDIA_PROJECTION) {
                return;
            }
            if (resultCode != RESULT_OK || data == null) {
                returnError("Screen capture permission denied");
                return;
            }

            try {
                mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
            } catch (Exception e) {
                returnError("getMediaProjection failed: " + e.getMessage());
                return;
            }
            if (mMediaProjection == null) {
                returnError("MediaProjection is null");
                return;
            }

            // Required before createVirtualDisplay on API 34+.
            if (Build.VERSION.SDK_INT >= 34) {
                mMediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Logger.logDebug(LOG_TAG, "MediaProjection stopped");
                        releaseCaptureResources();
                    }
                }, mMainHandler);
            }

            // Let the consent UI dismiss so the captured frame is the prior screen.
            mMainHandler.postDelayed(this::startCapture, mDelayMs);
            mMainHandler.postDelayed(() -> {
                if (!mResultReturned) {
                    returnError("Screenshot timed out");
                }
            }, mDelayMs + CAPTURE_TIMEOUT_MS);
        }

        private void startCapture() {
            if (mCaptureStarted || mResultReturned) {
                return;
            }
            mCaptureStarted = true;

            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) {
                returnError("WindowManager unavailable");
                return;
            }
            Display display = wm.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            Point size = new Point();
            display.getRealSize(size);
            final int width = size.x;
            final int height = size.y;
            final int density = metrics.densityDpi;

            try {
                mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
                mImageReader.setOnImageAvailableListener(reader -> {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image == null) {
                            return;
                        }
                        Bitmap bitmap = imageToBitmap(image, width, height);
                        if (bitmap == null) {
                            returnError("Failed to decode screenshot frame");
                            return;
                        }
                        File out = new File(mOutputFile);
                        File parent = out.getParentFile();
                        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                            returnError("Could not create " + parent);
                            bitmap.recycle();
                            return;
                        }
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                                returnError("PNG compress failed");
                                bitmap.recycle();
                                return;
                            }
                        }
                        bitmap.recycle();
                        returnSuccess(out.getAbsolutePath());
                    } catch (Exception e) {
                        returnError("Capture failed: " + e.getMessage());
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }, mCaptureHandler);

                mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "termux-screenshot",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mImageReader.getSurface(),
                    null,
                    mCaptureHandler);
            } catch (Exception e) {
                returnError("VirtualDisplay failed: " + e.getMessage());
            }
        }

        @Nullable
        private static Bitmap imageToBitmap(Image image, int width, int height) {
            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) {
                return null;
            }
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                bitmap.recycle();
                return cropped;
            }
            return bitmap;
        }

        private void returnSuccess(String path) {
            if (mResultReturned) {
                return;
            }
            mResultReturned = true;
            releaseCaptureResources();
            ResultReturner.returnData(this, getIntent(), out -> out.println(path));
        }

        private void returnError(String message) {
            if (mResultReturned) {
                return;
            }
            mResultReturned = true;
            Logger.logError(LOG_TAG, message);
            releaseCaptureResources();
            ResultReturner.returnData(this, getIntent(),
                out -> out.println("ERROR: " + message));
        }

        private void releaseCaptureResources() {
            try {
                if (mVirtualDisplay != null) {
                    mVirtualDisplay.release();
                    mVirtualDisplay = null;
                }
            } catch (Exception ignored) {
            }
            try {
                if (mImageReader != null) {
                    mImageReader.setOnImageAvailableListener(null, null);
                    mImageReader.close();
                    mImageReader = null;
                }
            } catch (Exception ignored) {
            }
            try {
                if (mMediaProjection != null) {
                    mMediaProjection.stop();
                    mMediaProjection = null;
                }
            } catch (Exception ignored) {
            }
            try {
                if (mCaptureThread != null) {
                    mCaptureThread.quitSafely();
                    mCaptureThread = null;
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        protected void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");
            super.onDestroy();
            if (!mResultReturned) {
                mResultReturned = true;
                ResultReturner.returnData(this, getIntent(),
                    out -> out.println("ERROR: Screenshot activity destroyed"));
            }
            releaseCaptureResources();
        }
    }
}
