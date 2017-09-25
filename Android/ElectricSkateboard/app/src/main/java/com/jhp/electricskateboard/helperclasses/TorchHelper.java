package com.jhp.electricskateboard.helperclasses;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;

/**
 * Created by J Park on 2/02/2017.
 */

public class TorchHelper {
    /**
     * Turn on back light. Requires api 23+
     */
    public static void turnOnTorch(Context context) {
        CameraManager camManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null; // Usually front camera is at 0 position.
        try {
            cameraId = camManager.getCameraIdList()[0];
            camManager.setTorchMode(cameraId, true);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Turn off back light. Requires api 23+
     */
    public static void turnOffTorch(Context context) {
        CameraManager camManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null; // Usually front camera is at 0 position.
        try {
            cameraId = camManager.getCameraIdList()[0];
            camManager.setTorchMode(cameraId, false);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
