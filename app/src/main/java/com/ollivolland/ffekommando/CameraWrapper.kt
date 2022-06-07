package com.ollivolland.ffekommando

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.core.impl.CameraInternal
import androidx.camera.core.impl.Observable
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File


class CameraWrapper(private val activity: Activity, private val vPreviewSurface:PreviewView, val path:String, val videoProfile:Int) {
    private var log = MyLog("CAMERA2")
    var timeStartedRecording:Long = -1
    private lateinit var videoCapture: VideoCapture

    private val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
    private lateinit var cameraProvider:ProcessCameraProvider
    private val qualitySelector = QualitySelector.from(Quality.FHD)
    private val executor = ContextCompat.getMainExecutor(activity)
    private var isHasCaptured = false

    init {
        log += "init"

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

             start()
        }, executor)
    }

    @SuppressLint("RestrictedApi")  //  dunno why, tutorial told me to
    private fun start() {
        cameraProvider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        //  Preview use case
        val preview = Preview.Builder().build()

        preview.setSurfaceProvider(vPreviewSurface.surfaceProvider)

        //  Video Capture use case
        videoCapture = VideoCapture.Builder()
            .setBitRate(20_000_000)
            .setVideoFrameRate(60)
            .build()

        cameraProvider.bindToLifecycle(activity as LifecycleOwner, cameraSelector, preview, videoCapture)
    }

    @SuppressLint("RestrictedApi")
    fun startRecord() {

        if(isHasCaptured) {
            log += "Duplicate startRecord()"
            return
        }

        if (ActivityCompat.checkSelfPermission(activity,Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            log += "Capture use case permission"
            return
        }

        isHasCaptured = true
        val observer = object : Observable.Observer<CameraInternal.State> {
            override fun onNewData(value: CameraInternal.State?) {
                log += "state has changed, new value: $value at ${ActivityMain.correctedTime}"

                if(value == CameraInternal.State.OPEN) timeStartedRecording = ActivityMain.correctedTime
                if(value == CameraInternal.State.CLOSING) log += "video exact duration = ${ActivityMain.correctedTime - timeStartedRecording}"
            }

            override fun onError(t: Throwable) { }
        }
        videoCapture.camera?.cameraState?.addObserver(executor, observer)
        videoCapture.startRecording(VideoCapture.OutputFileOptions.Builder(File(path)).build(),
            executor,
            object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                    log += "video saved successfully at $path"
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    log += "video save UNSUCCESSFUL"
                }
            })
        log += "start void at ${ActivityMain.correctedTime}"
    }

    @SuppressLint("RestrictedApi")
    fun stopRecord() {
        videoCapture.stopRecording()
        cameraProviderFuture.cancel(true)
        activity.runOnUiThread { cameraProvider.unbindAll() }
    }
}