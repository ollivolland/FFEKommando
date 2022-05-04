package com.ollivolland.ffekommando

import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*

class Globals {
    companion object {
        const val VERSION_STRING = "2022.04.27"
        val formatToSeconds = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH)

        fun getDeviceId(context: Context) : String {
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }
    }
}