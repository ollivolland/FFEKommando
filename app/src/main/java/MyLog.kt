import android.util.Log

class MyLog(private val tag:String) {

    operator fun plusAssign(string: String) {
        Log.i(tag, string)
    }

    operator fun plusAssign(e: Exception) {
        Log.e(tag, e.toString())
        Log.e(tag, e.stackTraceToString())
    }
}