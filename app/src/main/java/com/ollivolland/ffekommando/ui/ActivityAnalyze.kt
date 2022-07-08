package com.ollivolland.ffekommando.ui

import android.annotation.SuppressLint
import android.media.*
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.concurrent.thread
import com.ollivolland.ffekommando.*


class ActivityAnalyze : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analyze)

        //  UI
        val vParent = findViewById<LinearLayout>(R.id.analyze_lParent)
        val vText = findViewById<TextView>(R.id.analyze_tText)

        //  Files
//        val dir = File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath}/Camera")
        val mydir = File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath}/Camera/Analyze")
//        val lastPath = dir.listFiles()!!.toMutableList()
//            .filter { x -> x.path.endsWith(".mp4") }
//            .maxBy { x -> File(x.absolutePath).lastModified() }.absolutePath

        val paths = mydir.listFiles()!!.map { it.absolutePath }.toMutableList()
//        paths.add(lastPath)

        vText.text = "will analyze ${paths.count()} files"

        thread(name = "analyzer thread") {
            for (path in paths) {
                val extractor = MyMediaExtractor(path)
                val startTimeMs = Analyzer.getTimeToStartMs(path)

                Analyzer.analyze(extractor, { interests ->
                    interests.forEachIndexed { i, interest ->
                        val bitmap = extractor.frame(interest.best)
                        var inflatedView: View? = null
                        var image: ImageView? = null
                        var text: TextView? = null
                        var isInit = false

                        runOnUiThread {
                            inflatedView = View.inflate(this, R.layout.view_image, null)
                            image = inflatedView!!.findViewById(R.id.image_iImage)
                            text = inflatedView!!.findViewById(R.id.image_tText)

                            image!!.setImageBitmap(bitmap)
                            val textFrameTime = "$i : (${
                                path.split('/').last()
                            })\nframe time = ${"%.3f".format((extractor.frameToMs[interest.best]!! - startTimeMs) * 0.001)} s"
                            text!!.text = textFrameTime
                            vParent.addView(inflatedView)

                            isInit = true
                        }

                        while (!isInit) Thread.sleep(10)

                        val (static, timeTriangulatedMs) = Analyzer.getDifferenceBitmap(
                            extractor,
                            interest.best
                        )
                        runOnUiThread {
                            val textTriangulated =
                                "\ntriangulated = ${"%.3f".format((timeTriangulatedMs - startTimeMs) * 0.001)} s"

                            text!!.text = text!!.text.toString() + textTriangulated
                            image!!.setImageBitmap(static)
                        }

                    }
                }, { runOnUiThread { vText.text = it } })

                extractor.release()
            }
        }
    }
}