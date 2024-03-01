package com.ollivolland.ffe

import Globals
import MyTimer
import Profile
import StartInstance
import ViewInstance
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.ollivolland.ffe.*
import java.util.*
import kotlin.concurrent.thread


class ActivityMain : AppCompatActivity() {
    val version:Int by lazy { applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionCode }
    val androidIdd:String by lazy { Globals.getDeviceId(this) }
    val timer = MyTimer(System.currentTimeMillis())
    private val listRegisteredCamerasStarts = mutableListOf<Long>()
    private val listPlannedCameraStarts = mutableListOf<StartInstance>()
    private val mapCameraInstanceToView = mutableMapOf<StartInstance, ViewInstance>()
    private lateinit var vParent:LinearLayout

    private var profile = Profile.default

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        //  Permissions
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
            .apply {
                if(isNotEmpty())
                    this@ActivityMain.requestPermissions(this, 0)
            }


        profile.inflateDialog(this) {
            profile = it
        }

        val tText: TextView = findViewById(R.id.controller_tText)
        val vVersion: TextView = findViewById(R.id.main_version)
        val bStart:ImageButton = findViewById(R.id.controller_bStart)
//        val bSchedule:ImageButton = findViewById(R.id.controller_bSchedule)
        val vSettings:ImageButton = findViewById(R.id.main_settings)
        val vMaster:LinearLayout = findViewById(R.id.controller_lMaster)
        vParent = findViewById(R.id.controller_lParent)

        vMaster.visibility = View.VISIBLE

        bStart.setOnClickListener {
            val timeStartCamera = timer.time + 3000L

//            Toast.makeText(this, "wird in ${3000L / 1000L}s starten", Toast.LENGTH_SHORT).show()
            registerStartCamera(profile.createStart(timeStartCamera))
        }

        vSettings.setOnClickListener {
            vSettings.isEnabled = false
            profile.inflateDialog(this) {
                profile = it
                vSettings.isEnabled = true
            }
        }

//        bSchedule.setOnClickListener {
//            val viewDialog = layoutInflater.inflate(R.layout.view_schedule, null)
//            val vTimePicker = viewDialog.findViewById<TimePicker>(R.id.schedule_vTimePicker)
//
//            vTimePicker.setIs24HourView(true)
//            AlertDialog.Builder(this)
//                .setView(viewDialog)
//                .setPositiveButton("ok") { a, _ ->
//                    val calendar = Calendar.getInstance()
//                    calendar.time = Date()
//                    calendar[Calendar.HOUR_OF_DAY] = vTimePicker.hour
//                    calendar[Calendar.MINUTE] = vTimePicker.minute
//                    calendar[Calendar.SECOND] = 0
//
//                    val withConfig = DefaultCameraConfig.default.generateInstance(this, calendar.timeInMillis)
//
//                    registerStartCamera(withConfig)
//                    a.dismiss()
//                }
//                .show()
//        }

        vVersion.text = "v$version"

        //  UI Loop
        thread {
            while (!this.isDestroyed) {
                val time = timer.time

                runOnUiThread {
                    try {
                        val modText = Globals.formatTimeToSeconds.format(Date(time)) +
                            "\n\nKommando ${Profile.headerCommand[profile.command.ordinal]}" +
                            "\nDauer ${profile.millisVideoLength / 1000L}s"

                        tText.text = modText
                    } catch (_:Exception) {}
                }

                //  start planned cameras
                listPlannedCameraStarts.filter { it.timePreview - 3_000L < timer.time }.forEach { startCamera(it) }

                Thread.sleep(50)
            }
        }
    }

    private fun registerStartCamera(config: StartInstance) {
        listRegisteredCamerasStarts.add(config.timePreview)
        listPlannedCameraStarts.add(config)

        mapCameraInstanceToView[config] = ViewInstance(this, vParent)
            .also { view ->
                runOnUiThread {
                    view.vViewText.text = "start ${Globals.formatTimeToSeconds.format(Date(config.timePreview))}"
                    vParent.addView(view.container, 1)
                }
            }
    }

    private fun startCamera(config: StartInstance) {
        ActivityCamera.startCamera(this, config, timer) {
            mapCameraInstanceToView[config]!!.update(config)
        }

        listPlannedCameraStarts.remove(config)
    }
}