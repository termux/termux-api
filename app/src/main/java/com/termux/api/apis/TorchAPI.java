package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.widget.Toast;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

public class TorchAPI {
    private static Camera legacyCamera;

    private static final String LOG_TAG = "TorchAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        boolean enabled = intent.getBooleanExtra("enabled", false);

        toggleTorch(context, enabled);
        ResultReturner.noteDone(apiReceiver, intent);
    }

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
            Logger.logStackTraceWithMessage(LOG_TAG, "Error toggling torch", e);
        }
    }

    private static void legacyToggleTorch(boolean enabled) {
        Logger.logInfo(LOG_TAG, "Using legacy camera api to toggle torch");

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
