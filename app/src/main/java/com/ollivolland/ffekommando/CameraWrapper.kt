package com.ollivolland.ffekommando

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat


class CameraWrapper(val activity: Activity, private val vCameraSurface:TextureView, val path:String, val videoProfile:Int) {
    private val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice:CameraDevice? = null
    private val cameraDeviceCallBack = object : CameraDevice.StateCallback() {
        override fun onOpened(p0: CameraDevice) {
            cameraDevice = p0
            Log.w("CAMERA", "Camera opened")
            startPreview()
        }

        override fun onDisconnected(p0: CameraDevice) { closeCamera() }

        override fun onError(p0: CameraDevice, p1: Int) { closeCamera() }
    }
    private var cameraId:String = ""
    private val textureViewCallBack = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
            setupCamera(p1, p2)
            connectCamera()
        }

        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) { }

        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean { return false }

        override fun onSurfaceTextureUpdated(p0: SurfaceTexture) { }
    }
    private var thread: HandlerThread? = null
    private var handler:Handler? = null
    private var previewSize:Size? = null
    private var captureRequestBuilder:CaptureRequest.Builder? = null
    private val mediaRecorder = MediaRecorder()
    var timeStartedRecording:Long = -1L

    fun onResume() {
        thread = HandlerThread("cameraThread")
        thread!!.start()
        handler = Handler(thread!!.looper)

        if(vCameraSurface.isAvailable) {
            setupCamera(vCameraSurface.width, vCameraSurface.height);
            connectCamera();
        } else {
            vCameraSurface.surfaceTextureListener = textureViewCallBack;
        }
    }

    fun onPause() {
        thread?.quitSafely()
        try {
            thread?.join()
            thread = null
            handler = null
        } catch (e:Exception) { e.printStackTrace() }
    }

    private fun setupCamera(width:Int, height:Int) {

        try {
            for (thisCameraId in cameraManager.cameraIdList) {
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(thisCameraId)

                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) continue

                val map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                val deviceOrientation = activity.windowManager.defaultDisplay.rotation
                Log.i("CAMERA", "device orientation = ${ORIENTATIONS[deviceOrientation]}")
                val totalRotation = sensorToDeviceOrientation(cameraCharacteristics, deviceOrientation)
                val isSwapRotation = totalRotation == 90 || totalRotation == 270

//                val correctedWidth = if(isSwapRotation) height else width
//                val correctedHeight = if(isSwapRotation) width else height

                val sizes = map.getOutputSizes(SurfaceTexture::class.java)

                previewSize = compareSizeByArea(sizes, width, height)

                cameraId = thisCameraId
                return
            }
        } catch (e:Exception) { }
    }

    private fun closeCamera() {
        if(cameraDevice != null) {
            cameraDevice!!.close()
            cameraDevice = null
        }
    }

    private fun connectCamera() {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

        cameraManager.openCamera(cameraId, cameraDeviceCallBack, handler)
    }

    fun startRecord() {
        try {
            setupMediaRecorder()
            val surfaceTexture = vCameraSurface.surfaceTexture!!
//            surfaceTexture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            surfaceTexture.setDefaultBufferSize(1920, 1080)
            val previewSurface = Surface(surfaceTexture)
            val recordSurface: Surface = mediaRecorder.surface
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            captureRequestBuilder!!.addTarget(previewSurface)
            captureRequestBuilder!!.addTarget(recordSurface)
            cameraDevice!!.createCaptureSession(listOf(previewSurface, recordSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            session.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, handler
            )
            mediaRecorder.start()
            timeStartedRecording = ActivityMain.correctedTime
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecord() {
        try {  mediaRecorder.stop() } catch (e:Exception) { e.printStackTrace() }
        try {  mediaRecorder.reset() } catch (e:Exception) { e.printStackTrace() }
    }

    private fun startPreview() {
        val surfaceTexture = vCameraSurface.surfaceTexture!!
//            surfaceTexture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
        surfaceTexture.setDefaultBufferSize(1920, 1080)
        val previewSurface = Surface(surfaceTexture)

        try {
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder!!.addTarget(previewSurface)
            cameraDevice!!.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(p0: CameraCaptureSession) {
                    try {
                        p0.setRepeatingRequest(captureRequestBuilder!!.build(), null, handler)
                    } catch (e:Exception) { }
                }

                override fun onConfigureFailed(p0: CameraCaptureSession) {
                    Log.e("CAMERA", "camera preview setup failed")
                }

            }, null)
        } catch (e:Exception) { }
    }

    private fun setupMediaRecorder() {
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setOutputFile(path)
        mediaRecorder.setMaxDuration(300_000)
        mediaRecorder.setVideoEncodingBitRate(20_000_000)
        mediaRecorder.setVideoFrameRate(60)
//        mediaRecorder.setCaptureRate(60)
        mediaRecorder.setVideoSize(1920, 1080)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setOrientationHint(90)
        mediaRecorder.prepare()
    }

    companion object {
        private val ORIENTATIONS = mapOf(
            Surface.ROTATION_0 to 0,
            Surface.ROTATION_90 to 90,
            Surface.ROTATION_180 to 180,
            Surface.ROTATION_270 to 270,
        )

        fun compareSizeByArea(array:Array<Size>, width: Int, height: Int):Size {
            val isRatioMore = width > height

            val filtered = array.filter { option -> (option.width > option.height) == isRatioMore && option.width >= width && option.height >= height }
                .sortedBy { option -> option.width * option.height }

            return if (filtered.isEmpty()) array[0] else filtered[0]
        }

        private fun sensorToDeviceOrientation(cameraCharacteristics: CameraCharacteristics, deviceOrientation:Int):Int
        {
            val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
            Log.i("CAMERA", "sensorOrientation = $sensorOrientation")
            val newDeviceOrientation = ORIENTATIONS[deviceOrientation]!!
            return (sensorOrientation + newDeviceOrientation + 360) % 360
        }
    }
}