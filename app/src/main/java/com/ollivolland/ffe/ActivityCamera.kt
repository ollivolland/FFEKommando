package com.ollivolland.ffe

import Globals
import MyCommand
import MyTimer
import StartInstance
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import camera.IMyCamera
import camera.MyCameraCameraX
import com.ollivolland.ffe.*
import format
import org.jcodec.containers.mp4.boxes.MetaValue
import org.jcodec.movtool.MetadataEditor
import org.json.JSONObject
import java.io.File
import java.util.*
import kotlin.concurrent.thread


class ActivityCamera : AppCompatActivity() {
    private var camera: IMyCamera? = null
    private lateinit var bStop: ImageButton
    private lateinit var vCameraSurface:PreviewView
    private lateinit var fileName:String
    private lateinit var path:String
    private lateinit var cameraInstance: StartInstance
    private lateinit var timerSynchronized: MyTimer
    private var myCommandObserver: MyCommand? = null

    private val threadCamera:Thread by lazy { Thread {
        try {
            camera!!.init()

            timerSynchronized.sleepUntil(cameraInstance.timeExec - 3000L)
            camera!!.startRecord()

            Thread.sleep(Long.MAX_VALUE)
        }
        catch (e1: InterruptedException) {
            Log.i("VIDEO","camera thread interrupted $e1")

            thread {    //  because the interruption cancels i/o processes
                camera?.stopRecord()
                Thread.sleep(1000)
                tryWriteMetadata()

                lambda(path)
            }
        }
        catch (_: Exception) {}
    }}

    private val threadCommand:Thread by lazy { Thread {
        var commandWrapper: MyCommand? = null
        try {
            //  prepare
            commandWrapper = MyCommand[cameraInstance, this]
            myCommandObserver = commandWrapper

            //  wait & do
            commandWrapper.startAt(timerSynchronized, cameraInstance.timePreview)

            Thread.sleep(Long.MAX_VALUE)
        } catch (e: Exception) {
            Log.i("VIDEO","command thread interrupted $e")
            commandWrapper?.stopAndRelease()
        }
    }}

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vCameraSurface = findViewById(R.id.start_sCameraView)
        val tText:TextView = findViewById(R.id.camera_tText)
        bStop = findViewById(R.id.start_bStop)

        timerSynchronized = Companion.timerSynchronized!!
        cameraInstance = nextInstance!!

        val dateFormatted = Globals.formatDayToSeconds.format(Date(cameraInstance.timePreview))
        val deviceId = Globals.getDeviceId(this)
        fileName = "VID_${dateFormatted}_${deviceId}.mp4"
        val dir = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath}/Camera"
        if(!File(dir).exists()) File(dir).mkdirs()
        path = "$dir/$fileName"

        bStop.isEnabled = false
        bStop.setOnClickListener { stop("click") }

        if(cameraInstance.profile.isCamera) vCameraSurface.visibility = View.VISIBLE
        else tText.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

        thread {
            while (!this.isDestroyed) {
                runOnUiThread {
                    tText.text = "Zeit = ${"%.2f".format((timerSynchronized.time - cameraInstance.timeExec) / 1000.0)} s"
                }

                Thread.sleep(50)
            }
        }

        //  last
        camera = MyCameraCameraX(this, vCameraSurface, path, timerSynchronized)
//        camera = MyCameraMediaRecorder(path, vCameraSurface, this, timerSynchronized)

        start()
    }

    private fun start() {
        if(cameraInstance.profile.isCommand) threadCommand.start()
        if(cameraInstance.profile.isCamera) threadCamera.start()

        //  stopper thread
        thread {
            timerSynchronized.sleepUntil(cameraInstance.timeExec + cameraInstance.profile.millisVideoLength)
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

        thread {
            Thread.sleep(2000)
            finish()
        }
    }

    private fun tryWriteMetadata() {
        try {
            val mediaMeta: MetadataEditor = MetadataEditor.createFrom(File(path))
            val meta: MutableMap<String, MetaValue> = mediaMeta.keyedMeta

            val timeToCommandSeconds = (cameraInstance.timeExec - camera!!.timeSynchronizedStartedRecording) * 0.001
            val timeToCommandAdjustedSeconds = (cameraInstance.timeExec + myCommandObserver!!.observedCommandLag - camera!!.timeSynchronizedStartedRecording) * 0.001
            val json = JSONObject()

            json.put("dateVideoStart",
                Globals.formatDayToMillis.format(Date(camera!!.timeSynchronizedStartedRecording)))

            json.put("dateCommandWithoutLag",
                Globals.formatDayToMillis.format(cameraInstance.timeExec))

            if(myCommandObserver != null)
            {
                val lag = myCommandObserver!!.observedCommandLag
                json.put("dateCommand", Globals.formatDayToMillis.format(cameraInstance.timeExec + lag))
                json.put("lagMs", myCommandObserver!!.observedCommandLag)

                meta["com.apple.quicktime.album"] =
                    MetaValue.createString("time to command = ${(timeToCommandAdjustedSeconds).format(3)} s")
            }

            meta["com.apple.quicktime.title"] =
                MetaValue.createString(json.toString(4)) //  system.title will not work

            meta["com.apple.quicktime.information"] =
                MetaValue.createString("time to command without lag = ${timeToCommandSeconds.format(3)} s")

            //  Save
            mediaMeta.save(false) // fast mode is off
            for ((key, value) in mediaMeta.keyedMeta.entries) println("$key: $value")
            Log.i("CAMERA", "video finished and metadata written")
        }
        catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "error in writing metadata", Toast.LENGTH_LONG).show() }
            Log.w("CAMERA", "Metadata error: $e\n${e.stackTraceToString()}")
        }
    }

    companion object {
        private const val VIDEO_PROFILE = "videoProfile"
        var nextInstance: StartInstance? = null
        var timerSynchronized: MyTimer? = null
        var lambda: (String) -> Unit = {}

        fun startCamera(activity:Activity, config: StartInstance, timerSynchronized: MyTimer, lambda: (String) -> Unit)
        {
            nextInstance = config
            Companion.timerSynchronized = timerSynchronized
            Companion.lambda = lambda

            activity.startActivity(Intent(activity, ActivityCamera::class.java).apply {
//                putExtra(VIDEO_PROFILE, CamcorderProfile.QUALITY_1080P)   //  TODO
            })
        }
    }
}