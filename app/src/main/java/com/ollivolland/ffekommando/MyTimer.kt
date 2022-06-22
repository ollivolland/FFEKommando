package com.ollivolland.ffekommando

import kotlin.math.max

class MyTimer(private val delay:Long) {

    val time:Long  get() = System.currentTimeMillis() + delay

    fun sleepUntil(unixTime:Long) = Thread.sleep(max(unixTime - time, 0))
}