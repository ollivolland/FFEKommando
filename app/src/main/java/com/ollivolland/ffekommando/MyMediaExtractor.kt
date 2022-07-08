package com.ollivolland.ffekommando

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import java.nio.ByteBuffer

class MyMediaExtractor(val path:String) {

    val frameToMs:Map<Int, Long>
    val frameToUs:Map<Int, Long>
    val frameCount:Int
    val durationMs:Long
    val mediaMetadataRetriever = MediaMetadataRetriever()
    val fps:Double

    init {
        val mutableFrameToUs = mutableMapOf<Int, Long>()
        val extractor = MediaExtractor()
        extractor.setDataSource(path)

        val i = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.contains("video") }
        val format = extractor.getTrackFormat(i)
        val bufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)

        extractor.selectTrack(i)
        val inputBuffer = ByteBuffer.allocate(bufferSize)
        var index = 0

        while (extractor.readSampleData(inputBuffer, 0) >= 0) {
            mutableFrameToUs[index] = extractor.sampleTime

            extractor.advance()
            index++
        }

        frameToUs = mutableFrameToUs
        frameToMs = frameToUs.mapValues { (it.value * 0.001).toLong() }
        frameCount = index - 1
        durationMs = frameToMs.maxOf { it.value }
        fps = frameCount / (durationMs * 0.001)

        extractor.release()

        mediaMetadataRetriever.setDataSource(path)
    }

    fun frame(frameNum: Int): Bitmap {
        if(Build.VERSION.SDK_INT <= 28) return mediaMetadataRetriever.getFrameAtTime(frameToUs[frameNum]!!, MediaMetadataRetriever.OPTION_CLOSEST)!!

        return mediaMetadataRetriever.getFrameAtIndex(frameNum)!!
    }

    fun release() {
        mediaMetadataRetriever.release()
    }
}