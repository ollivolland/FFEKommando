package com.ollivolland.ffekommando

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*

class ActivitySlave : AppCompatActivity() {

    var lastStart:Long = 0
    lateinit var wakeLock: WakeLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slave)

        val masterId = intent.extras!!.getString("MASTER_ID")!!

        val tText:TextView = findViewById(R.id.slave_tText)
        tText.text = tText.text.toString() + "\n\nmaster = $masterId";

        Firebase.database.reference.child("masters").child(masterId).child("startCamera").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if(!dataSnapshot.exists()) return

                    val newVal = dataSnapshot.value as Long

                    if(newVal != lastStart) {
                        ActivityCamera.startCamera(this@ActivitySlave, false, newVal,
                            "VID_${Globals.formatToSeconds.format(Date(newVal))}_${Globals.getDeviceId(this@ActivitySlave)}.mp4")

                        lastStart = newVal
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Getting Post failed, log a message
                    Log.w("TAG", "loadPost:onCancelled", databaseError.toException())
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()

        wakeLock.release()
    }
}