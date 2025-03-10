package camera

import Globals
import MyTimer
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.camera.core.*
import androidx.camera.core.impl.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File


class MyCameraCameraX(private val activity: Activity, private val vPreviewSurface:PreviewView, val path:String, private val timerSynchronized: MyTimer) : IMyCamera() {

    private lateinit var videoCapture: VideoCapture<Recorder>
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
    private lateinit var cameraProvider:ProcessCameraProvider
    private val qualitySelector = QualitySelector.from(Quality.FHD)
    private val executor = ContextCompat.getMainExecutor(activity)
    private var isHasCaptured = false
    private var recording:Recording? = null

    init {
        if(path.isBlank())
            throw Exception()
    }

    override fun init() {
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
        log += "START"


        val output = FileOutputOptions.Builder(File(path)).setLocation(Location(path)).build()

        recording = videoCapture.output
            .prepareRecording(activity, output)
            .withAudioEnabled()
            .start(executor) {
            when (it) {
                is VideoRecordEvent.Start -> {
                    timeSynchronizedStartedRecording = timerSynchronized.time
                    val x = timerSynchronized.time
                    log += "VIDEO Start ${Globals.formatDayToMillis.format(x)}=${x - timerSynchronized.bootTime}"
                }
                is VideoRecordEvent.Finalize -> {
                    val x = timerSynchronized.bootTime + SystemClock.elapsedRealtime() - (it.recordingStats.recordedDurationNanos * 1E-6).toLong()
                    log += "VIDEO Finalize ${Globals.formatDayToMillis.format(x)}=${x - timerSynchronized.bootTime}"
                }
            }
        }
    }

    override fun stopRecord() {
        log += "STOP"

//        recording?.pause()
        recording?.stop()

        cameraProviderFuture.cancel(true)
        activity.runOnUiThread { cameraProvider.unbindAll() }
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

//        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        videoCapture = VideoCapture.withOutput(Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build())

        cameraProvider.bindToLifecycle(activity as LifecycleOwner, cameraSelector, videoCapture, preview)
    }
}