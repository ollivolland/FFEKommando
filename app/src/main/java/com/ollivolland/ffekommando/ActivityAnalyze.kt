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
        val path = dir.listFiles()!!.toMutableList()
            .filter { x -> x.path.endsWith(".mp4") }
            .maxBy { x -> File(x.absolutePath).lastModified() }.absolutePath

        thread {
            analyze(path, { interests ->
                //  Display
                interests.forEach { interest ->
                    if(Build.VERSION.SDK_INT < 28) return@forEach

                    val timeOfFrame = getTimesOfFramesMillis(path)
                    val timeToStart = getTimeToStartMillis(path)
                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    mediaMetadataRetriever.setDataSource(path)

                    runOnUiThread {
                        val inflatedView: View = View.inflate(this, R.layout.view_image, null)
                        val image = inflatedView.findViewById<ImageView>(R.id.image_iImage)
                        val image2 = inflatedView.findViewById<ImageView>(R.id.image_iImage2)
                        val text = inflatedView.findViewById<TextView>(R.id.image_tText)
                        image.setImageBitmap(mediaMetadataRetriever.getFrameAtIndex(interest.best)!!)

                        thread {
                            val static = getDifferenceBitmap(mediaMetadataRetriever, interest.best) { times ->
                                val triangulated = timeOfFrame[interest.best]!! + times * (timeOfFrame[interest.best]!! - timeOfFrame[interest.best - 1]!!) - timeToStart
                                runOnUiThread { text.text = text.text.toString() + "\ntriangulated = ${"%.3f".format(triangulated * 0.001)} s" }
                            }
                            runOnUiThread { image2.setImageBitmap(static) }
                        }
                        text.text = "frame time = ${"%.3f".format((timeOfFrame[interest.best]!! - timeToStart) * 0.001)} s"

                        vParent.addView(inflatedView)
                    }
                }
            }, { progress ->
                runOnUiThread { vText.text = "progress = ${"%.2f".format(progress)}" }
            } )
        }
    }

    companion object {
        const val MIN_LINE_BROKEN_FOR_REGISTER = 0.1

        fun analyze(path:String, func:(List<Interest>) -> Unit, progress:(Double) -> Unit = {}) {
            if(Build.VERSION.SDK_INT <= 28) return

            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(path)
            val frameCount = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt()
            val duration =  mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            val fps = frameCount / (duration * 0.001)

            val start = (getTimeToStartMillis(path) * 0.001 * fps).toInt()
            var frameLast: Bitmap = mediaMetadataRetriever.getFrameAtIndex(start)!!
            var isPart = false
            val partBroken = mutableListOf<Double>()
            val listFrameIndex = mutableListOf<Int>()
            val listFirstFrameWithBodyIndex = mutableListOf<Int>()
            val listInterests = mutableListOf<Interest>()

            for (i in start + 1 until frameCount) {
                val frameCurrent: Bitmap = mediaMetadataRetriever.getFrameAtIndex(i)!!
                val xOfLine = frameCurrent.width / 2
                val thisLineBroken = percentBroken(frameCurrent, frameLast, xOfLine)

                isPart = if(thisLineBroken > MIN_LINE_BROKEN_FOR_REGISTER && i != frameCount - 1) {
                    if(!isPart) listFrameIndex.add(i)

                    partBroken.add(thisLineBroken)
                    true
                } else {
                    if(isPart) {
                        val partMax = partBroken.max()
                        listFirstFrameWithBodyIndex.add(listFrameIndex.last() + partBroken.indexOfFirst { x -> x >= partMax * 0.8 })

                        partBroken.clear()
                        listInterests.add(Interest(listFrameIndex.last(), i - 1, listFirstFrameWithBodyIndex.last()))
                    }

                    false
                }

                frameLast = frameCurrent
                progress(i.toDouble() / frameCount)
            }

            mediaMetadataRetriever.release()
            func(listInterests.take(5))
        }

        fun getTimesOfFramesMillis(path: String):Map<Int, Long> {
            val timeOfFrame = mutableMapOf<Int, Long>()
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
                        timeOfFrame[index] = (extractor.sampleTime * 0.001).toLong()

                        extractor.advance()
                        index++
                    }
                }
            }
            extractor.release()
            return timeOfFrame
        }

        fun getTimeToStartMillis(path:String):Long {
            val mediaMeta: MetadataEditor = MetadataEditor.createFrom(File(path))
            val meta: MutableMap<String, MetaValue> = mediaMeta.keyedMeta
            val json = JSONObject(meta["com.apple.quicktime.title"].toString())
            return (Globals.formatToMillis.parse(json["dateCommand"].toString())!!.time - Globals.formatToMillis.parse(json["dateVideoStart"].toString())!!.time)
        }

        fun getDifferenceBitmap(retriever: MediaMetadataRetriever, frameIndex:Int, initXOfLine:Int = -1, lambda:(Double) -> Unit):Bitmap {
            if (Build.VERSION.SDK_INT <= 28) throw Exception("wrong sdk")

            val thisBitmap = retriever.getFrameAtIndex(frameIndex)!!
            val lastBitmap = retriever.getFrameAtIndex(frameIndex-1)!!
            val width = thisBitmap.width
            val height = thisBitmap.height
            val config = thisBitmap.config
            val xOfLine = if(initXOfLine == -1) width / 2 else initXOfLine
            val outMap = Bitmap.createBitmap(width, height, config)

            for (index in 0 until width * height) {
                val x = index % width
                val y = index / width

                outMap[x, y] = if(isBroken(thisBitmap[x, y], lastBitmap[x, y])) Color.RED else Color.BLACK
            }

            val firstBody = xOfBroken(thisBitmap, lastBitmap)
            val previousBody = xOfBroken(lastBitmap, retriever.getFrameAtIndex(frameIndex-2)!!)
            val direction = (firstBody.first - previousBody.first + firstBody.second - previousBody.second).sign
            val firstX = if(direction == -1) firstBody.first else firstBody.second
            val previousX = if(direction == -1) previousBody.first else previousBody.second

            lambda((xOfLine - firstX).toDouble() / (firstX - previousX))

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
            return (0 until frameCurrent.height).count { y ->
                isBroken(frameCurrent[posX, y], frameLast[posX, y])
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

        private fun isBroken(a: Int, b: Int): Boolean {
            val deltaR: Int = (a.red - b.red)
            val deltaG: Int = (a.green - b.green)
            val deltaB: Int = (a.blue - b.blue)

            return (deltaR * deltaR + deltaG * deltaG + deltaB * deltaB) > 1952 //  195075 * 0.1^2
        }

        class Interest(val start:Int, val end:Int, val best:Int)
    }
}