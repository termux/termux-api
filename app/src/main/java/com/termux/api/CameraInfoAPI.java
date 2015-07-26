package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.util.JsonWriter;
import android.util.SizeF;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;

public class CameraInfoAPI {

	static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
		ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
			@Override
			public void writeJson(JsonWriter out) throws Exception {
				final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

				out.beginArray();
				for (String cameraId : manager.getCameraIdList()) {
					out.beginObject();
					out.name("id").value(cameraId);

					CameraCharacteristics camera = manager.getCameraCharacteristics(cameraId);

					out.name("facing");
					int lensFacing = camera.get(CameraCharacteristics.LENS_FACING);
					switch (lensFacing) {
					case CameraMetadata.LENS_FACING_FRONT:
						out.value("front");
						break;
					case CameraMetadata.LENS_FACING_BACK:
						out.value("back");
						break;
					default:
						out.value(lensFacing);
					}

					out.name("focal_lengths").beginArray();
					for (float f : camera.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS))
						out.value(f);
					out.endArray();

					out.name("auto_exposure_modes").beginArray();
					int[] flashModeValues = camera.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
					for (int flashMode : flashModeValues) {
						switch (flashMode) {
						case CameraMetadata.CONTROL_AE_MODE_OFF:
							out.value("CONTROL_AE_MODE_OFF");
							break;
						case CameraMetadata.CONTROL_AE_MODE_ON:
							out.value("CONTROL_AE_MODE_ON");
							break;
						case CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
							out.value("CONTROL_AE_MODE_ON_ALWAYS_FLASH");
							break;
						case CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH:
							out.value("CONTROL_AE_MODE_ON_AUTO_FLASH");
							break;
						case CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE:
							out.value("CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE");
							break;
						default:
							out.value(flashMode);
						}
					}
					out.endArray();

					// out.write("  Focus modes: ");
					// boolean first = true;
					// // for (String mode : params.getSupportedFocusModes()) {
					// // if (first) {
					// // first = false;
					// // } else {
					// // out.write("/");
					// // }
					// // out.write(mode);
					// // }
					// out.write("\n");

					SizeF physicalSize = camera.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
					out.name("physical_size").beginObject().name("width").value(physicalSize.getWidth()).name("height")
							.value(physicalSize.getHeight()).endObject();

					out.name("capabilities").beginArray();
					for (int capability : camera.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)) {
						switch (capability) {
						case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR:
							out.value("manual_sensor");
							break;
						case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING:
							out.value("manual_post_processing");
							break;
						case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE:
							out.value("backward_compatible");
							break;
						case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW:
							out.value("raw");
							break;
						default:
							out.value(capability);
						}
					}
					out.endArray();
					// out.write("  Picture formats: ");
					// first = true;
					// for (int i : params.getSupportedPictureFormats()) {
					// if (first) {
					// first = false;
					// } else {
					// out.write("/");
					// }
					// switch (i) {
					// case ImageFormat.JPEG:
					// out.write("JPEG");
					// break;
					// case ImageFormat.NV16:
					// out.write("NV16");
					// break;
					// case ImageFormat.NV21:
					// out.write("NV21");
					// break;
					// case ImageFormat.RGB_565:
					// out.write("RGB_565");
					// break;
					// case ImageFormat.YUV_420_888:
					// out.write("YUV_420_888");
					// break;
					// case ImageFormat.YUY2:
					// out.write("YUY2");
					// break;
					// case ImageFormat.YV12:
					// out.write("YV12");
					// break;
					// default:
					// out.write(i + " (no matching ImageFormat constant)");
					// }
					// }
					// out.write("\n");

					// out.write("  Sizes:\n");

					// for (Size size : params.getSupportedPictureSizes()) {
					// out.write("    [" + count + "]: " + size.width + "x" + size.height + "\n");
					// count++;
					// }
					out.endObject();
				}
				out.endArray();
			}
		});
	}
}
