package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.graphics.Camera;
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
        final int numPreviews = intent.getIntExtra("previews", 30);

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultWriter() {
            @Override
            public void writeResult(PrintWriter stdout) throws Exception {
                if (numPreviews < 0 || numPreviews > 1000) {
                    stdout.println("Not a sensible number of previews: " + numPreviews);
                } else if (!(outputDir.isDirectory() || outputDir.mkdirs())) {
                    stdout.println("Not a folder (and unable to create it): " + outputDir.getAbsolutePath());
                } else {
                    takePicture(stdout, context, outputFile, cameraId, numPreviews);
                }
            }
        });
    }

    private static void takePicture(final PrintWriter stdout, final Context context, final File outputFile, String cameraId, final int numPreviews) {
        try {
            final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            Looper.prepare();
            final Looper looper = Looper.myLooper();

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice camera) {
                    try {
                        proceedWithOpenedCamera(context, manager, camera, numPreviews, outputFile, looper, stdout);
                    } catch (Exception e) {
                        TermuxApiLogger.error("in onOpened", e);
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
    static void proceedWithOpenedCamera(final Context context, final CameraManager manager, final CameraDevice camera, final int previews, final File outputFile, final Looper looper, final PrintWriter stdout) throws CameraAccessException, IllegalArgumentException {
        final List<Surface> outputSurfaces = new ArrayList<>();

        // Use largest available size:
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera.getId());
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                // Cast to ensure multiplications won't overflow:
                return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
            }
        });

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

        if (previews > 0) {
            final SurfaceTexture dummyPreview = new SurfaceTexture(1);
            final Surface dummySurface = new Surface(dummyPreview);
            outputSurfaces.add(dummySurface);
        }

        camera.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(final CameraCaptureSession session) {
                try {
                    final CaptureRequest.Builder jpegRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    // Render to our image reader:
                    jpegRequest.addTarget(imageReaderSurface);
                    // Configure auto-focus (AF) and auto-exposure (AE) modes:
                    jpegRequest.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    jpegRequest.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    // Orientation jpeg fix, from the Camera2BasicFragment example:
                    fixOrientation(context, jpegRequest);

                    if (previews == 0) {
                        saveImage(camera, session, jpegRequest.build());
                    } else {
                        // Take previews to dummySurface to allow camera to warm up before saving jpeg:
                        final CaptureRequest.Builder captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        // Render to our dummy preview surface:
                        captureBuilder.addTarget(outputSurfaces.get(1));
                        // Configure auto-focus (AF) and auto-exposure (AE) modes:
                        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        // Orientation jpeg fix, from the Camera2BasicFragment example:
                        fixOrientation(context, captureBuilder);

                        session.setRepeatingRequest(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            int mNumCaptures;

                            @Override
                            public void onCaptureCompleted(CameraCaptureSession completedSession, CaptureRequest request, TotalCaptureResult result) {
                                if (++mNumCaptures == previews) {
                                    try {
                                        completedSession.stopRepeating();
                                        saveImage(camera, session, jpegRequest.build());
                                    } catch (CameraAccessException e) {
                                        TermuxApiLogger.error("Error saving image", e);
                                        closeCamera(camera, looper);
                                    }
                                }
                            }
                        }, null);
                    }
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

    /** Orientation jpeg fix, from the Camera2BasicFragment example. */
    static void fixOrientation(Context context, CaptureRequest.Builder request) {
        int cameraJpegOrientation;
        final int deviceOrientation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        switch (deviceOrientation) {
            case Surface.ROTATION_0:
                cameraJpegOrientation = 90;
                break;
            case Surface.ROTATION_90:
                cameraJpegOrientation = 0;
                break;
            case Surface.ROTATION_180:
                cameraJpegOrientation = 270;
                break;
            case Surface.ROTATION_270:
                cameraJpegOrientation = 180;
                break;
            default:
                cameraJpegOrientation = 0;
        }
        request.set(CaptureRequest.JPEG_ORIENTATION, cameraJpegOrientation);
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