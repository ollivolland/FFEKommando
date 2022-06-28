package com.ollivolland.ffekommando

import android.os.SystemClock
import kotlin.math.max

class MyTimer(private val delayToUnixTime:Long) {

    private val timeFromUnixToElapsed = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    val time:Long  get() = SystemClock.elapsedRealtime() + timeFromUnixToElapsed + delayToUnixTime

    fun sleepUntil(unixTime:Long) = Thread.sleep(max(unixTime - time, 0))
}