package com.ollivolland.ffekommando

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase


class ActivityMain: AppCompatActivity()
{
    lateinit var androidIdd:String
    lateinit var bMaster:Button
    lateinit var bSlave:Button
    lateinit var read: DataBaseReader
    lateinit var write: DataBaseWriter

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //  Permissions
        val needed = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()

        needed.forEachIndexed { i, s ->this.requestPermissions(arrayOf(s), i)}

        androidIdd = Globals.getDeviceId(this)

        bMaster = findViewById(R.id.main_bMaster)
        bMaster.setOnClickListener { startMaster() }

        bSlave = findViewById(R.id.main_bSlave)
        bSlave.setOnClickListener { startSlave() }

        val tText:TextView = findViewById(R.id.main_tDescription)
        tText.append("version = ${Globals.VERSION_STRING}")

//        val dropdown = findViewById<Spinner>(R.id.spinner1)
//        val items = arrayOf("1080p", "2160p (4k)")
//        val adapter = ArrayAdapter(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, items)
//        dropdown.adapter = adapter
//        dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
//            override fun onNothingSelected(parent: AdapterView<*>?) {
//            }
//
//            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
//                //if(position == 0) profile = CamcorderProfile.QUALITY_1080P
//                //if(position == 1) profile = CamcorderProfile.QUALITY_2160P
//            }
//        }
//        dropdown.setSelection(0)

        val wrapper = DataBaseWrapper(this)
        write = wrapper.writer
        read = wrapper.reader

        //  Do
        write["test/test1"] = hashMapOf("testKey" to "testValue")
//        write["test/test2"] = {
//            it.child("a").value = "value a"
//            it.child("b").value = "value b"
//            it.child("c").value = "value c"
//            it
//        }
//        read["test/test2"] = Pair(
//            { println("success") },
//            { println("fail") }
//        )

        //  Version
        read["currentVersion"] = {
            if(it.isSuccessful) {
                if (it.result.value.toString() == Globals.VERSION_STRING) tText.append("\nversion ist aktuell")
                else tText.append("\nlade dir die neue version vom onedrive runter!")
            }
        }
    }

    private fun startMaster()
    {
        bMaster.isEnabled = false

        write["masters/$androidIdd", hashMapOf<String, Any?>(
            "isActive" to true
        )] = {
            bMaster.isEnabled = true

            if (!it) Toast.makeText(this, "failure", Toast.LENGTH_LONG).show()
            else startActivity(Intent(this, ActivityMaster::class.java).putExtra("ID", androidIdd))
        }
    }

    private fun startSlave()
    {
        bSlave.isEnabled = false

        read["masters"] = {
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
                            Intent(this, ActivitySlave::class.java).putExtra(
                                "MASTER_ID",
                                masters[which]
                            )
                        )
                        bSlave.isEnabled = true
                    }.setOnDismissListener {
                    bSlave.isEnabled = true
                }.create().show()
            }
        }
    }
}