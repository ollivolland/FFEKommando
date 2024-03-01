package camera

import MyLog

abstract class IMyCamera {
    protected var log = MyLog("MY_VIDEO")
    var timeSynchronizedStartedRecording:Long = -1

    abstract fun init()

    abstract fun startRecord()

    abstract fun stopRecord()
}