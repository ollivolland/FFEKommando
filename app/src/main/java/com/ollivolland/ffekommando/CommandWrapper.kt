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
            when (key.split('&')[0]) {
                "feuerwehr" -> return object : CommandWrapper(activity) {
                    var mpStartbefehl: MediaPlayer? = null
                    var mpWhiteNoise: MediaPlayer? = null
                    var timeStart:Long = -1L

                    override val time: Long = 19_000L
                    override val name: String = "feuerwehr"

                    override fun prepare() {
                        mpStartbefehl = MediaPlayer.create(activity, R.raw.startbefehl)
                        mpWhiteNoise = MediaPlayer.create(activity, R.raw.whitenoise)
                    }

                    override fun start() {
                        timeStart = System.currentTimeMillis()
                        mpWhiteNoise?.start()
                        sleepUntil(timeStart + 1_000L)
                        mpStartbefehl?.start()
                    }

                    override fun stopAndRelease() {
                        mpStartbefehl?.stop()
                        mpWhiteNoise?.stop()
                        mpStartbefehl?.release()
                        mpWhiteNoise?.release()
                    }
                }
                "leichtathletik10" -> return object : CommandWrapper(activity) {
                    var mpReady: MediaPlayer? = null
                    var mpSet: MediaPlayer? = null
                    var mpGo: MediaPlayer? = null
                    var mpWhiteNoise: MediaPlayer? = null
                    val generatedTimeToSet:Long
                    val generatedTimeToGo:Long
                    var timeStart:Long = -1L

                    init {
                        if(key.contains('&')) {
                            generatedTimeToSet = key.split('&')[1].toLong()
                            generatedTimeToGo = key.split('&')[2].toLong()
                        } else {
                            val random = Random(System.currentTimeMillis())
                            generatedTimeToSet = random.nextLong(4_000L,6_000L)
                            generatedTimeToGo = 1_000L + random.nextLong(2_000L,5_000L)
                        }
                    }

                    override val time: Long = generatedTimeToSet + generatedTimeToGo
                    override val name: String = "leichtathletik10&$generatedTimeToSet&$generatedTimeToGo"

                    override fun prepare() {
                        mpReady = MediaPlayer.create(activity, R.raw.aufdieplaetze)
                        mpSet = MediaPlayer.create(activity, R.raw.fertig)
                        mpGo = MediaPlayer.create(activity, R.raw.gunshot_5db)
                        mpWhiteNoise = MediaPlayer.create(activity, R.raw.whitenoise)
                    }

                    override fun start() {
                        timeStart = System.currentTimeMillis()
                        mpReady?.start()
                        sleepUntil(timeStart + generatedTimeToSet)
                        mpSet?.start()
                        mpWhiteNoise?.start()
                        sleepUntil(timeStart + generatedTimeToSet + generatedTimeToGo)
                        mpGo?.start()
                    }

                    override fun stopAndRelease() {
                        mpReady?.stop()
                        mpSet?.stop()
                        mpGo?.stop()
                        mpWhiteNoise?.stop()
                        mpReady?.release()
                        mpSet?.release()
                        mpGo?.release()
                        mpWhiteNoise?.release()
                    }
                }
                "leichtathletik30" -> return object : CommandWrapper(activity) {
                    var mpReady: MediaPlayer? = null
                    var mpSet: MediaPlayer? = null
                    var mpGo: MediaPlayer? = null
                    var mpWhiteNoise: MediaPlayer? = null
                    val generatedTimeToReady:Long
                    val generatedTimeToSet:Long
                    val generatedTimeToGo:Long
                    var timeStart:Long = -1L

                    init {
                        if(key.contains('&')) {
                            generatedTimeToReady= key.split('&')[1].toLong()
                            generatedTimeToSet = key.split('&')[2].toLong()
                            generatedTimeToGo = key.split('&')[3].toLong()
                        } else {
                            val random = Random(System.currentTimeMillis())
                            generatedTimeToReady = random.nextLong(20_000L,40_000L)
                            generatedTimeToSet = random.nextLong(20_000L,40_000L)
                            generatedTimeToGo = 1_000L + random.nextLong(2_000L,5_000L)
                        }
                    }

                    override val time: Long = generatedTimeToReady + generatedTimeToSet + generatedTimeToGo
                    override val name: String = "leichtathletik30&$generatedTimeToReady&$generatedTimeToSet&$generatedTimeToGo"

                    override fun prepare() {
                        mpReady = MediaPlayer.create(activity, R.raw.aufdieplaetze)
                        mpSet = MediaPlayer.create(activity, R.raw.fertig)
                        mpGo = MediaPlayer.create(activity, R.raw.gunshot_5db)
                        mpWhiteNoise = MediaPlayer.create(activity, R.raw.whitenoise)
                    }

                    override fun start() {
                        timeStart = System.currentTimeMillis()
                        sleepUntil(timeStart + generatedTimeToReady)
                        mpReady?.start()
                        sleepUntil(timeStart + generatedTimeToReady + generatedTimeToSet)
                        mpSet?.start()
                        mpWhiteNoise?.start()
                        sleepUntil(timeStart + generatedTimeToReady + generatedTimeToSet + generatedTimeToGo)
                        mpGo?.start()
                    }

                    override fun stopAndRelease() {
                        mpReady?.stop()
                        mpSet?.stop()
                        mpGo?.stop()
                        mpWhiteNoise?.stop()
                        mpReady?.release()
                        mpSet?.release()
                        mpGo?.release()
                        mpWhiteNoise?.release()
                    }
                }
                else -> throw Exception("false build command")
            }
        }
    }
}