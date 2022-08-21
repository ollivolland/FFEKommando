package com.ollivolland.ffekommando

import android.os.SystemClock
import kotlin.math.max

class MyTimer(private val timeToSystemBoot:Long) {

//    private val timeFromUnixToElapsed = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    val time:Long  get() = timeToSystemBoot + SystemClock.elapsedRealtime()

    fun sleepUntil(unixTime:Long) = Thread.sleep(max(unixTime - time, 0))
}