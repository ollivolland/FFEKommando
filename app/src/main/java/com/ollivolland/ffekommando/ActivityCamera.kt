package com.ollivolland.ffekommando

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.CamcorderProfile
import android.media.CameraProfile
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import kotlin.math.max


class ActivityCamera : AppCompatActivity() {
    lateinit var recorder: MediaRecorder
    lateinit var  holder: SurfaceHolder
    //lateinit var bPlay: ImageButton
    var  recording = false
    private val myCallBack = MyCallBack()
    var path = ""
    var thread:Thread? = null
    var dateStarted: Date? = null
    var dateCommand: Date? = null
    private val formatToMillis = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ENGLISH)
    lateinit var mp: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        val cameraView = findViewById<SurfaceView>(R.id.start_sCameraView)

//        bPlay = findViewById(R.id.start_bPlay)
//        bPlay.isEnabled = false
//        bPlay.setOnClickListener { startStop() }

        val tText:TextView = findViewById(R.id.camera_tText)
        tText.text = tText.text.toString() + "\ndelay = ${intent.extras!!.getLong(UNIX_TIME_START) - System.currentTimeMillis()} ms"

        try {
        recorder = MediaRecorder()
        mp = MediaPlayer.create(this, R.raw.startbefehl)

        //  Oncreated
        myCallBack.onCreated.add {
//            bPlay.isEnabled = true
//            bPlay.setImageResource(R.drawable.ic_outline_play_arrow_32)

            recorder.setPreviewDisplay(holder.surface)
            recorder.prepare()
            startStop()
        }

        //  Ondestroyed
        myCallBack.onDestroyed.add {
            if (recording) startStop()
            recorder.release()
            mp.release()

            val mediaMeta: MetadataEditor = MetadataEditor.createFrom(File(path))
            val meta: MutableMap<String, MetaValue> = mediaMeta.keyedMeta
            for ((key, value) in meta.entries)  println("$key: $value")

            val json = JSONObject()
            json.put("dateVideoStart", formatToMillis.format(dateStarted!!))
            json.put("isHasCommand", dateCommand != null)
            if(dateCommand != null) json.put("dateCommand", formatToMillis.format(dateCommand!!))

            meta["com.apple.quicktime.title"] = MetaValue.createString(json.toString(4)) //  system.title will not work
            meta["com.apple.quicktime.album"] = MetaValue.createString("olli album =>  ${formatToMillis.format(dateStarted!!)}")
            meta["com.apple.quicktime.author"] = MetaValue.createString("olli author")
            meta["com.apple.quicktime.artist"] = MetaValue.createString("olli artist")
            meta["com.apple.quicktime.comment"] = MetaValue.createString("olli comment")
            meta["com.apple.quicktime.description"] = MetaValue.createString("olli description")
            mediaMeta.save(false) // fast mode is off

            for ((key, value) in mediaMeta.keyedMeta.entries) println("$key: $value")
        }

        holder = cameraView.holder
        holder.addCallback(myCallBack)
        initRecorder()
        } catch (e:Exception) {
            finish()
        }
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
        val dir = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path}/Camera"
        if(!File(dir).exists()) File(dir).mkdirs()

        recorder.setOutputFile("$dir/${intent.extras!!.getString(FILE_NAME)}")
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

    private fun startStop() {
        if(thread == null)
        {
            thread = Thread {
                try {
                    //  wait until time
                    Thread.sleep(max(intent.extras!!.getLong(UNIX_TIME_START) - System.currentTimeMillis(), 0))

                    recorder.start()    //  HEAVY AS FUCK, takes 750ms
                    dateStarted = Date()
                    if(intent.extras!!.getBoolean(IS_COMMAND)) mp.start()

                    Thread.sleep(18_000)

                    if(intent.extras!!.getBoolean(IS_COMMAND)) dateCommand = Date()

                    Thread.sleep(60_000)

                    startStop()
                } catch (e: Exception) { println("thread interrupted") }
            }
            thread!!.start()

            //flashlight
            Thread {
                CameraH.switchFlashLight(this, true)
                Thread.sleep(1000)
                CameraH.switchFlashLight(this, false)
            }.start()
        } else {
            thread!!.interrupt()
            thread = null
            recorder.stop()
            mp.stop()
            mp.reset()

            finish()
        }

        recording = !recording
//        bPlay.setImageResource(if (recording) R.drawable.ic_outline_stop_32 else R.drawable.ic_outline_play_arrow_32)
    }

    class MyCallBack : SurfaceHolder.Callback{
        val onCreated = arrayListOf<() -> Unit>()
        val onDestroyed = arrayListOf<() -> Unit>()

        override fun surfaceCreated(p0: SurfaceHolder) {
            for (e in onCreated) e()
        }

        override fun surfaceDestroyed(p0: SurfaceHolder) {
            for (e in onDestroyed) e()
        }

        override fun surfaceChanged(p0: SurfaceHolder, format: Int, w: Int, h: Int) {
            // Handle changes
        }
    }

    companion object {
        const val IS_COMMAND = "isCommand"
        const val FILE_NAME = "FILE_NAME"
        const val UNIX_TIME_START = "unixTimeStart"
        const val VIDEO_PROFILE = "videoProfile"

        fun startCamera(activity:Activity, isCommand: Boolean, unixTimeStartCamera:Long, fileName: String)
        {
            activity.startActivity(Intent(activity, ActivityCamera::class.java).apply {
                putExtra(IS_COMMAND, isCommand)
                putExtra(VIDEO_PROFILE, CamcorderProfile.QUALITY_1080P)
                putExtra(UNIX_TIME_START, unixTimeStartCamera)
                putExtra(FILE_NAME, fileName)
            })
        }
    }
}