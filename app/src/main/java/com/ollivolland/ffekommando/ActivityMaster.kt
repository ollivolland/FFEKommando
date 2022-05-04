package com.ollivolland.ffekommando

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*
import kotlin.math.max

class ActivityMaster : AppCompatActivity() {
    lateinit var id:String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master)

        id = intent.extras!!.getString("ID")!!

        val text: TextView = findViewById(R.id.master_tText)
        text.text = "id: $id"

        val bStart:Button = findViewById(R.id.master_bStart)

        bStart.setOnClickListener {
            val timeStartCamera = System.currentTimeMillis() + 5000L
            Firebase.database.reference.child("masters").child(id).child("startCamera").setValue(timeStartCamera)

            Thread {
                Thread.sleep(max(timeStartCamera - System.currentTimeMillis(), 0))
                Firebase.database.reference.child("masters").child(id).child("startCamera").removeValue()
            }.start()

            ActivityCamera.startCamera(this, true, timeStartCamera,
                "VID_${Globals.formatToSeconds.format(Date(timeStartCamera))}_master.mp4")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Firebase.database.reference.child("masters").child(id).removeValue()
    }
}