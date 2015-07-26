package com.termux.api;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
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
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;

import com.termux.api.util.TermuxApiLogger;

public class PhotoActivity extends Activity {

	private SurfaceView surfaceView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		surfaceView = new SurfaceView(this);
		setContentView(surfaceView);
	}

	@Override
	protected void onResume() {
		super.onResume();
		takePictureNoPreview();
	}

	static class CompareSizesByArea implements Comparator<Size> {
		@Override
		public int compare(Size lhs, Size rhs) {
			// We cast here to ensure the multiplications won't overflow
			return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
	}

	public void takePictureNoPreview() {
		try {
			String filePath = getIntent().getStringExtra("file");
			final File tmpFile = new File(filePath);
			String cameraId = getIntent().getStringExtra("camera");
			if (cameraId == null) {
				cameraId = "0";
			}

			final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

			TermuxApiLogger.info("cameraId=" + cameraId + ", filePath=" + tmpFile.getAbsolutePath());

			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
			StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

			// For still image captures, we use the largest available size.
			Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
					new CompareSizesByArea());
			final ImageReader mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
					ImageFormat.JPEG, 2);
			mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
				@Override
				public void onImageAvailable(final ImageReader reader) {
					TermuxApiLogger.info("onImageAvailable() from mImageReader");
					new Thread() {
						@Override
						public void run() {
							try (final Image mImage = reader.acquireNextImage()) {
								ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
								byte[] bytes = new byte[buffer.remaining()];
								buffer.get(bytes);
								try (FileOutputStream output = new FileOutputStream(tmpFile)) {
									output.write(bytes);
								} catch (Exception e) {
									TermuxApiLogger.error("Error writing image", e);
								}
							}
						}
					}.start();
				}
			}, null);

			manager.openCamera(cameraId, new CameraDevice.StateCallback() {
				@Override
				public void onOpened(final CameraDevice camera) {
					TermuxApiLogger.info("onOpened() from camera");
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
						switch (getWindowManager().getDefaultDisplay().getRotation()) {
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
								TermuxApiLogger.info("onConfigured() from camera");
								try {
									session.stopRepeating();
									session.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
										@Override
										public void onCaptureCompleted(CameraCaptureSession completedSession,
												CaptureRequest request, TotalCaptureResult result) {
											TermuxApiLogger.info("onCaptureCompleted()");
											camera.close();
											finish();
										}
									}, null);
								} catch (Exception e) {
									TermuxApiLogger.error("onConfigured() error", e);
								}
							}

							@Override
							public void onConfigureFailed(CameraCaptureSession session) {
								TermuxApiLogger.error("onConfigureFailed() error");
							}
						}, null);
					} catch (Exception e) {
						TermuxApiLogger.error("in onOpened", e);
					}
				}

				@Override
				public void onDisconnected(CameraDevice camera) {
					TermuxApiLogger.info("onDisconnected() from camera");
				}

				@Override
				public void onError(CameraDevice camera, int error) {
					TermuxApiLogger.error("Failed opening camera: " + error);
					setResult(1);
					finish();
				}

			}, null);
		} catch (Exception e) {
			TermuxApiLogger.error("Error getting camera", e);
		}
	}

}
