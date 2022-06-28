package com.ollivolland.ffekommando

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.concurrent.thread


class MyCameraCameraX(private val activity: Activity, private val vPreviewSurface:PreviewView, val path:String, val videoProfile:Int, private val timerSynchronized: MyTimer) : MyCamera() {

    private lateinit var videoCapture: VideoCapture<Recorder>
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
    private lateinit var cameraProvider:ProcessCameraProvider
    private val qualitySelector = QualitySelector.from(Quality.FHD)
    private val executor = ContextCompat.getMainExecutor(activity)
    private var isHasCaptured = false
    private var isVideoSaved = false
    private var isStartTimeWritten = false
    private var recording:Recording? = null

    init {
        log += "init"

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

             create()
        }, executor)
    }

    @SuppressLint("RestrictedApi")
    override fun startRecord() {
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
//        videoCapture.camera?.cameraControl?.setLinearZoom(0.5f)

        recording = videoCapture.output.prepareRecording(activity, FileOutputOptions.Builder(File(path)).build()).start(executor) {
            when (it) {
                is VideoRecordEvent.Start -> {
                    log += "VIDEO START ${SystemClock.uptimeMillis()}000"
                }
                is VideoRecordEvent.Pause -> {
                    log += "VIDEO PAUSE ${SystemClock.uptimeMillis() - (it.recordingStats.recordedDurationNanos / 1E6).toLong()}000"
                }
                is VideoRecordEvent.Finalize -> {
                    getStartTimeUs { timeUs ->
                        log += "TIME START = $timeUs"
                        timeSynchronizedStartedRecording = timerSynchronized.time - SystemClock.uptimeMillis() + (timeUs / 1000)

                        isStartTimeWritten = true
                    }

                    log += "VIDEO FINALIZED"
                    isVideoSaved = true
                }
            }
        }
    }

    override fun stopRecord() {
        recording?.pause()
        recording?.stop()

        cameraProviderFuture.cancel(true)
        activity.runOnUiThread { cameraProvider.unbindAll() }

        while (!isVideoSaved || !isStartTimeWritten) Thread.sleep(10)
    }

    @SuppressLint("RestrictedApi")
    private fun create() {
        cameraProvider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        //  Preview use case
        val preview = Preview.Builder()
            .build()

        preview.setSurfaceProvider(vPreviewSurface.surfaceProvider)

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        videoCapture = VideoCapture.withOutput(Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build())

        cameraProvider.bindToLifecycle(activity as LifecycleOwner, cameraSelector, videoCapture, preview)
    }

    private fun getStartTimeUs(func:(Long) -> Unit) {
        thread {
            val process = Runtime.getRuntime().exec("logcat -d")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String? = ""
            while (bufferedReader.readLine().also { line = it } != null)
                if(line!!.contains("MPEG4Writer") && line!!.contains("setStartTimestampUs")) {
                    val time = line!!.split(" ").last().toLong()
                    func(time)
                    return@thread
                }

            throw Exception("time not found")
        }
    }
}