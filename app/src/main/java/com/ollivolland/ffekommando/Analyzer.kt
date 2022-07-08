package com.ollivolland.ffekommando

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.*
import org.jcodec.containers.mp4.boxes.MetaValue
import org.jcodec.movtool.MetadataEditor
import org.json.JSONObject
import java.io.File
import kotlin.math.sign
import kotlin.math.sqrt

class Analyzer {
    companion object {
        const val MIN_LINE_BROKEN_FOR_REGISTER = 0.1
        const val MIN_LINE_BROKEN_FOR_BODY = 0.7

        fun analyze(extractor: MyMediaExtractor, func:(List<Interest>) -> Unit, progress:(String) -> Unit = {}) {
            val start = extractor.frameToMs.entries.filter { it.value > getTimeToStartMs(extractor.path) }.minBy { it.value }.key
            var frameLast: Bitmap = extractor.frame(start)
            var isPart = false
            val partBroken = mutableListOf<Double>()
            val listFrameIndex = mutableListOf<Int>()
            val listFirstFrameWithBodyIndex = mutableListOf<Int>()
            val listInterests = mutableListOf<Interest>()

            for (i in start + 1 until extractor.frameCount) {
                val frameCurrent: Bitmap = extractor.frame(i)
                val xOfLine = frameCurrent.width / 2
                val thisLineBroken = percentBroken(frameCurrent, frameLast, xOfLine)

                isPart = if(thisLineBroken > MIN_LINE_BROKEN_FOR_REGISTER && i != extractor.frameCount - 1) {
                    if(!isPart) listFrameIndex.add(i)

                    partBroken.add(thisLineBroken)
                    true
                } else {
                    if(isPart) {
                        val partMax = partBroken.max()
                        listFirstFrameWithBodyIndex.add(listFrameIndex.last() + partBroken.indexOfFirst { x -> x >= partMax * MIN_LINE_BROKEN_FOR_BODY })

                        partBroken.clear()
                        listInterests.add(Interest(listFrameIndex.last(), i - 1, listFirstFrameWithBodyIndex.last()))
                    }

                    false
                }

                frameLast = frameCurrent
                progress("progress = ${"%.0f".format((i.toDouble() / extractor.frameCount)*100)}%")
            }

            progress("found ${listInterests.count()} instances")
            func(listInterests.take(5))
        }

        fun getTimeToStartMs(path:String):Long {
            val mediaMeta: MetadataEditor = MetadataEditor.createFrom(File(path))
            val meta: MutableMap<String, MetaValue> = mediaMeta.keyedMeta
            val json = JSONObject(meta["com.apple.quicktime.title"].toString())
            return (Globals.formatToMillis.parse(json["dateCommand"].toString())!!.time - Globals.formatToMillis.parse(json["dateVideoStart"].toString())!!.time)
        }

        fun getDifferenceBitmap(extractor: MyMediaExtractor, frameIndex:Int, initXOfLine:Int = -1): Pair<Bitmap, Long> {
            val thisBitmap = extractor.frame(frameIndex)
            val lastBitmap = extractor.frame(frameIndex-1)
            val lastlastBitmap = extractor.frame(frameIndex-2)
            val width = thisBitmap.width
            val height = thisBitmap.height
            val config = thisBitmap.config
            val xOfLine = if(initXOfLine == -1) width / 2 else initXOfLine
            val outMap = Bitmap.createBitmap(width, height, config)

            for (index in 0 until width * height) {
                val x = index % width
                val y = index / width

                outMap[x, y] = if(isBroken(thisBitmap[x, y], lastBitmap[x, y])) Color.RED else thisBitmap[x, y]
            }

            val firstBody = xOfBroken(thisBitmap, lastBitmap)
            val previousBody = xOfBroken(lastBitmap, lastlastBitmap)
            val direction = (firstBody.first - previousBody.first + firstBody.second - previousBody.second).sign
            val firstX = if(direction == -1) firstBody.first else firstBody.second
            val previousX = if(direction == -1) previousBody.first else previousBody.second

            val amountOfDeltasToLine = (xOfLine - firstX).toDouble() / (firstX - previousX)
            val deltaTime = (extractor.frameToMs[frameIndex]!! - extractor.frameToMs[frameIndex - 1]!!)
            val triangulated = (extractor.frameToMs[frameIndex]!! + deltaTime * amountOfDeltasToLine).toLong()

            paintBarOnBitmap(outMap, xOfLine, 4, Color.GREEN)
            paintBarOnBitmap(outMap, firstX, 2, Color.LTGRAY)
            paintBarOnBitmap(outMap, previousX, 2, Color.DKGRAY)

            return Pair(outMap, triangulated)
        }

        private fun xOfBroken(frameCurrent: Bitmap, frameLast: Bitmap):Pair<Int, Int> {
            val percentBroken = (0 until frameCurrent.width).map { x -> percentBroken(frameCurrent, frameLast, x) }.toTypedArray()
            val broken = percentBroken.max() * MIN_LINE_BROKEN_FOR_BODY

            return Pair(percentBroken.indexOfFirst { p -> p >= broken }, percentBroken.indexOfLast { p -> p >= broken })
        }

        private fun percentBroken(frameCurrent: Bitmap, frameLast: Bitmap, posX:Int):Double {
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