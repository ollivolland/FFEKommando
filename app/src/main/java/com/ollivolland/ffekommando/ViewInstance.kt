package com.ollivolland.ffekommando

import android.app.Activity
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
    val lines = mutableListOf<String>()

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

        lines.add("[done] started at ${Globals.formatToTimeOfDay.format(Date(config.correctedTimeStartCamera))}")
        if(config.isAnalyze) lines.add("will analyze shortly")
        activity.runOnUiThread { vViewText.text = lines.joinToString("\n") }

        if(!config.isAnalyze) return

        thread(name = "analyzer thread") {
            try {
                val extractor = MyMediaExtractor(path)
                val startTimeMs = Analyzer.getTimeToStartMs(path)

                Analyzer.analyze(extractor, { interests ->
                    interests.forEach { interest ->
                        val bitmap = extractor.frame(interest.best)
                        var inflatedView: View? = null
                        var image: ImageView? = null
                        var text: TextView? = null
                        var isInit = false

                        activity.runOnUiThread {
                            inflatedView = View.inflate(activity, R.layout.view_image, null)
                            image = inflatedView!!.findViewById(R.id.image_iImage)
                            text = inflatedView!!.findViewById(R.id.image_tText)

                            image!!.setImageBitmap(bitmap)
                            val textFrameTime = "frame time = ${"%.3f".format((extractor.frameToMs[interest.best]!! - startTimeMs) * 0.001)} s"
                            text!!.text = textFrameTime
                            vParent.addView(inflatedView)

                            isInit = true
                        }

                        while (!isInit) Thread.sleep(10)

                        val (static, timeTriangulatedMs) = Analyzer.getDifferenceBitmap(extractor, interest.best)
                        activity.runOnUiThread {
                            val textTriangulated = "\ntriangulated = ${"%.3f".format((timeTriangulatedMs - startTimeMs) * 0.001)} s"

                            text!!.text = text!!.text.toString() + textTriangulated
                            image!!.setImageBitmap(static)
                        }

                    }
                }, {
                    lines[1] = it
                    activity.runOnUiThread { vViewText.text = lines.joinToString("\n") }
                })

                extractor.release()
            } catch (e:Exception) {}
        }
    }
}