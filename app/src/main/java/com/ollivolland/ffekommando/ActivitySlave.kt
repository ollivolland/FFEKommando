package com.ollivolland.ffekommando

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class ActivitySlave : AppCompatActivity() {

    var lastStart:Long = 0
    var text:String = ""
    lateinit var wakeLock: WakeLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slave)

        val masterId = intent.extras!!.getString("MASTER_ID")!!

        val tText:TextView = findViewById(R.id.slave_tText)
        text = "waiting for command\n\nmaster = $masterId";

        val db = MyDB(this)

        db.listen("masters/$masterId/sessions") { dataSnapshot ->
            if(!dataSnapshot.hasChildren()) return@listen

            val timerSynchronized = ActivityMain.timerSynchronized
            val child = dataSnapshot.children.maxByOrNull { x -> x.key!! }!!
            val timeStartCamera = child.child("correctedTimeCameraStart").value as Long
            val command = child.child("command").value as String

            val withConfig = CameraInstance(
                isCamera = CameraConfig.default.isCamera,
                isCommand = CameraConfig.default.isCommand,
                commandFullName = command,
                correctedTimeStartCamera = timeStartCamera,
                correctedTimeCommandExecuted = child.child("correctedTimeCommandExecuted").value as Long,
                millisVideoLength = child.child("millisVideoLength").value as Long,
            )

            if(timeStartCamera > timerSynchronized.time && timeStartCamera != lastStart) {
                ActivityCamera.startCamera(this@ActivitySlave, withConfig, timerSynchronized)

                lastStart = timeStartCamera
            }
        }

        wakeLock = WakeLock(this)

        //  Ui Loop
        Thread {
            while (!this.isDestroyed)
            {
                runOnUiThread {
                    tText.text = "$text" +
                            "\n\ndelay to GPS satellite = ${ActivityMain.delayList.mean().format(2)} ms" +
                            " ± ${ActivityMain.delayList.stdev().format(2)} ms"
                }

                Thread.sleep(50)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        wakeLock.release()
    }
}