package com.ollivolland.ffekommando

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class ActivityController : AppCompatActivity() {
    lateinit var wakeLock: WakeLock
    lateinit var db: MyDB
    var text:String = ""
    private val listRegisteredCamerasStarts = mutableListOf<Long>()
    private val listPlannedCameraStarts = mutableListOf<CameraInstance>()
    private val mapCameraInstanceToView = mutableMapOf<CameraInstance, View>()
    private lateinit var vParent:LinearLayout

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        db = MyDB(this)
        wakeLock = WakeLock(this)

        val tText: TextView = findViewById(R.id.controller_tText)
        val bStart:Button = findViewById(R.id.controller_bStart)
        val bSchedule:Button = findViewById(R.id.controller_bSchedule)
        val vMaster:LinearLayout = findViewById(R.id.controller_lMaster)
        vParent = findViewById(R.id.controller_lParent)
        text = "myId: $myId\nmasterId: $masterId\nisMaster: $isMaster"

        if(isMaster) {
            vMaster.visibility = View.VISIBLE

            bStart.setOnClickListener {
                val timerSynchronized = ActivityMain.timerSynchronized
                val timeStartCamera = timerSynchronized.time + CameraConfig.default.millisDelay
                val withConfig = CameraConfig.default.generateInstance(this, timeStartCamera)

                db["masters/$myId/sessions/$timeStartCamera"] = hashMapOf(
                    "correctedTimeCameraStart" to withConfig.correctedTimeStartCamera,
                    "correctedTimeCommandExecuted" to withConfig.correctedTimeCommandExecuted,
                    "millisVideoLength" to withConfig.millisVideoLength,
                    "command" to withConfig.commandFullName
                )

//                Thread {
//                    timerSynchronized.sleepUntil(withConfig.correctedTimeCommandExecuted)
//                    db - "/masters/$myId/sessions/$timeStartCamera"
//                }.start()

                registerStartCamera(withConfig)
            }

            bSchedule.setOnClickListener {
                val viewDialog = layoutInflater.inflate(R.layout.view_schedule, null)
                val vTimePicker = viewDialog.findViewById<TimePicker>(R.id.schedule_vTimePicker)

                vTimePicker.setIs24HourView(true)
                AlertDialog.Builder(this)
                    .setView(viewDialog)
                    .setPositiveButton("ok") { a, _ ->
                        val calendar = Calendar.getInstance()
                        calendar.time = Date()
                        calendar[Calendar.HOUR_OF_DAY] = vTimePicker.hour
                        calendar[Calendar.MINUTE] = vTimePicker.minute
                        calendar[Calendar.SECOND] = 0

                        val withConfig = CameraConfig.default.generateInstance(this, calendar.timeInMillis)

                        db["masters/$myId/sessions/${calendar.timeInMillis}"] = hashMapOf(
                            "correctedTimeCameraStart" to withConfig.correctedTimeStartCamera,
                            "correctedTimeCommandExecuted" to withConfig.correctedTimeCommandExecuted,
                            "millisVideoLength" to withConfig.millisVideoLength,
                            "command" to withConfig.commandFullName
                        )

                        registerStartCamera(withConfig)
                        a.dismiss()
                    }
                    .show()
            }
        } else {
            db.listen("masters/$masterId/sessions") { dataSnapshot ->
               dataSnapshot.children.forEach { child ->
                    val timerSynchronized = ActivityMain.timerSynchronized
                    val timeStartCamera = child.child("correctedTimeCameraStart").value as Long
                    val command = child.child("command").value as String

                    if(listRegisteredCamerasStarts.contains(timeStartCamera) || timeStartCamera < timerSynchronized.time) return@forEach

                    val withConfig = CameraInstance(
                        isCamera = CameraConfig.default.isCamera,
                        isCommand = CameraConfig.default.isCommand,
                        commandFullName = command,
                        correctedTimeStartCamera = timeStartCamera,
                        correctedTimeCommandExecuted = child.child("correctedTimeCommandExecuted").value as Long,
                        millisVideoLength = child.child("millisVideoLength").value as Long,
                    )

                    registerStartCamera(withConfig)
                }
            }
        }

        //  UI Loop
        Thread {
            while (!this.isDestroyed)
            {
                val time = ActivityMain.timerSynchronized.time

                runOnUiThread {
                    tText.text = text +
                        "\n\ndelay to GPS satellite = ${ActivityMain.delayList.mean().format(2)} ms" +
                        " ± ${ActivityMain.delayList.stdev().format(2)} ms\n" +
                        "time = ${Globals.formatToTimeOfDay.format(Date(time))}"
                }

                //  start planned cameras
                listPlannedCameraStarts.filter { it.correctedTimeStartCamera - 3_000L < ActivityMain.timerSynchronized.time }.forEach { startCamera(it) }

                Thread.sleep(50)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        if(isMaster) db - "masters/$masterId"

        wakeLock.release()
        isExists = false
    }

    private fun registerStartCamera(config: CameraInstance) {
        listRegisteredCamerasStarts.add(config.correctedTimeStartCamera)
        listPlannedCameraStarts.add(config)

        //  View
        val view = layoutInflater.inflate(R.layout.view_camera_instance, vParent, false)
        val vViewText = view.findViewById<TextView>(R.id.camera_instance_tText)

        vViewText.text = "start at ${Globals.formatToTimeOfDay.format(Date(config.correctedTimeStartCamera))}"

        mapCameraInstanceToView[config] = view
        runOnUiThread { vParent.addView(view) }
    }

    private fun startCamera(config: CameraInstance) {
        ActivityCamera.startCamera(this, config, ActivityMain.timerSynchronized)
        listPlannedCameraStarts.remove(config)

        runOnUiThread { vParent.removeView(mapCameraInstanceToView[config]) }
    }

    companion object {
        var isExists = false
        var isMaster = false
        var myId = ""
        var masterId = ""

        fun launchMaster(context: Context, myId:String) {
            if(isExists) throw Exception("Activity already exists")

            isMaster = true
            this.myId = myId

            isExists = true
            context.startActivity(Intent(context, ActivityController::class.java))
        }

        fun launchSlave(context: Context, myId:String, masterId:String) {
            if(isExists) throw Exception("Activity already exists")

            isMaster = false
            this.myId = myId
            this.masterId = masterId

            isExists = true
            context.startActivity(Intent(context, ActivityController::class.java))
        }
    }
}