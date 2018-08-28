package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
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

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

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

public class PhotoAPI {

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        final String filePath = intent.getStringExtra("file");
        final File outputFile = new File(filePath);
        final File outputDir = outputFile.getParentFile();
        final String cameraId = Objects.toString(intent.getStringExtra("camera"), "0");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultWriter() {
            @Override
            public void writeResult(PrintWriter stdout) {
                if (!(outputDir.isDirectory() || outputDir.mkdirs())) {
                    stdout.println("Not a folder (and unable to create it): " + outputDir.getAbsolutePath());
                } else {
                    takePicture(stdout, context, outputFile, cameraId);
                }
            }
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
                        TermuxApiLogger.error("Exception in onOpened()", e);
                        closeCamera(camera, looper);
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    TermuxApiLogger.info("onDisconnected() from camera");
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    TermuxApiLogger.error("Failed opening camera: " + error);
                    closeCamera(camera, looper);
                }
            }, null);

            Looper.loop();
        } catch (Exception e) {
            TermuxApiLogger.error("Error getting camera", e);
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
        Comparator<Size> bySize = new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                // Cast to ensure multiplications won't overflow:
                return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
            }
        };
        List<Size> sizes = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
        Size largest = Collections.max(sizes, bySize);

        final ImageReader mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(final ImageReader reader) {
                new Thread() {
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
                                TermuxApiLogger.error("Error writing image", e);
                            } finally {
                                closeCamera(camera, looper);
                            }
                        }
                    }
                }.start();
            }
        }, null);
        final Surface imageReaderSurface = mImageReader.getSurface();
        outputSurfaces.add(imageReaderSurface);

        camera.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(final CameraCaptureSession session) {
                try {
                    final CaptureRequest.Builder jpegRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    // Render to our image reader:
                    jpegRequest.addTarget(imageReaderSurface);
                    // Configure auto-focus (AF) and auto-exposure (AE) modes:
                    jpegRequest.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    jpegRequest.set(CaptureRequest.CONTROL_AE_MODE, autoExposureModeFinal);
                    jpegRequest.set(CaptureRequest.JPEG_ORIENTATION, correctOrientation(context, characteristics));

                    saveImage(camera, session, jpegRequest.build());
                } catch (Exception e) {
                    TermuxApiLogger.error("onConfigured() error in preview", e);
                    closeCamera(camera, looper);
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                TermuxApiLogger.error("onConfigureFailed() error in preview");
                closeCamera(camera, looper);
            }
        }, null);
    }

    static void saveImage(final CameraDevice camera, CameraCaptureSession session, CaptureRequest request) throws CameraAccessException {
        session.capture(request, new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession completedSession, CaptureRequest request, TotalCaptureResult result) {
                TermuxApiLogger.info("onCaptureCompleted()");
                closeCamera(camera, null);
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
        TermuxApiLogger.info((isFrontFacing ? "Using" : "Not using") + " a front facing camera.");

        Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (sensorOrientation != null) {
            TermuxApiLogger.info(String.format("Sensor orientation: %s degrees", sensorOrientation));
        } else {
            TermuxApiLogger.info("CameraCharacteristics didn't contain SENSOR_ORIENTATION. Assuming 0 degrees.");
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
                TermuxApiLogger.info(
                        String.format("Default display has unknown rotation %d. Assuming 0 degrees.", deviceRotation));
                deviceOrientation = 0;
        }
        TermuxApiLogger.info(String.format("Device orientation: %d degrees", deviceOrientation));

        int jpegOrientation;
        if (isFrontFacing) {
            jpegOrientation = sensorOrientation + deviceOrientation;
        } else {
            jpegOrientation = sensorOrientation - deviceOrientation;
        }
        // Add an extra 360 because (-90 % 360) == -90 and Android won't accept a negative rotation.
        jpegOrientation = (jpegOrientation + 360) % 360;
        TermuxApiLogger.info(String.format("Returning JPEG orientation of %d degrees", jpegOrientation));
        return jpegOrientation;
    }

    static void closeCamera(CameraDevice camera, Looper looper) {
        try {
            camera.close();
        } catch (RuntimeException e) {
            TermuxApiLogger.info("Exception closing camera: " + e.getMessage());
        }
        if (looper != null) looper.quit();
    }

}