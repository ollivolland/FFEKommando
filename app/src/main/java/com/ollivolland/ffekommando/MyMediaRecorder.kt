package com.ollivolland.ffekommando

import android.app.Activity
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView

class MyMediaRecorder(private val path:String, private val surfaceView: SurfaceView, private val activity: Activity):MyCamera() {
    private val mediaRecorder = MediaRecorder()
    private var isHolderCreated = false
    private var isRecorderPrepared = false

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(p0: SurfaceHolder) {
                isHolderCreated = true
            }

            override fun surfaceDestroyed(p0: SurfaceHolder) {}
            override fun surfaceChanged(p0: SurfaceHolder, format: Int, w: Int, h: Int) {}
        })

        setupMediaRecorder()
    }

    override fun startRecord() {
        while (!isRecorderPrepared) Thread.sleep(10)

        try {
            mediaRecorder.start()
            timeStartedRecording = ActivityMain.correctedTime
            log += "started"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopRecord() {
        try {  mediaRecorder.stop() } catch (e:Exception) { e.printStackTrace() }
        try {  mediaRecorder.reset() } catch (e:Exception) { e.printStackTrace() }
        log += "finished"
    }

    private fun setupMediaRecorder() {
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT)
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)

        if(false) {     //  Profile
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_1080P))   //  configures datasource, ie new config will throw error
            mediaRecorder.setOutputFile(path)
            mediaRecorder.setMaxDuration(300_000)
        }
        else {      //  Custom
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setOutputFile(path)
            mediaRecorder.setMaxDuration(300_000)
            mediaRecorder.setVideoEncodingBitRate(10_000_000)
            mediaRecorder.setVideoFrameRate(60)
            mediaRecorder.setVideoSize(1920, 1080)  //  higher x lower
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }

        while (!isHolderCreated) Thread.sleep(10)

        try {
            mediaRecorder.setPreviewDisplay(surfaceView.holder.surface)
            mediaRecorder.prepare()
            isRecorderPrepared = true
            log += "prepared"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}