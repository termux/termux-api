package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.file.TermuxFileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CameraPhotoAPI {

    private static final String LOG_TAG = "CameraPhotoAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final String filePath = intent.getStringExtra("file");
        final String cameraId = Objects.toString(intent.getStringExtra("camera"), "0");

        ResultReturner.returnData(apiReceiver, intent, stdout -> {
            if (filePath == null || filePath.isEmpty()) {
                stdout.println("ERROR: " + "File path not passed");
                return;
            }

            // Get canonical path of photoFilePath
            String photoFilePath = TermuxFileUtils.getCanonicalPath(filePath, null, true);
            String photoDirPath = FileUtils.getFileDirname(photoFilePath);
            Logger.logVerbose(LOG_TAG, "photoFilePath=\"" + photoFilePath + "\", photoDirPath=\"" + photoDirPath + "\"");

            // If workingDirectory is not a directory, or is not readable or writable, then just return
            // Creation of missing directory and setting of read, write and execute permissions are only done if workingDirectory is
            // under allowed termux working directory paths.
            // We try to set execute permissions, but ignore if they are missing, since only read and write permissions are required
            // for working directories.
            Error error = TermuxFileUtils.validateDirectoryFileExistenceAndPermissions("photo directory", photoDirPath,
                    true, true, true,
                    false, true);
            if (error != null) {
                stdout.println("ERROR: " + error.getErrorLogString());
                return;
            }

            takePicture(stdout, context, new File(photoFilePath), cameraId);
        });
    }

    private static void takePicture(final PrintWriter stdout, final Context context, final File outputFile, String cameraId) {
        try {
            final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            Looper.prepare();
            final Looper looper = Looper.myLooper();

            //noinspection MissingPermission
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice camera) {
                    try {
                        proceedWithOpenedCamera(context, manager, camera, outputFile, looper, stdout);
                    } catch (Exception e) {
                        Logger.logStackTraceWithMessage(LOG_TAG, "Exception in onOpened()", e);
                        closeCamera(camera, looper);
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    Logger.logInfo(LOG_TAG, "onDisconnected() from camera");
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Logger.logError(LOG_TAG, "Failed opening camera: " + error);
                    closeCamera(camera, looper);
                }
            }, null);

            Looper.loop();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error getting camera", e);
        }
    }

    // See answer on http://stackoverflow.com/questions/31925769/pictures-with-camera2-api-are-really-dark
    // See https://developer.android.com/reference/android/hardware/camera2/CameraDevice.html#createCaptureSession(java.util.List<android.view.Surface>, android.hardware.camera2.CameraCaptureSession.StateCallback, android.os.Handler)
    // for information about guaranteed support for output sizes and formats.
    static void proceedWithOpenedCamera(final Context context, final CameraManager manager, final CameraDevice camera, final File outputFile, final Looper looper, final PrintWriter stdout) throws CameraAccessException, IllegalArgumentException {
        final List<Surface> outputSurfaces = new ArrayList<>();

        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera.getId());

        int autoExposureMode = CameraMetadata.CONTROL_AE_MODE_OFF;
        for (int supportedMode : characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)) {
            if (supportedMode == CameraMetadata.CONTROL_AE_MODE_ON) {
                autoExposureMode = supportedMode;
            }
        }
        final int autoExposureModeFinal = autoExposureMode;

        // Use largest available size:
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Comparator<Size> bySize = (lhs, rhs) -> {
            // Cast to ensure multiplications won't overflow:
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        };
        List<Size> sizes = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
        Size largest = Collections.max(sizes, bySize);

        final ImageReader mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(reader -> new Thread() {
            @Override
            public void run() {
                try (final Image mImage = reader.acquireNextImage()) {
                    ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    try (FileOutputStream output = new FileOutputStream(outputFile)) {
                        output.write(bytes);
                    } catch (Exception e) {
                        stdout.println("Error writing image: " + e.getMessage());
                        Logger.logStackTraceWithMessage(LOG_TAG, "Error writing image", e);
                    }
                } finally {
                    mImageReader.close();
                    releaseSurfaces(outputSurfaces);
                    closeCamera(camera, looper);
                }
            }
        }.start(), null);
        final Surface imageReaderSurface = mImageReader.getSurface();
        outputSurfaces.add(imageReaderSurface);

        // create a dummy PreviewSurface
        SurfaceTexture previewTexture = new SurfaceTexture(1);
        Surface dummySurface = new Surface(previewTexture);
        outputSurfaces.add(dummySurface);

        camera.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(final CameraCaptureSession session) {
                try {
                    // create preview Request
                    CaptureRequest.Builder previewReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    previewReq.addTarget(dummySurface);
                    previewReq.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    previewReq.set(CaptureRequest.CONTROL_AE_MODE, autoExposureModeFinal);

                    // continous preview-capture for 1/2 second
                    session.setRepeatingRequest(previewReq.build(), null, null);
                    Logger.logInfo(LOG_TAG, "preview started");
                    Thread.sleep(500);
                    session.stopRepeating();
                    Logger.logInfo(LOG_TAG, "preview stoppend");

                    final CaptureRequest.Builder jpegRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    // Render to our image reader:
                    jpegRequest.addTarget(imageReaderSurface);
                    // Configure auto-focus (AF) and auto-exposure (AE) modes:
                    jpegRequest.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    jpegRequest.set(CaptureRequest.CONTROL_AE_MODE, autoExposureModeFinal);
                    jpegRequest.set(CaptureRequest.JPEG_ORIENTATION, correctOrientation(context, characteristics));

                    saveImage(camera, session, jpegRequest.build());
                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "onConfigured() error in preview", e);
                    mImageReader.close();
                    releaseSurfaces(outputSurfaces);
                    closeCamera(camera, looper);
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                Logger.logError(LOG_TAG, "onConfigureFailed() error in preview");
                mImageReader.close();
                releaseSurfaces(outputSurfaces);
                closeCamera(camera, looper);
            }
        }, null);
    }

    static void saveImage(final CameraDevice camera, CameraCaptureSession session, CaptureRequest request) throws CameraAccessException {
        session.capture(request, new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession completedSession, CaptureRequest request, TotalCaptureResult result) {
                Logger.logInfo(LOG_TAG, "onCaptureCompleted()");
            }
        }, null);
    }

    /**
     * Determine the correct JPEG orientation, taking into account device and sensor orientations.
     * See https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
     */
    static int correctOrientation(final Context context, final CameraCharacteristics characteristics) {
        final Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        final boolean isFrontFacing = lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT;
        Logger.logInfo(LOG_TAG, (isFrontFacing ? "Using" : "Not using") + " a front facing camera.");

        Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (sensorOrientation != null) {
            Logger.logInfo(LOG_TAG, String.format("Sensor orientation: %s degrees", sensorOrientation));
        } else {
            Logger.logInfo(LOG_TAG, "CameraCharacteristics didn't contain SENSOR_ORIENTATION. Assuming 0 degrees.");
            sensorOrientation = 0;
        }

        int deviceOrientation;
        final int deviceRotation =
                ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        switch (deviceRotation) {
            case Surface.ROTATION_0:
                deviceOrientation = 0;
                break;
            case Surface.ROTATION_90:
                deviceOrientation = 90;
                break;
            case Surface.ROTATION_180:
                deviceOrientation = 180;
                break;
            case Surface.ROTATION_270:
                deviceOrientation = 270;
                break;
            default:
                Logger.logInfo(LOG_TAG,
                        String.format("Default display has unknown rotation %d. Assuming 0 degrees.", deviceRotation));
                deviceOrientation = 0;
        }
        Logger.logInfo(LOG_TAG, String.format("Device orientation: %d degrees", deviceOrientation));

        int jpegOrientation;
        if (isFrontFacing) {
            jpegOrientation = sensorOrientation + deviceOrientation;
        } else {
            jpegOrientation = sensorOrientation - deviceOrientation;
        }
        // Add an extra 360 because (-90 % 360) == -90 and Android won't accept a negative rotation.
        jpegOrientation = (jpegOrientation + 360) % 360;
        Logger.logInfo(LOG_TAG, String.format("Returning JPEG orientation of %d degrees", jpegOrientation));
        return jpegOrientation;
    }

    static void releaseSurfaces(List<Surface> outputSurfaces) {
        for (Surface outputSurface : outputSurfaces) {
            outputSurface.release();
        }
        Logger.logInfo(LOG_TAG, "surfaces released");
    }

    static void closeCamera(CameraDevice camera, Looper looper) {
        try {
            camera.close();
        } catch (RuntimeException e) {
            Logger.logInfo(LOG_TAG, "Exception closing camera: " + e.getMessage());
        }
        if (looper != null) looper.quit();
    }

}