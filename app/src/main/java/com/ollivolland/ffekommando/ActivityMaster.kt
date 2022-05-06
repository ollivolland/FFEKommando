package com.ollivolland.ffekommando

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*
import kotlin.math.max

class ActivityMaster : AppCompatActivity() {
    lateinit var id:String
    lateinit var wakeLock: WakeLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master)

        id = intent.extras!!.getString("ID")!!

        val text: TextView = findViewById(R.id.master_tText)
        text.text = "id: $id"

        val bStart:Button = findViewById(R.id.master_bStart)
        val(read, write) = DataBaseWrapper[this]

        bStart.setOnClickListener {
            val timeStartCamera = System.currentTimeMillis() + 5000L

            write["masters/$id"] = hashMapOf("startCamera" to timeStartCamera)
            Thread {
                Thread.sleep(max(timeStartCamera - System.currentTimeMillis(), 0))
                write - "/masters/$id/startCamera"
            }.start()

            ActivityCamera.startCamera(this, true, timeStartCamera,
                "VID_${Globals.formatToSeconds.format(Date(timeStartCamera))}_master.mp4", 60_000)
        }

        wakeLock = WakeLock(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        val(read, write) = DataBaseWrapper[this]
        write - "masters/$id"

        wakeLock.release()
    }
}