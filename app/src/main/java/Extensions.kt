import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.R
import kotlin.math.pow
import kotlin.math.sqrt

fun <T> MutableList<T>.mean():Double where T : Number {
    return this.toList().sumOf { x -> x.toDouble() } / this.count()
}

fun <T> MutableList<T>.stdev():Double where T : Number {
    val ave = this.mean()
    val concurrent = this.toList()
    return sqrt(concurrent.sumOf { x -> (x.toDouble() - ave).pow(2.0) } / concurrent.count())
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun sleepUntil(predicate:() -> Boolean) {
    while (!predicate()) Thread.sleep(1)
}

fun Spinner.config(context: Context, headers: List<String>, onSelect: (Int) -> Unit) {
    adapter = ArrayAdapter(context, R.layout.support_simple_spinner_dropdown_item, headers)
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            onSelect(position)
        }
    }
}
//fun Spinner.config(context: Context, vararg headers: String, onSelect: (Int) -> Unit) =
//    config(context, headers.toList().toTypedArray(), onSelect)