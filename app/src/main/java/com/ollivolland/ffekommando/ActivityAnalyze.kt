package com.ollivolland.ffekommando

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.media.*
import android.os.Build
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
import java.io.File
import java.nio.ByteBuffer
import kotlin.collections.set
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt


class ActivityAnalyze : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analyze)

        //  UI
        val vParent = findViewById<LinearLayout>(R.id.analyze_lParent)
        val vText = findViewById<TextView>(R.id.analyze_tText)
        val vText2 = findViewById<TextView>(R.id.analyze_tText2)

        //  Files
        val dir = File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath}/Camera")
        val path = dir.listFiles()!!.toMutableList().maxBy { x -> File(x.absolutePath).lastModified() }.absolutePath

        //  Metadata
        val mediaMeta: MetadataEditor = MetadataEditor.createFrom(File(path))
        val meta: MutableMap<String, MetaValue> = mediaMeta.keyedMeta
        val json = JSONObject(meta["com.apple.quicktime.title"].toString())
        val timeToStart = (Globals.formatToMillis.parse(json["dateCommand"].toString())!!.time - Globals.formatToMillis.parse(json["dateVideoStart"].toString())!!.time) * 0.001

        //  accurate frame time
        val timeOfFrame = mutableMapOf<Int, Double>()
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)

            if (mime!!.contains("video")) {
                val bufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)

                extractor.selectTrack(i)
                val inputBuffer = ByteBuffer.allocate(bufferSize)
                var index = 0

                while (extractor.readSampleData(inputBuffer, 0) >= 0) {
                    timeOfFrame[index] = extractor.sampleTime * 0.000001 - timeToStart

                    extractor.advance()
                    index++
                }
            }
        }
        extractor.release()

        //  Video analysis
        if(Build.VERSION.SDK_INT <= 28) return

        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(path)
        val frameCount = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt()
        val duration =  mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        val fps = frameCount / (duration * 0.001)

        vText.text = "frameCount = $frameCount\nfps = ${fps.format(2)}\nduration = $duration"

        val start = (timeToStart * fps).toInt()
        var frameLast: Bitmap = mediaMetadataRetriever.getFrameAtIndex(start)!!
        var isPart = false
        val partBroken = mutableListOf<Double>()
        val listFrameIndex = mutableListOf<Int>()
        val listFirstFrameWithBodyIndex = mutableListOf<Int>()

        thread {
            for (i in start + 1 until frameCount) {
                val frameCurrent: Bitmap = mediaMetadataRetriever.getFrameAtIndex(i)!!
                val xOfLine = frameCurrent.width / 2
                val thisLineBroken = percentBroken(frameCurrent, frameLast, xOfLine)

                isPart = if(thisLineBroken > 0.1 && i != frameCount - 1) {
                    if(!isPart) listFrameIndex.add(i)

                    partBroken.add(thisLineBroken)
                    true
                } else {
                    if(isPart) {
                        val partMax = partBroken.max()
                        listFirstFrameWithBodyIndex.add(listFrameIndex.last() + partBroken.indexOfFirst { x -> x >= partMax * 0.8 })

                        partBroken.clear()
                    }

                    false
                }

                frameLast = frameCurrent

                if(i % 10 == 0) runOnUiThread { vText2.text = "frame num $i" }
            }

            //  Display
            runOnUiThread {
                listFrameIndex.forEachIndexed { index, _ ->
                    val frameIndexCrossedWithBody = listFirstFrameWithBodyIndex[index]
                    val inflatedView: View = View.inflate(this, R.layout.view_image, null)
                    val image = inflatedView.findViewById<ImageView>(R.id.image_iImage)
                    val image2 = inflatedView.findViewById<ImageView>(R.id.image_iImage2)
                    val text = inflatedView.findViewById<TextView>(R.id.image_tText)
                    image.setImageBitmap(mediaMetadataRetriever.getFrameAtIndex(frameIndexCrossedWithBody)!!)
                    thread {
                        val static = getDifferenceBitmap(mediaMetadataRetriever, frameIndexCrossedWithBody,
                            frameLast.width, frameLast.height, frameLast.config, frameLast.width / 2
                        ) { wheightFirst, wheightLast ->    //  ORDER IMPORTANT!!!!
                            runOnUiThread { text.text = text.text.toString() + "\ntriangulated = ${"%.3f".format(timeOfFrame[frameIndexCrossedWithBody]!! * wheightFirst + timeOfFrame[frameIndexCrossedWithBody - 1]!! * wheightLast)} s" }
                        }
                        runOnUiThread { image2.setImageBitmap(static) }
                    }
                    text.text = "acc time = ${"%.3f".format(timeOfFrame[frameIndexCrossedWithBody]!!)} s"

                    vParent.addView(inflatedView)
                }
            }
        }
    }

    private fun getDifferenceBitmap(retriever: MediaMetadataRetriever, frameIndex:Int, width:Int, height:Int, config:Bitmap.Config, xOfLine:Int, lambda:(Double, Double) -> Unit):Bitmap {
        if (Build.VERSION.SDK_INT <= 28) throw Exception("wrong sdk")

        val outMap = Bitmap.createBitmap(width, height, config)

        val thisBitmap = retriever.getFrameAtIndex(frameIndex)!!
        val lastBitmap = retriever.getFrameAtIndex(frameIndex-1)!!

        for (index in 0 until width * height) {
            val x = index % width
            val y = index / width

            outMap[x, y] = if(distanceEuclidean(thisBitmap[x, y], lastBitmap[x, y]) < 0.1) Color.BLACK else Color.RED
        }

        val firstBody = xOfBroken(thisBitmap, lastBitmap)
        val previousBody = xOfBroken(lastBitmap, retriever.getFrameAtIndex(frameIndex-2)!!)
        val directionCheck = xOfBroken(retriever.getFrameAtIndex(frameIndex-5)!!, retriever.getFrameAtIndex(frameIndex-6)!!)

        val direction = (firstBody.first - directionCheck.first + firstBody.second - directionCheck.second).sign

        val firstX: Int
        val previousX: Int

        if(direction == -1) {
            firstX = firstBody.first
            previousX = previousBody.first
        } else {
            firstX = firstBody.second
            previousX = previousBody.second
        }

        val deltaTot = (firstX - previousX).absoluteValue.toDouble()
        val first = (firstX - xOfLine).absoluteValue
        val previous = (previousX - xOfLine).absoluteValue
        lambda(1 - first / deltaTot, 1 - previous / deltaTot)

        paintBarOnBitmap(outMap, firstX, 2, Color.LTGRAY)
        paintBarOnBitmap(outMap, previousX, 2, Color.DKGRAY)
        paintBarOnBitmap(outMap, xOfLine, 2, Color.GREEN)

        return outMap
    }

    private fun xOfBroken(frameCurrent:Bitmap, frameLast:Bitmap):Pair<Int, Int> {
        val percentBroken = (0 until frameCurrent.width).map { x -> percentBroken(frameCurrent, frameLast, x) }.toTypedArray()
        val maxBroken = percentBroken.max()

        return Pair(percentBroken.indexOfFirst { p -> p >= maxBroken * 0.8 }, percentBroken.indexOfLast { p -> p >= maxBroken * 0.8 })
    }

    private fun percentBroken(frameCurrent:Bitmap, frameLast:Bitmap, posX:Int):Double {
        val thresholdSquared = 0.1.pow(2)

        return (0 until frameCurrent.height).count { y ->
            distanceEuclideanSquared(
                frameCurrent[posX, y],
                frameLast[posX, y]
            ) > thresholdSquared
        }.toDouble() / frameCurrent.height
    }

    private inline fun paintBarOnBitmap(bitmap: Bitmap, xCenter:Int, width: Int, color: Int) {
        for (x in xCenter-width until  xCenter+width + 1)
            for (y in 0 until bitmap.height)
                bitmap[x, y] = color
    }

    private fun distanceEuclidean(a: Int, b: Int): Double {
        val deltaR: Int = (a.red - b.red)
        val deltaG: Int = (a.green - b.green)
        val deltaB: Int = (a.blue - b.blue)

        return sqrt((deltaR * deltaR + deltaG * deltaG + deltaB * deltaB).toDouble() / 195075)
    }

    private fun distanceEuclideanSquared(a: Int, b: Int): Double {
        val deltaR: Int = (a.red - b.red)
        val deltaG: Int = (a.green - b.green)
        val deltaB: Int = (a.blue - b.blue)

        return (deltaR * deltaR + deltaG * deltaG + deltaB * deltaB).toDouble() / 195075
    }
}