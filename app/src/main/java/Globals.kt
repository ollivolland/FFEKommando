import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*

class Globals {
    companion object {
        val formatDayToSeconds = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH)
        val formatDayToMillis = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ENGLISH)
        val formatTimeToSeconds = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        val formatTimeToMillis = SimpleDateFormat("HH:mm:ss_SSS", Locale.ENGLISH)

        @SuppressLint("HardwareIds")
        fun getDeviceId(context: Context) : String {
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }
    }
}