package com.ollivolland.ffekommando

abstract class MyCamera {
    protected var log = MyLog("MY_CAMERA")
    var timeStartedRecording:Long = -1

    abstract fun startRecord()

    abstract fun stopRecord()
}