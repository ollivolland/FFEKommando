import android.os.SystemClock
import kotlin.math.max

class MyTimer(time: Long) {
    val bootTime = time - SystemClock.elapsedRealtime()
    val time;get() = bootTime + SystemClock.elapsedRealtime()

    fun sleepUntil(time:Long) {
        Thread.sleep(max(0, time - this.time))
    }
}