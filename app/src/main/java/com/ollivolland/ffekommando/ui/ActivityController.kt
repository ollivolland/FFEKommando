package com.ollivolland.ffekommando.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.ollivolland.ffekommando.*
import java.util.*
import kotlin.concurrent.thread


class ActivityController : AppCompatActivity() {
    lateinit var db: MyDB
    var text:String = ""
    private val listRegisteredCamerasStarts = mutableListOf<Long>()
    private val listPlannedCameraStarts = mutableListOf<CameraInstance>()
    private val mapCameraInstanceToView = mutableMapOf<CameraInstance, ViewInstance>()
    private lateinit var vParent:LinearLayout
    private val listSlaves = mutableMapOf<String, Long>()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        db = MyDB(this)

        val tText: TextView = findViewById(R.id.controller_tText)
        val bStart:ImageButton = findViewById(R.id.controller_bStart)
        val bSchedule:ImageButton = findViewById(R.id.controller_bSchedule)
        val vMaster:LinearLayout = findViewById(R.id.controller_lMaster)
        vParent = findViewById(R.id.controller_lParent)
        text = if(isMaster) "master ($myId)" else "slave ($myId)\nmaster id: $masterId"

        if(isMaster) {
            vMaster.visibility = View.VISIBLE

            bStart.setOnClickListener {
                val timerSynchronized = ActivityMain.timerSynchronized
                val timeStartCamera = timerSynchronized.time + DefaultCameraConfig.default.millisDelay
                val withConfig = DefaultCameraConfig.default.generateInstance(this, timeStartCamera)

                db["masters/$myId/sessions/$timeStartCamera"] = hashMapOf(
                    "correctedTimeCameraStart" to withConfig.correctedTimeStartCamera,
                    "correctedTimeCommandExecuted" to withConfig.correctedTimeCommandExecuted,
                    "millisVideoLength" to withConfig.millisVideoLength,
                    "command" to withConfig.commandFullName
                )

                registerStartCamera(withConfig)
                Toast.makeText(this, "wird in ${DefaultCameraConfig.default.millisDelay / 1000L}s starten", Toast.LENGTH_SHORT).show()
            }

            if (Build.VERSION.SDK_INT >= 23)
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

                            val withConfig = DefaultCameraConfig.default.generateInstance(this, calendar.timeInMillis)

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
            else bSchedule.isEnabled = false

            //  connection info
            db.listen("masters/$myId/slavesLastActive") { dataSnapshot ->
                dataSnapshot.children.forEach {
                    listSlaves[it.key!!] = it.value as Long
                }
            }
        } else {
            db.listen("masters/$masterId/sessions") { dataSnapshot ->
               dataSnapshot.children.forEach { child ->
                    val timerSynchronized = ActivityMain.timerSynchronized
                    val timeStartCamera = child.child("correctedTimeCameraStart").value as Long
                    val command = child.child("command").value as String

                    if(listRegisteredCamerasStarts.contains(timeStartCamera) || timeStartCamera < timerSynchronized.time) return@forEach

                    val withConfig = CameraInstance(
                        isCamera = DefaultCameraConfig.default.isCamera,
                        isCommand = DefaultCameraConfig.default.isCommand,
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
        thread {
            while (!this.isDestroyed) {
                val time = ActivityMain.timerSynchronized.time

                runOnUiThread {
                    try {
                        var newText = "\n\ndelay to GPS time = ${ActivityMain.timerSynchronized.time - System.currentTimeMillis()} ms\n" +
                            "time = ${Globals.formatTimeToSeconds.format(Date(time))}" +
                            "   (± ${ActivityMain.timeToBootStdDev.format(0)} ms)"

                        if(isMaster) {
                            val filtered = listSlaves.filter {  it.value > time - 5_000L }.toList()

                            newText += "\n\n${filtered.count()} connected devices"
                            if(filtered.isNotEmpty()) newText += "\n{\t${filtered.joinToString { "\n\t${it.first}" }.removePrefix("\n\t")} }"
                        }

                        tText.text = text + newText
                    } catch (_:Exception) {}
                }

                //  start planned cameras
                listPlannedCameraStarts.filter { it.correctedTimeStartCamera - 3_000L < ActivityMain.timerSynchronized.time }.forEach { startCamera(it) }

                Thread.sleep(50)
            }
        }

        //  Connection info loop
        thread {
            while (!this.isDestroyed) {
                if(isMaster) db["masters/$myId/lastActive"] = ActivityMain.timerSynchronized.time
                else db["masters/$masterId/slavesLastActive/$myId"]= ActivityMain.timerSynchronized.time

                Thread.sleep(1000)
            }

            if(isMaster) db - "masters/$masterId"
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        isExists = false
    }

    private fun registerStartCamera(config: CameraInstance) {
        listRegisteredCamerasStarts.add(config.correctedTimeStartCamera)
        listPlannedCameraStarts.add(config)

        mapCameraInstanceToView[config] = ViewInstance(this, vParent)
        mapCameraInstanceToView[config]!!.let { view ->
            runOnUiThread {
                view.vViewText.text = "start at ${Globals.formatTimeToSeconds.format(Date(config.correctedTimeStartCamera))}"
                vParent.addView(view.container, 1)
            }
        }
    }

    private fun startCamera(config: CameraInstance) {
        ActivityCamera.startCamera(this, config, ActivityMain.timerSynchronized) { s ->
            mapCameraInstanceToView[config]!!.update(s, config)
        }

        listPlannedCameraStarts.remove(config)
    }

    companion object {
        var isExists = false
        var isMaster = false
        var myId = ""
        var masterId = ""

        fun launchMaster(context: Context, myId:String) {
            if(isExists) {
                Toast.makeText(context, "Activity already exists", Toast.LENGTH_LONG).show()
                return
            }

            isMaster = true
            Companion.myId = myId

            isExists = true
            context.startActivity(Intent(context, ActivityController::class.java))
        }

        fun launchSlave(context: Context, myId:String, masterId:String) {
            if(isExists) {
                Toast.makeText(context, "Activity already exists", Toast.LENGTH_LONG).show()
                return
            }

            isMaster = false
            Companion.myId = myId
            Companion.masterId = masterId

            isExists = true
            context.startActivity(Intent(context, ActivityController::class.java))
        }
    }
}