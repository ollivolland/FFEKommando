package com.ollivolland.ffekommando

import android.app.Activity
import android.media.MediaPlayer
import kotlin.random.Random

abstract class CommandWrapper(activity: Activity) {
    abstract val time:Long
    abstract val name:String
    abstract fun start()
    abstract fun prepare()
    abstract fun stopAndRelease()

    companion object {

        operator fun get(key:String, activity: Activity): CommandWrapper
        {
            val random = Random(System.currentTimeMillis())

            when (key.split('&')[0]) {
                "feuerwehr" -> {
                    return createCommandWrapper(activity, "feuerwehr",
                        arrayOf(R.raw.startbefehl),
                        arrayOf(1000),
                        executionDelay = 18_000L)
                }
                "feuerwehrstaffel" -> {
                    val resources = arrayOf(
                        R.raw.narakeet_meinkommandowirdlauten,
                        R.raw.narakeet_aufdieplaetze,
                        R.raw.narakeet_fertig,
                        R.raw.narakeet_los,
                        R.raw.narakeet_meinkommandogilt,
                        R.raw.narakeet_aufdieplaetze,
                        R.raw.narakeet_fertig,
                        R.raw.narakeet_los
                    )

                    return if(key.contains('&'))  createCommandWrapper(activity, key, resources)
                    else createCommandWrapper(activity, "feuerwehrstaffel", resources,
                        arrayOf(1000, 3000, 2000, 1200, 3000, 3000, 2000, 1200))
                }
                "leichtathletik10" -> {
                    val resources = arrayOf(R.raw.aufdieplaetze,
                        R.raw.fertig,
                        R.raw.gunshot_5db)

                    return if(key.contains('&'))  createCommandWrapper(activity, key, resources)
                    else createCommandWrapper(activity, "leichtathletik10", resources,
                        arrayOf(0,
                            random.nextLong(4_000L, 6_000L),
                            1_000L + random.nextLong(2_000L,5_000L)))
                }
                "leichtathletik30" -> {
                    val resources = arrayOf(R.raw.aufdieplaetze,
                            R.raw.fertig,
                            R.raw.gunshot_5db)

                    return if(key.contains('&'))  createCommandWrapper(activity, key, resources)
                    else createCommandWrapper(activity, "leichtathletik30", resources,
                            arrayOf(random.nextLong(20_000L,40_000L),
                                random.nextLong(20_000L,40_000L),
                                1_000L + random.nextLong(2_000L,5_000L)))
                }
                else -> throw Exception("false build command")
            }
        }

        private fun createCommandWrapper(activity: Activity, name:String, resources:Array<Int>, delays: Array<Long>, executionDelay:Long = 0): CommandWrapper {
            return object : CommandWrapper(activity) {
                val mps: Array<MediaPlayer> = resources.map { x -> MediaPlayer.create(activity, x) }.toTypedArray()
                val mpNoise = MediaPlayer.create(activity, R.raw.whitenoise)
                var timeStart:Long = -1L

                override val time: Long = delays.sum() + executionDelay
                override val name: String = "$name&${delays.joinToString(separator = "&")}"

                override fun prepare() { }

                override fun start() {
                    timeStart = System.currentTimeMillis()

                    mpNoise.isLooping = true
                    mpNoise.start()

                    for (i in 0 until resources.count())
                    {
                        sleepUntil(timeStart + delays.take(i+1).sum())
                        mps[i].start()
                    }
                }

                override fun stopAndRelease() {
                    mpNoise.isLooping = false

                    for (mp in mps.plus(mpNoise)) {
                        mp.stop()
                        mp.release()
                    }
                }
            }
        }

        private fun createCommandWrapper(activity: Activity, buildName:String, resources:Array<Int>): CommandWrapper {
            val delays = buildName.split('&').drop(1).map { x -> x.toLong() }.toTypedArray()
            return createCommandWrapper(activity, buildName.split('&')[0], resources, delays)
        }
    }
}