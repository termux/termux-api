package com.termux.api.apis;

import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH;
import static android.hardware.camera2.CameraMetadata.FLASH_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.FLASH_MODE_SINGLE;
import static java.lang.Float.parseFloat;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Looper;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CameraPhotoAPI {

    private static final String LOG_TAG = "CameraPhotoAPI";

    private static float focus_distance=0;
    private static Integer iso=0;
    private static Integer exposure=0;
    private static Integer ev_steps=0;
    private static String flash;

    private static Integer preview_time;

    private static String no_processing;

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US);

        final String filePath = Objects.toString(intent.getStringExtra("file"), "/data/data/com.termux/files/home/Photos/" + sdf.format(new Date()) + ".jpg");
        final String cameraId = Objects.toString(intent.getStringExtra("camera"), "0");
        flash = Objects.toString(intent.getStringExtra("flash"), "off");
        no_processing = Objects.toString(intent.getStringExtra("no_processing"), "off");
        iso=Integer.parseInt(Objects.toString(intent.getStringExtra("iso"), "0"));
        exposure=Integer.parseInt(Objects.toString(intent.getStringExtra("exposure"), "0"));
        ev_steps=Integer.parseInt(Objects.toString(intent.getStringExtra("ev_steps"), "0"));
        preview_time=Integer.parseInt(Objects.toString(intent.getStringExtra("preview_time"), "500"));

        focus_distance=parseFloat(Objects.toString(intent.getStringExtra("focus"), "0"));

        ResultReturner.returnData(apiReceiver, intent, stdout -> {
            if (filePath == null || filePath.isEmpty()) {

                stdout.println("ERROR: " + "File path not passed");
                return;
            }


            stdout.println("ISO: " + iso);
            stdout.println("Exposure: " + exposure);
            stdout.println("Focus: " + focus_distance);
            stdout.println("EV steps: " + ev_steps);
            stdout.println("File path: " + filePath);
            stdout.println("Flash: " + flash);


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
                stdout.println("Photo dir path: " + photoDirPath);
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

        Rect sensor_size;
        sensor_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        stdout.println("Resolution is " + sensor_size.width() + "x" + sensor_size.height());

        final ImageReader mImageReader = ImageReader.newInstance(sensor_size.width(), sensor_size.height(), ImageFormat.JPEG, 2);
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
                    //no need for a preview if we don't need auto focus/exposure
                    if(preview_time>0)
                    {
                        // create preview Request
                        CaptureRequest.Builder previewReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewReq.addTarget(dummySurface);
                        //previewReq.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                        if(focus_distance<0.01f)
                            previewReq.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        else
                        {
                            float focus_diopters = 1000.0f / focus_distance;
                            previewReq.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                            previewReq.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus_diopters);
                        }

                        if(flash.equals("off"))
                            previewReq.set(CaptureRequest.FLASH_MODE, FLASH_MODE_OFF);
                        else
                        if(flash.equals("on"))
                            previewReq.set(CaptureRequest.FLASH_MODE, FLASH_MODE_SINGLE);

                        if(flash.equals("auto"))
                            previewReq.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_ON_AUTO_FLASH);
                        else
                            previewReq.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_ON);

                        if(ev_steps!=0)
                        {
                            previewReq.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev_steps);
                        }

                        // continous preview-capture for 1/2 second
                        session.setRepeatingRequest(previewReq.build(), null, null);
                        Logger.logInfo(LOG_TAG, "preview started");
                        Thread.sleep(preview_time);
                        session.stopRepeating();
                        Logger.logInfo(LOG_TAG, "preview stoppend");
                    }

                    final CaptureRequest.Builder jpegRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    // Render to our image reader:
                    jpegRequest.addTarget(imageReaderSurface);
                    // Configure auto-focus (AF) and auto-exposure (AE) modes:

                    if(focus_distance<0.01f)
                    jpegRequest.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    else
                    {
                        float focus_diopters = 1000.0f / focus_distance;
                        jpegRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                        jpegRequest.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus_diopters);
                    }

                    if(flash.equals("off"))
                        jpegRequest.set(CaptureRequest.FLASH_MODE, FLASH_MODE_OFF);
                    else
                    if(flash.equals("on"))
                        jpegRequest.set(CaptureRequest.FLASH_MODE, FLASH_MODE_SINGLE);

                    if(iso!=0 && exposure!=0)
                    {
                        long forced_exposure=exposure*1000;
                        jpegRequest.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
                        jpegRequest.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                        jpegRequest.set(CaptureRequest.SENSOR_EXPOSURE_TIME, forced_exposure);
                    }
                    else
                    {
                        if(flash.equals("auto"))
                            jpegRequest.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_ON_AUTO_FLASH);
                        else
                            jpegRequest.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_ON);
                    }

                    jpegRequest.set(CaptureRequest.JPEG_ORIENTATION, correctOrientation(context, characteristics));

                    if(no_processing.equals("on"))
                    {
                        jpegRequest.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
                        jpegRequest.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            jpegRequest.set(CaptureRequest.DISTORTION_CORRECTION_MODE, CaptureRequest.DISTORTION_CORRECTION_MODE_OFF);
                        }
                    }

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
