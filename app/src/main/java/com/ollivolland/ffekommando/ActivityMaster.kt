package com.ollivolland.ffekommando

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class ActivityMaster : AppCompatActivity() {
    lateinit var id:String
    lateinit var wakeLock: WakeLock
    lateinit var db: MyDB
    var text:String = ""

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master)

        id = intent.extras!!.getString("ID")!!

        val tText: TextView = findViewById(R.id.master_tText)
        text = "id: $id"

        val bStart:Button = findViewById(R.id.master_bStart)
        db = MyDB(this)

        bStart.setOnClickListener {
            val timerSynchronized = ActivityMain.timerSynchronized
            val timeStartCamera = timerSynchronized.time + CameraConfig.default.millisDelay
            val withConfig = CameraConfig.default.generateInstance(this, timeStartCamera)

            db["masters/$id/sessions/$timeStartCamera"] = hashMapOf(
                "correctedTimeCameraStart" to withConfig.correctedTimeStartCamera,
                "correctedTimeCommandExecuted" to withConfig.correctedTimeCommandExecuted,
                "millisVideoLength" to withConfig.millisVideoLength,
                "command" to withConfig.commandFullName
            )

            Thread {
                timerSynchronized.sleepUntil(withConfig.correctedTimeCommandExecuted + withConfig.millisVideoLength + 10_000L)
                db - "/masters/$id/sessions/$timeStartCamera"
            }.start()

            ActivityCamera.startCamera(this, withConfig, timerSynchronized)
        }

        wakeLock = WakeLock(this)

        //  UI Loop
        Thread {
            while (!this.isDestroyed)
            {
                runOnUiThread {
                    tText.text = text +
                        "\n\ndelay to GPS satellite = ${ActivityMain.delayList.mean().format(2)} ms" +
                        " ± ${ActivityMain.delayList.stdev().format(2)} ms"
                }

                Thread.sleep(50)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        db - "masters/$id"

        wakeLock.release()
    }
}