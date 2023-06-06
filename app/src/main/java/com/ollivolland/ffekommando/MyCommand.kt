package com.ollivolland.ffekommando

import android.app.Activity
import android.media.MediaPlayer
import android.util.Log
import kotlin.concurrent.thread
import kotlin.random.Random

abstract class MyCommand(activity: Activity) {
    abstract val time:Long
    abstract val name:String
    var observedCommandLag:Long = -1
    abstract fun startAt(timer: MyTimer, time: Long)
    abstract fun prepare()
    abstract fun stopAndRelease()

    companion object {

        operator fun get(key:String, activity: Activity): MyCommand
        {
            MyCommandBuilder(activity, "test").apply {
                this[R.raw.startbefehl_5db] = 0L
                executionDelay = 19_000L
            }.build()

            when (key.split('&')[0]) {
                "feuerwehr" -> {
                    return MyCommandBuilder(activity, key).apply {
                        this[R.raw.startbefehl_5db] = 0L
                        executionDelay = 19_000L
                    }.build()
                }
                "feuerwehr_slowenisch" -> {
                    return MyCommandBuilder(activity, key).apply {
                        this[R.raw.startbefehl_slowenisch_5db] = 0L
                        executionDelay = 9_000L
                    }.build()
                }
                "feuerwehrstaffel" -> {
                    return MyCommandBuilder(activity, key).apply {
                        this[R.raw.startbefehl_slowenisch_5db] = 0L
                        executionDelay = 13_000L
                    }.build()
                }
                else -> throw Exception("false build command")
            }
        }
    }

    class MyCommandBuilder(private val activity: Activity, private val buildName: String) {

        var executionDelay:Long = 0
        private val listRawIds = mutableListOf<Int>()
        private var listDelayFromStart = mutableListOf<Long>()

        operator fun set(rawId:Int, delayFromStart:Long) {
            listRawIds.add(rawId)
            listDelayFromStart.add(delayFromStart)
        }

        fun build():MyCommand {
            if(buildName.contains("&")) {
                listDelayFromStart = buildName.split('&').drop(1).map { x -> x.toLong() }.toMutableList()
            }

            return object : MyCommand(activity) {
                val mps: Array<MediaPlayer> = listRawIds.map { x -> MediaPlayer.create(activity, x) }.toTypedArray()
                val mpNoise = MediaPlayer.create(activity, R.raw.whitenoise_point_001db)

                override val time: Long = executionDelay
                override val name: String = "$buildName&${listDelayFromStart.joinToString(separator = "&")}"

                init {
                    mpNoise.isLooping = true
                }

                override fun prepare() { }

                override fun startAt(timer: MyTimer, time: Long) {  //  IN SYSTEM TIME  bc. of standard deviation in gps time
                    thread { mpNoise.start() }

                    for (i in 0 until mps.count())
                        thread {
                            try {
                                val startAt = time + listDelayFromStart[i]
                                timer.sleepUntil(startAt)
                                mps[i].start()
                                Log.i("COMMAND", "MediaPlayer[$i] started")

                                mps[i].setOnSeekCompleteListener { Log.i("COMMAND", "MediaPlayer[$i] seek complete") }

                                sleepUntil { mps[i].currentPosition >= 10 }
                                Log.i("COMMAND", "MediaPlayer[$i] pos positive, current lag = ${timer.time - startAt - mps[i].currentPosition}")
                                mps[i].seekTo((timer.time - startAt).toInt())

                                //  observe lag
                                if(i == mps.count() - 1)  {
                                    val delay = timer.time - startAt - mps[i].currentPosition
                                    observedCommandLag = delay
                                    Log.i("COMMAND", "MediaPlayer[$i] lag = $delay ms")
                                }

                                //  release
                                sleepUntil { !mps[i].isPlaying }
                                mps[i].release()
                                Log.i("COMMAND", "MediaPlayer[$i] released bc. unused")
                            } catch (_:Exception) { }
                        }
                }

                override fun stopAndRelease() {
                    mpNoise.isLooping = false

                    for (mp in mps.plus(mpNoise)) mp.release()
                }
            }
        }
    }
}