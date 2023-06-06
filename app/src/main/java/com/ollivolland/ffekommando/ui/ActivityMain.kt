package com.ollivolland.ffekommando.ui

import android.Manifest
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
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.ollivolland.ffekommando.*
import com.ollivolland.ffekommando.R


class ActivityMain: AppCompatActivity() {
    private val version:Int by lazy { applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionCode }
    var isFirstLocation = true
    lateinit var androidIdd:String
    lateinit var bMaster:Button
    lateinit var bSlave:Button
    lateinit var sCamera:SwitchCompat
    lateinit var sCommand:SwitchCompat
    lateinit var db: MyDB
    lateinit var locationManager:LocationManager

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val thisTimeToBoot = location.time - location.elapsedRealtimeNanos / 1_000_000L

            if (isFirstLocation) isFirstLocation = false
            else timeToBootList.add(thisTimeToBoot)
            if (timeToBootList.count() > 300) timeToBootList.removeAt(0)

            Log.v(
                "LOCATION",
                "Time GPS: ${Globals.formatTimeToMillis.format(location.time)}, " +
                        "delay = $thisTimeToBoot (${location.provider})"
            )

            timeToBoot = if(timeToBootList.isEmpty()) 0 else timeToBootList.mean().toLong()
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        sCamera = findViewById(R.id.main_sCamera)
        sCommand = findViewById(R.id.main_sCommand)
        val vSpinnerCommand = findViewById<Spinner>(R.id.spinnerCommand)
        val vSpinnerDuration = findViewById<Spinner>(R.id.spinnerDuration)
        val vSpinnerDelay = findViewById<Spinner>(R.id.spinnerDelay)
        val tText:TextView = findViewById(R.id.main_tDescription)

        bMaster.setOnClickListener { startMaster() }
        bSlave.setOnClickListener { startSlave() }
        sCamera.setOnClickListener { DefaultCameraConfig.default.isCamera = sCamera.isChecked }
        sCommand.setOnClickListener { DefaultCameraConfig.default.isCommand = sCommand.isChecked }

        tText.append("sdk = ${Build.VERSION.SDK_INT}\nversion = $version")

        val selectionCommandBuilder = arrayOf("feuerwehr", "feuerwehr_slowenisch", "feuerwehrstaffel")
        configSpinner(vSpinnerCommand, arrayOf("feuerwehr", "feuerwehr slowenisch", "feuerwehrstaffel"))
        { i ->
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

        //  Version
        db["ffekommando/currentVersion", {
            if(it.isSuccessful) {
                if (it.result.value.toString().toInt() == version) tText.append("\n\nversion ist aktuell")
                else tText.append("\n\nVERSION IST VERALTET\nlösche diese Version.\nLade dei neue version aus onedrive herunter,\ndann suche in Downloads nach der .apk-Datei.\nInstalliere diese.")
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

    private fun startMaster() {
        db["masters/$androidIdd", { task ->
                if(task.isSuccessful) ActivityController.launchMaster(this, androidIdd)
                else Toast.makeText(this, "failure", Toast.LENGTH_LONG).show()
            }
        ] = hashMapOf("isActive" to true)
    }

    private fun startSlave() {
        bSlave.isEnabled = false

        db["masters", {
            if(!it.isSuccessful) {
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
        if (Build.VERSION.SDK_INT > 22 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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

    companion object {
        private val timeToBootList:MutableList<Long> = mutableListOf()
        private var timeToBoot:Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val timeToBootStdDev:Double get() = if(timeToBootList.isEmpty()) 0.0 else timeToBootList.stdev()
        val timerSynchronized: MyTimer get() = MyTimer(timeToBoot)
    }
}