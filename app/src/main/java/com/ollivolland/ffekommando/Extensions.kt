package com.ollivolland.ffekommando

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

fun <T> MutableList<T>.mean():Double where T : Number {
    return this.toList().sumOf { x -> x.toDouble() } / this.count()
}

fun <T> MutableList<T>.stdev():Double where T : Number {
    val ave = this.mean()
    val concurrent = this.toList()
    return sqrt(concurrent.sumOf { x -> (x.toDouble() - ave).pow(2.0) } / concurrent.count())
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun sleepUntil(predicate:() -> Boolean) {
    while (!predicate()) Thread.sleep(10)
}