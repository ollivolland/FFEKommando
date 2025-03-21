import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*

object Globals {
    const val DELAY_LAUNCH_NOW = 500L
    const val BLOCKING_WINDOW = 3000L


    val formatDayToSeconds = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH)
    val formatDayToMillis = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ENGLISH)
    val formatTimeToSeconds = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
    val formatTimeToMillis = SimpleDateFormat("HH:mm:ss_SSS", Locale.ENGLISH)

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}