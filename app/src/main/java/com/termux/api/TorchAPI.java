package com.termux.api;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.widget.Toast;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

public class TorchAPI {
    private static Camera legacyCamera;

    // this is needed to prevent some device camera implementations from inadvertently toggling
    // the LED state if same request is sent subsequent times
    private static boolean isEnabled = false;


    @TargetApi(Build.VERSION_CODES.M)
    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        boolean enabled = intent.getBooleanExtra("enabled", false);

        if (enabled != isEnabled) {
            isEnabled = enabled;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                toggleTorch(context, enabled);
            } else {
                // use legacy api for pre-marshmallow
                legacyToggleTorch(enabled);
            }
        }
        ResultReturner.noteDone(apiReceiver, intent);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static void toggleTorch(Context context, boolean enabled) {
        try {
            final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String torchCameraId = getTorchCameraId(cameraManager);

            if (torchCameraId != null) {
                cameraManager.setTorchMode(torchCameraId, enabled);
            } else {
                Toast.makeText(context, "Torch unavailable on your device", Toast.LENGTH_LONG).show();
            }
        } catch (CameraAccessException e) {
            TermuxApiLogger.error("Error toggling torch", e);
        }
    }

    private static void legacyToggleTorch(boolean enabled) {
        TermuxApiLogger.info("Using legacy camera api to toggle torch");

        if (legacyCamera == null) {
            legacyCamera = Camera.open();
        }

        Camera.Parameters params = legacyCamera.getParameters();

        if (enabled) {
            params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            legacyCamera.setParameters(params);
            legacyCamera.startPreview();
        } else {
            legacyCamera.stopPreview();
            legacyCamera.release();
            legacyCamera = null;
        }
    }

    private static String getTorchCameraId(CameraManager cameraManager) throws CameraAccessException {
        String[] cameraIdList =  cameraManager.getCameraIdList();
        String result = null;

        for (String id : cameraIdList) {
            if (cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                result = id;
                break;
            }
        }
        return result;
    }
}
