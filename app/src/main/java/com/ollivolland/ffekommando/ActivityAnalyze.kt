package com.ollivolland.ffekommando

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.*
import org.jcodec.containers.mp4.boxes.MetaValue
import org.jcodec.movtool.MetadataEditor
import org.json.JSONObject
import org.w3c.dom.Text
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.sqrt


class ActivityAnalyze : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analyze)

        val vImage = findViewById<ImageView>(R.id.analyze_iImage)
        val vImage2 = findViewById<ImageView>(R.id.analyze_iImage2)
        val vParent = findViewById<LinearLayout>(R.id.analyze_lParent)
        val vText = findViewById<TextView>(R.id.analyze_tText)
        val vText2 = findViewById<TextView>(R.id.analyze_tText2)

        val dir = File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath}/Camera")
        val path = dir.listFiles()!!.toMutableList().maxBy { x -> File(x.absolutePath).lastModified() }.absolutePath

        val mediaMeta: MetadataEditor = MetadataEditor.createFrom(File(path))
        val meta: MutableMap<String, MetaValue> = mediaMeta.keyedMeta
        val json = JSONObject(meta["com.apple.quicktime.title"].toString())
        val timeToStart = (Globals.formatToMillis.parse(json["dateCommand"].toString())!!.time - Globals.formatToMillis.parse(json["dateVideoStart"].toString())!!.time) * 0.001

        if(android.os.Build.VERSION.SDK_INT >= 28) {
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(path)
            val frameCount = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt()
            val duration =  mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            val fps = frameCount / (duration * 0.001)

            vText.text = "frameCount = $frameCount\nfps = ${fps.format(2)}\nduration = $duration"

            val start = (timeToStart * fps).toInt()
            var frameLast: Bitmap = mediaMetadataRetriever.getFrameAtIndex(start)!!
            var maxLineBroken = 0.0
            var timeFinish = 0.0
            var frameNumFinish = 0
            var isPart = false
            val listParts = mutableListOf<Int>()
            val listTimes = mutableListOf<Double>()

            thread {
                for (i in start + 1 until frameCount) {
                    val frameCurrent: Bitmap = mediaMetadataRetriever.getFrameAtIndex(i)!!
                    val xOfLine = frameCurrent.width / 2

//                    val frameDifference = Bitmap.createBitmap(frameCurrent.width, frameCurrent.height, frameCurrent.config)

//                    for (x in 0 until frameCurrent.width)
//                        for (y in 0 until frameCurrent.height) {
//                            frameDifference[x, y] = ColorUtils.blendARGB(Color.BLACK, Color.RED,
//                                min(distanceSquared(frameCurrent[x, y], frameLast[x, y]).toFloat() / 0.1f, 1f))
//                        }
//
//                    for (y in 0 until frameCurrent.height) frameDifference[xOfLine, y] = Color.GREEN

                    val thisLineBroken = (0 until frameCurrent.height).map { y -> if(distanceSquared(frameCurrent[xOfLine, y], frameLast[xOfLine, y]) > 0.1) 1.0 else 0.0 }.average()
                    if(thisLineBroken > 0.1) {
                        if(!isPart) {
                            listParts.add(i)
                            listTimes.add(i / fps - timeToStart)
                        }

                        maxLineBroken = thisLineBroken
                        timeFinish = i / fps - timeToStart
                        frameNumFinish = i
                        isPart = true
                    } else isPart = false

                    frameLast = frameCurrent

                    if(i % 10 == 0)
                        runOnUiThread {
    //                        vImage.setImageBitmap(frameCurrent)
    //                        vImage2.setImageBitmap(frameDifference)
                            vText2.text = "frame num $i\n\ntime finish = ${timeFinish.format(2)} s\npercentage of line broken = ${maxLineBroken.format(2)}\n\n" +
                                    "num found = ${listParts.count()}"
                        }
                }

                //  display
                runOnUiThread {
                    vImage.setImageBitmap(mediaMetadataRetriever.getFrameAtIndex(frameNumFinish)!!)

                    listParts.forEachIndexed { index, part ->
                        val inflatedView: View = View.inflate(this, R.layout.view_image, null)
                        val image = inflatedView.findViewById<ImageView>(R.id.image_iImage)
                        image.setImageBitmap(mediaMetadataRetriever.getFrameAtIndex(part)!!)
                        val text = inflatedView.findViewById<TextView>(R.id.image_tText)
                        text.text = "time = ${listTimes[index].format(3)} s"

                        vParent.addView(inflatedView)
                    }
                }
            }
        }
    }

    private inline fun distanceSquared(a: Int, b: Int): Double {
        val deltaR: Int = (a.red - b.red)
        val deltaG: Int = (a.green - b.green)
        val deltaB: Int = (a.blue - b.blue)

        return sqrt((deltaR * deltaR + deltaG * deltaG + deltaB * deltaB).toDouble() / 195075)
    }
}