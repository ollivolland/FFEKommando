package com.ollivolland.ffekommando

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager




@SuppressLint("InvalidWakeLockTag")
class WakeLock(context: Context) {

    val mWakeLock: PowerManager.WakeLock

    init {
        /* This code together with the one in onDestroy()
         * will make the screen be always on until this Activity gets destroyed. */
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag")
        mWakeLock.acquire()
    }

    fun release() {
        mWakeLock.release()
    }
}