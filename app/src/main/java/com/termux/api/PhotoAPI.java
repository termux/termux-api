package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class PhotoAPI {

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        final String filePath = intent.getStringExtra("file");
        final File outputFile = new File(filePath);
        final String cameraId = Objects.toString(intent.getStringExtra("camera"), "0");
        TermuxApiLogger.info("cameraId=" + cameraId + ", filePath=" + outputFile.getAbsolutePath());

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultWriter() {
            @Override
            public void writeResult(PrintWriter out) throws Exception {
                takePictureNoPreview(out, context, outputFile, cameraId);
            }
        });
    }

    private static void takePictureNoPreview(final PrintWriter out, final Context context, final File outputFile, String cameraId) {
        try {
            final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = null;
            try {
                characteristics = manager.getCameraCharacteristics(cameraId);
            } catch (IllegalArgumentException e) {
                out.println("No such camera: " + cameraId);
                return;
            }
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            // For still image captures, we use the largest available size.
            Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    new CompareSizesByArea());
            final ImageReader mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                    ImageFormat.JPEG, 2);
            Looper.prepare();
            final Looper looper = Looper.myLooper();
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
                                    out.append("Error writing image: " + e.getMessage());
                                    TermuxApiLogger.error("Error writing image", e);
                                } finally {
                                    looper.quit();
                                }
                            }
                        }
                    }.start();
                }
            }, null);

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice camera) {
                    try {
                        final CaptureRequest.Builder captureBuilder = camera
                                .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        captureBuilder.addTarget(mImageReader.getSurface());

                        // Use the same AE and AF modes as the preview.
                        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        captureBuilder
                                .set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);

                        // Orientation jpeg fix, from the Camera2BasicFragment example:
                        int cameraJpegOrientation;
                        int deviceOrientation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
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
                        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, cameraJpegOrientation);

                        List<Surface> outputSurfaces = Collections.singletonList(mImageReader.getSurface());
                        camera.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                try {
                                    session.stopRepeating();
                                    session.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                                        @Override
                                        public void onCaptureCompleted(CameraCaptureSession completedSession,
                                                                       CaptureRequest request, TotalCaptureResult result) {
                                            TermuxApiLogger.info("onCaptureCompleted()");
                                            camera.close();
                                        }
                                    }, null);
                                } catch (Exception e) {
                                    out.println("onConfigured() error: " + e.getMessage());
                                    TermuxApiLogger.error("onConfigured() error", e);
                                    looper.quit();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                                TermuxApiLogger.error("onConfigureFailed() error");
                                looper.quit();
                            }
                        }, null);
                    } catch (Exception e) {
                        TermuxApiLogger.error("in onOpened", e);
                        looper.quit();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    TermuxApiLogger.info("onDisconnected() from camera");
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    TermuxApiLogger.error("Failed opening camera: " + error);
                    looper.quit();
                }

            }, null);
            Looper.loop();
        } catch (Exception e) {
            TermuxApiLogger.error("Error getting camera", e);
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

}