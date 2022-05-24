package com.ollivolland.ffekommando

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.lang.Exception
import java.text.SimpleDateFormat


class ActivityMain: AppCompatActivity()
{
    var isFirstLocation = true
    lateinit var androidIdd:String
    lateinit var bMaster:Button
    lateinit var bSlave:Button
    lateinit var sCamera:SwitchMaterial
    lateinit var sCommand:SwitchMaterial
    lateinit var db: DataBaseWrapper
    lateinit var locationManager:LocationManager
    private val locationListener = object : LocationListener {
        @SuppressLint("SimpleDateFormat")
        override fun onLocationChanged(location: Location) {
                val time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS").format(location.time)

                val nanosNow = android.os.SystemClock.elapsedRealtimeNanos()
                val nanosReceivedLocation = location.elapsedRealtimeNanos
                val dMillis = (nanosNow - nanosReceivedLocation) / 1_000_000L
                val thisDelay = (location.time + dMillis) - System.currentTimeMillis()

                if (isFirstLocation) isFirstLocation = false
                else delayList.add(thisDelay)
                if (delayList.count() > 1000) delayList.removeAt(0)

                Log.v(
                    "LOCATION",
                    "Time GPS: $time, delay = $thisDelay (${location.provider}) => $delay" +
                            ", stdev = ${delayList.stdev().format(2)}"
                )
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //  TODO save state

        //  Permissions
        val needed = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

        if(needed.isNotEmpty()) this.requestPermissions(needed, 0)

        androidIdd = Globals.getDeviceId(this)

        bMaster = findViewById(R.id.main_bMaster)
        bSlave = findViewById(R.id.main_bSlave)
        sCamera = findViewById(R.id.main_sCamera)
        sCommand = findViewById(R.id.main_sCommand)
        val vSpinnerCommand = findViewById<Spinner>(R.id.spinnerCommand)
        val vSpinnerDuration = findViewById<Spinner>(R.id.spinnerDuration)
        val tText:TextView = findViewById(R.id.main_tDescription)

        bMaster.setOnClickListener { startMaster() }
        bSlave.setOnClickListener { startSlave() }
        CameraConfig.default.isCamera = sCamera.isChecked
        sCamera.setOnClickListener { CameraConfig.default.isCamera = sCamera.isChecked }
        CameraConfig.default.isCommand = sCommand.isChecked
        sCommand.setOnClickListener { CameraConfig.default.isCommand = sCommand.isChecked }

        tText.append("version = $versionName")

        val selectionCommandBuilder = arrayOf("feuerwehr", "leichtathletik10", "leichtathletik30")
        configSpinner(vSpinnerCommand, arrayOf("feuerwehr", "leichtathletik10", "leichtathletik30")) { i ->
            CameraConfig.default.commandBuilder = selectionCommandBuilder[i]
        }

        val selectionDuration = arrayOf(60_000L, 100_000L, 10_000L)
        configSpinner(vSpinnerDuration, arrayOf("60 sek", "100 sek", "10 sek")) { i ->
            CameraConfig.default.millisVideoDuration = selectionDuration[i]
        }

        db = DataBaseWrapper(this)
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
        db["masters/$androidIdd",
            { task ->
                if(task.isSuccessful) startActivity(Intent(this, ActivityMaster::class.java).putExtra("ID", androidIdd))
                else Toast.makeText(this, "failure", Toast.LENGTH_LONG).show()
            }
        ] = hashMapOf(
            "isActive" to true,
            "activeSince" to correctedTime
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
                        startActivity(
                            Intent(this, ActivitySlave::class.java)
                                .putExtra("MASTER_ID", masters[which]))

                        bSlave.isEnabled = true
                    }.setOnDismissListener {
                    bSlave.isEnabled = true
                }.create().show()
            }
        }]
    }

    private fun initialiseLocationListener() {
        //  Check for permission
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
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
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
            .setCancelable(false)
            .setPositiveButton(
                "Yes"
            ) { dialog, id -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            .setNegativeButton(
                "No"
            ) { dialog, id -> dialog.cancel(); finish() }
        val alert = builder.create()
        alert.show()
    }

    private val versionName:String get() = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName

    companion object {
        val delayList:MutableList<Long> = mutableListOf()
        val delay:Long get() = if(delayList.isEmpty()) 0 else delayList.mean().toLong()
        val correctedTime:Long get() = System.currentTimeMillis() + delay
    }
}