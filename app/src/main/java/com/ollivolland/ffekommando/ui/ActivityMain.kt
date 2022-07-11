package com.ollivolland.ffekommando.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.switchmaterial.SwitchMaterial
import com.ollivolland.ffekommando.*
import com.ollivolland.ffekommando.R
import java.text.SimpleDateFormat


class ActivityMain: AppCompatActivity()
{
    var isFirstLocation = true
    lateinit var androidIdd:String
    lateinit var bMaster:Button
    lateinit var bSlave:Button
    lateinit var sCamera:SwitchMaterial
    lateinit var sCommand:SwitchMaterial
    lateinit var sAnalyze:SwitchMaterial
    lateinit var db: MyDB
    lateinit var locationManager:LocationManager
    private val locationListener = object : LocationListener {
        @SuppressLint("SimpleDateFormat")
        override fun onLocationChanged(location: Location) {
            val time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS").format(location.time)
            val millisSinceReceived = (android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
            val thisDelay = (location.time + millisSinceReceived) - System.currentTimeMillis()

            if (isFirstLocation) isFirstLocation = false
            else delayList.add(thisDelay)
            if (delayList.count() > 120) delayList.removeAt(0)

            Log.v(
                "LOCATION",
                "Time GPS: $time, delay = $thisDelay (${location.provider}) => $delay" +
                        ", stdev = ${delayList.stdev().format(2)}"
            )

            delay = if(delayList.isEmpty()) 0 else delayList.mean().toLong()
            delayStdDev = if(delayList.isEmpty()) 0.0 else delayList.stdev()
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //  TODO save state

        //  Permissions
        if(Build.VERSION.SDK_INT > 22) {
            val needed = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

            if (needed.isNotEmpty()) this.requestPermissions(needed, 0)
        }

        androidIdd = Globals.getDeviceId(this)

        bMaster = findViewById(R.id.main_bMaster)
        bSlave = findViewById(R.id.main_bSlave)
        val bAnalyze:Button = findViewById(R.id.main_bAnalyze)
        sCamera = findViewById(R.id.main_sCamera)
        sCommand = findViewById(R.id.main_sCommand)
        sAnalyze = findViewById(R.id.main_sAnalyze)
        val vSpinnerCommand = findViewById<Spinner>(R.id.spinnerCommand)
        val vSpinnerDuration = findViewById<Spinner>(R.id.spinnerDuration)
        val vSpinnerDelay = findViewById<Spinner>(R.id.spinnerDelay)
        val tText:TextView = findViewById(R.id.main_tDescription)

        bMaster.setOnClickListener { startMaster() }
        bSlave.setOnClickListener { startSlave() }
        bAnalyze.setOnClickListener { startActivity(Intent(this, ActivityAnalyze::class.java)) }
        DefaultCameraConfig.default.isCamera = sCamera.isChecked
        sCamera.setOnClickListener { DefaultCameraConfig.default.isCamera = sCamera.isChecked }
        DefaultCameraConfig.default.isCommand = sCommand.isChecked
        sCommand.setOnClickListener { DefaultCameraConfig.default.isCommand = sCommand.isChecked }
        DefaultCameraConfig.default.isAnalyze = sAnalyze.isChecked
        sAnalyze.setOnClickListener { DefaultCameraConfig.default.isAnalyze = sAnalyze.isChecked }

        tText.append("version = $versionName")

        val selectionCommandBuilder = arrayOf("feuerwehr", "feuerwehr_slowenisch", "leichtathletik10", "leichtathletik20", "leichtathletik30", "feuerwehrstaffel")
        configSpinner(vSpinnerCommand, arrayOf("feuerwehr", "feuerwehr slowenisch", "leichtathletik 10 sek", "leichtathletik 20 sek", "leichtathletik 30 sek", "feuerwehrstaffel")) { i ->
            DefaultCameraConfig.default.commandBuilder = selectionCommandBuilder[i]
        }

        val selectionDuration = arrayOf(60_000L, 30_000L, 100_000L, 10_000L)
        configSpinner(vSpinnerDuration, arrayOf("60 sek", "30 sek", "100 sek", "10 sek")) { i ->
            DefaultCameraConfig.default.millisVideoDuration = selectionDuration[i]
        }

        val selectionDelay = arrayOf(3_000L, 10_000L, 60_000L, 150_000L, 300_000L, 600_000L)
        configSpinner(vSpinnerDelay, arrayOf("Δ+3 sek", "Δ+10 sek", "Δ+60 sek", "Δ+150 sek", "Δ+300 sek", "Δ+600 sek")) { i ->
            DefaultCameraConfig.default.millisDelay = selectionDelay[i]
        }

        db = MyDB(this)
        db["test/test1/testKey"] = "testValue"

        //  Version
        db["currentVersion",
        {
            if(it.isSuccessful) {
                if (it.result.value.toString() == versionName) tText.append("\nversion ist aktuell")
                else tText.append("\nVERSION IST VERALTET\nlösche diese Version.\nGehe dann in onedrive, klicke bei der neuen version auf herunterladen.\nGehe dann auf Dateien/Downloads und installiere sie.")
            }
        }]

        //  GPS
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) buildAlertMessageNoGps()
        initialiseLocationListener()
    }

    override fun onDestroy() {
        super.onDestroy()

        locationManager.removeUpdates(locationListener)
    }

    private fun configSpinner(spinner: Spinner, headers: Array<String>, onSelect: (Int) -> Unit) {
        val adapter = ArrayAdapter(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, headers)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelect(position)
            }
        }
        spinner.setSelection(0)
    }

    private fun startMaster()
    {
        val timerSynchronized = timerSynchronized

        db["masters/$androidIdd",
            { task ->
                if(task.isSuccessful) ActivityController.launchMaster(this, androidIdd)
                else Toast.makeText(this, "failure", Toast.LENGTH_LONG).show()
            }
        ] = hashMapOf(
            "isActive" to true,
            "activeSince" to timerSynchronized.time
        )
    }

    private fun startSlave()
    {
        bSlave.isEnabled = false

        db["masters", {
            if(!it.isSuccessful)
            {
                Toast.makeText(this, "failure", Toast.LENGTH_LONG).show()
                bSlave.isEnabled = true
            }
            else {
                val builder = AlertDialog.Builder(this)
                val masters = it.result.children.map { child -> child.key.toString() }.toTypedArray()

                builder.setTitle("wähle den Meister (${it.result.childrenCount} vorhanden)")
                    .setItems(masters) { _, which ->
                        ActivityController.launchSlave(this, androidIdd, masters[which])
                        bSlave.isEnabled = true
                    }.setOnDismissListener {
                    bSlave.isEnabled = true
                }.create().show()
            }
        }]
    }

    private fun initialiseLocationListener() {
        //  Check for permission
        if (Build.VERSION.SDK_INT > 22 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LOCATION", "Incorrect 'uses-permission', requires 'ACCESS_FINE_LOCATION'")
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            100,
            0f,
            locationListener
        )
    }

    private fun buildAlertMessageNoGps() {
        val locationRequest = LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setInterval(100).setFastestInterval(100)
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest).setAlwaysShow(true)
        val client: SettingsClient = LocationServices.getSettingsClient(this)

        client.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                Toast.makeText(this, "Gps is enabled", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(this, 1000)
                    } catch (sendEx: SendIntentException) {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                } else startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
    }

    private val versionName:String get() = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName

    companion object {
        private val delayList:MutableList<Long> = mutableListOf()
        var delay:Long = 0
        var delayStdDev:Double = 0.0
        val timerSynchronized: MyTimer get() = MyTimer(delay)
    }
}