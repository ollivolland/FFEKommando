package com.ollivolland.ffekommando

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*

class Globals {
    companion object {
        val formatToSeconds = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH)
        val formatToMillis = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ENGLISH)
        val formatToTimeOfDay = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)

        @SuppressLint("HardwareIds")
        fun getDeviceId(context: Context) : String {
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }
    }
}