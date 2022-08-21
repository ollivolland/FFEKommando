package com.ollivolland.ffekommando

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import android.view.TextureView


class MyStreamer(private val context: Context, private val vTexture: TextureView) {

    internal val log = MyLog(this::class.java.name)
    lateinit var backgroundThread: HandlerThread
    lateinit var backgroundHandler: Handler
    lateinit var cameraDevice: CameraDevice
    lateinit var captureSession: CameraCaptureSession
    var onImageAvailable:(ImageReader) -> Unit = { }

    val PREVIEW_WIDTH = 1920
    val PREVIEW_HEIGHT = 1080

    fun prepare() {
        //  start background thread
        backgroundThread = HandlerThread("my camera2 background thread").apply { start() }
        backgroundHandler = Handler(backgroundThread.looper)

        //  surface listener
        vTexture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            @SuppressLint("MissingPermission")
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, width: Int, height: Int) {
                log += "surface texture available = ${width}x${height}"

                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

                val cameraId = cameraManager.cameraIdList.filter {
                    cameraManager.getCameraCharacteristics(it)[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK
                }[0]

                //  open
                cameraManager.openCamera(cameraId, object: CameraDevice.StateCallback() {
                    override fun onOpened(p0: CameraDevice) {
                        log += "camera device opened"
                        cameraDevice = p0

                        //  preview
                        vTexture.surfaceTexture!!.setDefaultBufferSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                        val surface = Surface(vTexture.surfaceTexture!!)

                        //  Image reader
                        val imReader = ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT,  ImageFormat.YUV_420_888, 2)
                        imReader.setOnImageAvailableListener(onImageAvailable, backgroundHandler)

                        //  builder
                        val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        captureBuilder.addTarget(imReader.surface)
                        captureBuilder.addTarget(surface)

                        cameraDevice.createCaptureSession(listOf(imReader.surface, surface), object: CameraCaptureSession.StateCallback() {
                            override fun onConfigured(p0: CameraCaptureSession) {
                                captureSession = p0
                                captureBuilder[CaptureRequest.CONTROL_AF_MODE] = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                captureBuilder[CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE] = Range(30, 30)
                                captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
                            }

                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                                log += "CONFIG FAILED"
                            }

                        }, null)
                    }

                    override fun onDisconnected(p0: CameraDevice) {
                        log += "camera device disconnected"
                        p0.close()
                    }

                    override fun onError(p0: CameraDevice, p1: Int) {
                        log += "camera device error"
                    }
                }, backgroundHandler)
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) = Unit
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) = Unit
        }
    }

    fun startAt(elapsedMillisTime:Long) {
    }

    fun withImage(lambda: (ImageReader) -> Unit) {
        onImageAvailable = lambda
    }

    fun stop() {
    }

    fun destroy() {
        if(this::captureSession.isInitialized) captureSession.close()
        if(this::cameraDevice.isInitialized) cameraDevice.close()

        //  stop background thread
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: Exception) {
            log += e
        }
    }
}