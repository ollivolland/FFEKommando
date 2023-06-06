package com.ollivolland.ffekommando.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.CamcorderProfile
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.ollivolland.camera2.YuvToRgbConverter
import com.ollivolland.ffekommando.*
import org.jcodec.containers.mp4.boxes.MetaValue
import org.jcodec.movtool.MetadataEditor
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.concurrent.thread


class ActivityCamera : AppCompatActivity() {
    private var camera: MyCamera? = null
    private lateinit var bStop: ImageButton
    private lateinit var vCameraSurface:PreviewView
    private var fileName = ""; var path = ""
    private lateinit var threadCamera:Thread
    private lateinit var threadCommand:Thread
    private lateinit var cameraInstance: CameraInstance
    private lateinit var timerSynchronized: MyTimer
    private var myCommandObserver: MyCommand? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        timerSynchronized = Companion.timerSynchronized!!
        cameraInstance = nextInstance!!
        fileName = "VID_${Globals.formatDayToSeconds.format(Date(cameraInstance.correctedTimeStartCamera))}_${
            Globals.getDeviceId(
                this
            )
        }.mp4"
        val dir = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath}/Camera"
        if(!File(dir).exists()) File(dir).mkdirs()
        path = "$dir/$fileName"

        vCameraSurface = findViewById(R.id.start_sCameraView)
        val tText:TextView = findViewById(R.id.camera_tText)
        bStop = findViewById(R.id.start_bStop)

        bStop.isEnabled = false
        bStop.setOnClickListener { stop("click") }

        //  Camera
        threadCamera = Thread {
            try {
                //  Init
                camera = MyCameraCameraX(this, vCameraSurface, path, intent.extras!!.getInt(
                    VIDEO_PROFILE
                ), timerSynchronized)
//                camera = MyCameraMediaRecorder(path, vCameraSurface, this, timerSynchronized)
                //wait & do
                timerSynchronized.sleepUntil(cameraInstance.correctedTimeStartCamera)
                camera!!.startRecord()

                Thread.sleep(Long.MAX_VALUE)
            }
            catch (e1: InterruptedException) {
                Log.i("CAMERA","camera thread interrupted $e1")

                thread {    //  because the interruption cancels i/o processes
                    camera?.stopRecord()
                    Thread.sleep(1000)
                    tryWriteMetadata()

                    lambda(path)
                }
            }
            catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
            }
        }

        //  Command
        threadCommand = Thread {
            var commandWrapper: MyCommand? = null
            try {
                //  prepare
                commandWrapper = MyCommand[cameraInstance.commandFullName, this]
                myCommandObserver = commandWrapper
                commandWrapper.prepare()

                //  wait & do
                commandWrapper.startAt(timerSynchronized, cameraInstance.correctedTimeStartCamera)

                Thread.sleep(Long.MAX_VALUE)
            } catch (e: Exception) {
                Log.i("CAMERA","command thread interrupted $e")
                commandWrapper?.stopAndRelease()
            }
        }

        if(cameraInstance.isCamera) vCameraSurface.visibility = View.VISIBLE
        else tText.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

        thread {
            while (!this.isDestroyed) {
                runOnUiThread {
                    tText.text = "time to start = ${(cameraInstance.correctedTimeStartCamera - timerSynchronized.time) / 1000.0} s"
                }

                Thread.sleep(100)
            }
        }

        start()
    }

    private fun start() {
        if(cameraInstance.isCommand) threadCommand.start()
        if(cameraInstance.isCamera) threadCamera.start()

        //  stopper thread
        thread {
            timerSynchronized.sleepUntil(cameraInstance.correctedTimeCommandExecuted + cameraInstance.millisVideoLength)
            stop("stopper thread")
        }

        //  Stop button
        bStop.isEnabled = true
        bStop.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()

        stop("activity destroyed")
    }

    private fun stop(debugReason:String) {
        Log.i("CAMERA", "stopped due to $debugReason")

        threadCamera.interrupt()
        threadCommand.interrupt()

        finish()
    }

    private fun tryWriteMetadata() {
        try {
            val mediaMeta: MetadataEditor = MetadataEditor.createFrom(File(path))
            val meta: MutableMap<String, MetaValue> = mediaMeta.keyedMeta

            val timeToCommandSeconds =
                (cameraInstance.correctedTimeCommandExecuted - camera!!.timeSynchronizedStartedRecording) * 0.001
            val json = JSONObject()

            json.put("dateVideoStart",
                Globals.formatDayToMillis.format(Date(camera!!.timeSynchronizedStartedRecording)))

            json.put("dateCommand",
                Globals.formatDayToMillis.format(cameraInstance.correctedTimeCommandExecuted))

            if(myCommandObserver != null)
            {
                val lag = myCommandObserver!!.observedCommandLag
                json.put("dateCommandWithLag",
                    Globals.formatDayToMillis.format(cameraInstance.correctedTimeCommandExecuted + lag))
                json.put("lagMs", lag)

                meta["com.apple.quicktime.album"] =
                    MetaValue.createString(
                        "time to command with lag = ${(timeToCommandSeconds + lag * 0.001).format(3)} s")
            }

            meta["com.apple.quicktime.title"] =
                MetaValue.createString(json.toString(4)) //  system.title will not work

            meta["com.apple.quicktime.information"] =
                MetaValue.createString(
                    "time to command = ${timeToCommandSeconds.format(3)} s")

            //  Save
            mediaMeta.save(false) // fast mode is off
            for ((key, value) in mediaMeta.keyedMeta.entries) println("$key: $value")
            Log.i("CAMERA", "video finished and metadata written")
        }
        catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "error in writing metadata", Toast.LENGTH_LONG).show() }
            Log.w("CAMERA", "Metadata error: $e\n${e.stackTraceToString()}")
            Firebase.crashlytics.recordException(e)
        }
    }

    companion object {
        private const val VIDEO_PROFILE = "videoProfile"
        var nextInstance: CameraInstance? = null
        var timerSynchronized: MyTimer? = null
        var lambda: (String) -> Unit = {}

        fun startCamera(activity:Activity, config: CameraInstance, timerSynchronized: MyTimer, lambda: (String) -> Unit)
        {
            nextInstance = config
            Companion.timerSynchronized = timerSynchronized
            Companion.lambda = lambda

            activity.startActivity(Intent(activity, ActivityCamera::class.java).apply {
                putExtra(VIDEO_PROFILE, CamcorderProfile.QUALITY_1080P)
            })
        }
    }
}