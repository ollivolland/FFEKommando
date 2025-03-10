import android.app.Activity
import android.media.MediaPlayer
import android.util.Log
import com.ollivolland.ffe.R
import kotlin.concurrent.thread

abstract class MyCommand {
    var observedCommandLag:Long = -1
    abstract fun startAt(timer: MyTimer, time: Long)
    abstract fun stopAndRelease()

    companion object {

        operator fun get(cameraInstance: StartInstance, activity: Activity): MyCommand
        {
            return when (cameraInstance.profile.command) {
                Profile.Command.NONE -> {
                    MyCommandBuilder(activity).build()
                }
                Profile.Command.SHORT -> {
                    MyCommandBuilder(activity).apply {
                        this[R.raw.startbefehl_2x_5db] = 0L
                    }.build()
                }
                Profile.Command.FULL -> {
                    MyCommandBuilder(activity).apply {
                        this[R.raw.startbefehl_5db] = 0L
                    }.build()
                }
            }
        }
    }

    class MyCommandBuilder(private val activity: Activity) {
        private var rawId:Int = -1
        private var delayFromStart:Long = -1L
        private var isTerminated = false

        operator fun set(rawId:Int, delayFromStart:Long) {
            this.rawId = rawId
            this.delayFromStart = delayFromStart
        }

        fun build(): MyCommand {
            return object : MyCommand() {
                val mps: MediaPlayer = MediaPlayer.create(activity, rawId)
                val mpNoise = MediaPlayer.create(activity, R.raw.whitenoise_point_001db)

                init {
                    mpNoise.isLooping = true
                }

                override fun startAt(timer: MyTimer, time: Long) {  //  IN SYSTEM TIME  bc. of standard deviation in gps time
                    thread {
                        timer.sleepUntil(time)
                        mpNoise.start()
                    }

                    if(rawId == -1)
                    {
                        observedCommandLag = 0
                        return
                    }
                    else
                        thread {
                            val startAt = time + delayFromStart

                            timer.sleepUntil(startAt)
                            mps.start()
                            Log.i("COMMAND", "MediaPlayer[] started")

                            //  observe lag
                            sleepUntil { mps.currentPosition >= 10 }
                            observedCommandLag = timer.time - startAt - mps.currentPosition
                            Log.i("COMMAND", "MediaPlayer lag = $observedCommandLag")

                            //  release
                            sleepUntil { isTerminated || !mps.isPlaying }
                            mps.release()
                            Log.i("COMMAND", "MediaPlayer[] released")
                        }
                }

                override fun stopAndRelease() {
                    isTerminated = true
                    mpNoise.isLooping = false

                    for (mp in arrayOf(mpNoise, mps)) mp.release()
                }
            }
        }
    }
}