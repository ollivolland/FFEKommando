package com.ollivolland.ffekommando

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Range
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.core.impl.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.video.impl.VideoCaptureConfig
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlin.concurrent.thread


class MyCameraCameraX(private val activity: Activity, private val vPreviewSurface:PreviewView, val path:String, val videoProfile:Int, private val timerSynchronized: MyTimer) : MyCamera() {

    private lateinit var videoCapture: VideoCapture<Recorder>
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
    private lateinit var cameraProvider:ProcessCameraProvider
    private val qualitySelector = QualitySelector.from(Quality.FHD)
    private val executor = ContextCompat.getMainExecutor(activity)
    private var isHasCaptured = false
    private var isVideoSaved = false
    private var recording:Recording? = null
    private var logCatReader:LogCatReader? = null

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

        recording = videoCapture.output
            .prepareRecording(activity, FileOutputOptions.Builder(File(path)).build())
            .withAudioEnabled()
            .start(executor) {
            when (it) {
                is VideoRecordEvent.Start -> {
                    log += "VIDEO START ${SystemClock.uptimeMillis()}000"
                    logCatReader = LogCatReader()
                }
                is VideoRecordEvent.Pause -> {
                    log += "VIDEO PAUSE ${SystemClock.uptimeMillis() - (it.recordingStats.recordedDurationNanos / 1E6).toLong()}000"
                }
                is VideoRecordEvent.Finalize -> {
                    try {
                        val timeUs = logCatReader!!.getStartTimeUsAndDestroy()
                        log += "TIME START = $timeUs"
                        timeSynchronizedStartedRecording = timerSynchronized.time - SystemClock.uptimeMillis() + (timeUs / 1000)
                    } catch (e:Exception) { Toast.makeText(activity, "error in logcat spying", Toast.LENGTH_LONG).show() }

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

        while (!isVideoSaved) Thread.sleep(10)
    }

    @SuppressLint("RestrictedApi", "UnsafeOptInUsageError")
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

    companion object {
        class LogCatReader() {

            private val lines = mutableListOf<String>()
            var process:Process? = null

            init {
                thread {
                    val process = Runtime.getRuntime().exec("logcat")
                    process.inputStream
                        .bufferedReader()
                        .useLines { newLines -> newLines.forEach { line -> lines.add(line) } }
                }
            }

            fun getStartTimeUsAndDestroy(): Long {
                val targetLine =
                    lines.toTypedArray().last { x -> x.contains("MPEG4Writer") && x.contains("setStartTimestampUs") }
                val time = targetLine.split(" ").last().toLong()

                //  terminate
                process?.destroy()

                return time
            }
        }
    }
}