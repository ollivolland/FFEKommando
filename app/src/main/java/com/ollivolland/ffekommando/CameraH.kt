package com.ollivolland.ffekommando

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager

class CameraH {
    companion object {
        fun switchFlashLight(context: Context, status: Boolean) {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            try {
                cameraManager.setTorchMode(cameraManager.cameraIdList[0], status)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }
}