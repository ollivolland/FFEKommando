package com.ollivolland.ffekommando

import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.contains
import java.util.*
import kotlin.concurrent.thread

class ViewInstance(private val activity:Activity, private val vInstanceParent:ViewGroup) {

    var isExists = true
    val container:View
    val vParent:LinearLayout
    val vViewText:TextView
    val vExit:Button

    init {
        container = activity.layoutInflater.inflate(R.layout.view_instance, vInstanceParent, false)

        vViewText = container.findViewById(R.id.instance_tText)
        vParent = container.findViewById(R.id.instance_lParent)
        vExit = container.findViewById(R.id.instance_bExit)

        vExit.setOnClickListener { destroy() }
    }

    fun destroy() {
        isExists = false
        if(vInstanceParent.contains(container)) vInstanceParent.removeView(container)
    }

    fun update(path:String, config: CameraInstance) {
        if(!isExists) return

        vViewText.text = "[done] started at ${Globals.formatToTimeOfDay.format(Date(config.correctedTimeStartCamera))}"

        if(config.isAnalyze) thread {
            try {
            ActivityAnalyze.analyze(path, { interests ->
                //  Display
                interests.forEach { interest ->
                    if(Build.VERSION.SDK_INT < 28) return@forEach

                    val timeOfFrame = ActivityAnalyze.getTimesOfFramesMillis(path)
                    val timeToStart = ActivityAnalyze.getTimeToStartMillis(path)
                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    mediaMetadataRetriever.setDataSource(path)

                    activity.runOnUiThread {
                        val inflatedView: View = View.inflate(activity, R.layout.view_image, null)
                        val image = inflatedView.findViewById<ImageView>(R.id.image_iImage)
                        val image2 = inflatedView.findViewById<ImageView>(R.id.image_iImage2)
                        val text = inflatedView.findViewById<TextView>(R.id.image_tText)
                        image.setImageBitmap(mediaMetadataRetriever.getFrameAtIndex(interest.best)!!)

                        thread {
                            val static = ActivityAnalyze.getDifferenceBitmap(mediaMetadataRetriever, interest.best) { times ->
                                val triangulated = timeOfFrame[interest.best]!! + times * (timeOfFrame[interest.best]!! - timeOfFrame[interest.best - 1]!!) - timeToStart
                                activity.runOnUiThread { text.text = text.text.toString() + "\ntriangulated = ${"%.3f".format(triangulated * 0.001)} s" }
                            }
                            activity.runOnUiThread { image2.setImageBitmap(static) }
                        }
                        text.text = "frame time = ${"%.3f".format((timeOfFrame[interest.best]!! - timeToStart) * 0.001)} s"

                        vParent.addView(inflatedView)
                    }
                }
            })
            } catch (e:Exception) {}
        }
    }
}