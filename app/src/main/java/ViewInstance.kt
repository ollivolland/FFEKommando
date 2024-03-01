import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.contains
import com.ollivolland.ffe.R
import java.util.Date

class ViewInstance(private val activity:Activity, private val vInstanceParent:ViewGroup) {

    var isExists = true
    val container:View = activity.layoutInflater.inflate(R.layout.view_instance, vInstanceParent, false)
    val vParent:RelativeLayout = container.findViewById(R.id.instance_lParent)
    val vViewText:TextView = container.findViewById(R.id.instance_tText)
    val vExit:ImageButton = container.findViewById(R.id.instance_bExit)
    private val lines = mutableListOf<String>()

    init {
        vExit.setOnClickListener { destroy() }
    }

    fun destroy() {
        isExists = false
        if(vInstanceParent.contains(container)) vInstanceParent.removeView(container)
    }

    fun update(config: StartInstance) {
        if(!isExists) return

        lines.add("[erledigt] start ${Globals.formatTimeToSeconds.format(Date(config.timePreview))}")
        activity.runOnUiThread { vViewText.text = lines.joinToString("\n") }
    }
}