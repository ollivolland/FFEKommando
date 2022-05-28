package com.ollivolland.ffekommando

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.jcodec.containers.mp4.boxes.MetaValue
import org.jcodec.movtool.MetadataEditor
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread


class ActivityCamera : AppCompatActivity() {
    private lateinit var recorder: MediaRecorder
    private lateinit var bStop: ImageButton
    private var isCameraCreated = false
    private val myCallBack = MyCallBack()
    private var fileName = ""; var path = ""
    private lateinit var threadCamera:Thread
    private lateinit var threadCommand:Thread
    private var dateStarted: Date? = null
    private lateinit var cameraInstance:CameraInstance

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        cameraInstance = nextInstance!!
        fileName = "VID_${Globals.formatToSeconds.format(Date(cameraInstance.correctedTimeStartCamera))}_${Globals.getDeviceId(this)}.mp4"
        val dir = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path}/Camera"
        if(!File(dir).exists()) File(dir).mkdirs()
        path = "$dir/$fileName"

        val vCameraSurface:SurfaceView = findViewById(R.id.start_sCameraView)
        val tText:TextView = findViewById(R.id.camera_tText)
        bStop = findViewById(R.id.start_bStop)

        bStop.isEnabled = false
        bStop.setOnClickListener { stop("click") }
        tText.text = "\ntime to start = ${cameraInstance.correctedTimeStartCamera - ActivityMain.correctedTime} ms"

        //  Camera
        threadCamera = Thread {
            try {
                //  prepare
                recorder = MediaRecorder()
                initRecorder()
                recorder.setPreviewDisplay(vCameraSurface.holder.surface)
                vCameraSurface.holder.addCallback(myCallBack)
                while (!isCameraCreated) Thread.sleep(10)
                runOnUiThread { recorder.prepare() }    //  else illegal state when calling stop()

                //wait & do
                sleepUntilCorrected(cameraInstance.correctedTimeStartCamera)
                recorder.start()    //  HEAVY AS FUCK, takes 750ms
                dateStarted = Date(ActivityMain.correctedTime)

                Thread.sleep(Long.MAX_VALUE)
            } catch (e1: Exception) {
                Log.i("CAMERA","camera thread interrupted $e1")
                try {
                    thread {    //  because the interruption cancels i/o processes
                        try {
                            recorder.stop()
                            recorder.release()
                        } catch (e:Exception) { Log.e("CAMERA", "exception on recorder termination, $e\n${e.stackTraceToString()}") }

                        val mediaMeta: MetadataEditor = MetadataEditor.createFrom(File(path))
                        val meta: MutableMap<String, MetaValue> = mediaMeta.keyedMeta

                        val timeToCommandSeconds =
                            (cameraInstance.correctedTimeCommandExecuted - dateStarted!!.time) / 1000.0
                        val json = JSONObject()

                        json.put("dateVideoStart",
                            Globals.formatToMillis.format(dateStarted!!))

                        json.put("dateCommand",
                            Globals.formatToMillis.format(cameraInstance.correctedTimeCommandExecuted))

                        meta["com.apple.quicktime.title"] =
                            MetaValue.createString(json.toString(4)) //  system.title will not work

                        meta["com.apple.quicktime.information"] =
                            MetaValue.createString(
                                "time to command = ${
                                    timeToCommandSeconds.format(
                                        3
                                    )
                                } s"
                            )

                        //  Save
                        mediaMeta.save(false) // fast mode is off
                        for ((key, value) in mediaMeta.keyedMeta.entries) println("$key: $value")
                        Log.i("CAMERA", "video finished and metadata written")
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "error in writing metadata", Toast.LENGTH_LONG).show() }
                    Log.e("CAMERA", "Metadata error: $e\n${e.stackTraceToString()}")
                }
            }
        }

        //  Command
        threadCommand = Thread {
            var commandWrapper:CommandWrapper? = null
            try {
                //  prepare
                commandWrapper = CommandWrapper[cameraInstance.commandFullName, this]
                commandWrapper.prepare()

                //  wait & do
                sleepUntilCorrected(cameraInstance.correctedTimeStartCamera + CameraConfig.COMMAND_DELAY)
                commandWrapper.start()

                Thread.sleep(Long.MAX_VALUE)
            } catch (e: Exception) {
                Log.i("CAMERA","command thread interrupted $e")
                commandWrapper?.stopAndRelease()
            }
        }

        //  On created
        myCallBack.onCreated.add { isCameraCreated = true }

        if(cameraInstance.isCamera) vCameraSurface.visibility = View.VISIBLE
        else tText.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

        start()
    }

    private fun start() {
        if(cameraInstance.isCamera) threadCamera.start()
        if(cameraInstance.isCommand) threadCommand.start()

        //  stopper thread
        thread {
            sleepUntilCorrected(cameraInstance.correctedTimeCommandExecuted + cameraInstance.millisVideoLength)
            stop("stopper thread")
        }

        //flashlight
        thread {
            CameraH.switchFlashLight(this, true)
            Thread.sleep(1000)
            CameraH.switchFlashLight(this, false)
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

    private fun initRecorder() {
        //  Initial
        recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT)
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        //  Initialized
        recorder.setProfile(CamcorderProfile.get(intent.extras!!.getInt(VIDEO_PROFILE)))   //  configures datasource, ie new config will throw error
        //recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        //  DatasourceConfig
        //recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)    //  needs outputFormat
        //recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

        //  File
        recorder.setOutputFile(path)
        recorder.setMaxDuration(300_000) // 5 minutes
        recorder.setMaxFileSize(2_000_000_000L) //  2gb

        //recorder.setVideoSize(intent.extras!!.getInt("videoWidth"), intent.extras!!.getInt("videoHeight"))
        //recorder.setVideoFrameRate(intent.extras!!.getInt("videoFPS"))
        //recorder.setCaptureRate(intent.extras!!.getInt("videoFPS").toDouble())   //diff?
        //recorder.setVideoEncodingBitRate(intent.extras!!.getInt("videoBitRate"))    //  pulled out of my ass

        if(Build.VERSION.SDK_INT >= 26) {
            println("size = ${recorder.metrics.get(MediaRecorder.MetricsConstants.WIDTH)}x${recorder.metrics.get(MediaRecorder.MetricsConstants.HEIGHT)}")
            println("fps = ${recorder.metrics.get(MediaRecorder.MetricsConstants.FRAMERATE)}")
            println("bitrate = ${recorder.metrics.get(MediaRecorder.MetricsConstants.VIDEO_BITRATE)}")
        }
    }

    class MyCallBack : SurfaceHolder.Callback{
        val onCreated = arrayListOf<() -> Unit>()

        override fun surfaceCreated(p0: SurfaceHolder) {
            for (e in onCreated) e()
        }

        override fun surfaceDestroyed(p0: SurfaceHolder) {}
        override fun surfaceChanged(p0: SurfaceHolder, format: Int, w: Int, h: Int) {}
    }

    companion object {
        const val VIDEO_PROFILE = "videoProfile"
        var nextInstance:CameraInstance? = null

        fun startCamera(activity:Activity, config: CameraInstance)
        {
            nextInstance = config

            activity.startActivity(Intent(activity, ActivityCamera::class.java).apply {
                putExtra(VIDEO_PROFILE, CamcorderProfile.QUALITY_1080P)
            })
        }
    }
}