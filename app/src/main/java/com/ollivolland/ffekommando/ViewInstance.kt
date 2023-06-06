package com.ollivolland.ffekommando

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.contains
import java.util.*
import kotlin.concurrent.thread

class ViewInstance(private val activity:Activity, private val vInstanceParent:ViewGroup) {

    var isExists = true
    val container:View = activity.layoutInflater.inflate(R.layout.view_instance, vInstanceParent, false)
    val vParent:RelativeLayout = container.findViewById(R.id.instance_lParent)
    val vViewText:TextView = container.findViewById(R.id.instance_tText)
    val vExit:ImageButton = container.findViewById(R.id.instance_bExit)
    private val lines = mutableListOf<String>()

    init {
        vExit.setOnClickListener { destroy() }
    }

    fun destroy() {
        isExists = false
        if(vInstanceParent.contains(container)) vInstanceParent.removeView(container)
    }

    fun update(path:String, config: CameraInstance) {
        if(!isExists) return

        lines.add("[done] started at ${Globals.formatTimeToSeconds.format(Date(config.correctedTimeStartCamera))}")
        activity.runOnUiThread { vViewText.text = lines.joinToString("\n") }
    }
}