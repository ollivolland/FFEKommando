import android.app.Activity
import android.app.AlertDialog
import android.view.View
import com.ollivolland.ffe.R
import com.ollivolland.ffe.SettingsSpinner
import com.ollivolland.ffe.SettingsSwitch

data class Profile(
    val isMaster:Boolean,
    val isCamera: Boolean,
    val isCommand: Boolean,
    val command: Command,
    val millisVideoLength:Long)
{

    fun inflateDialog(context: Activity, onDismiss:(Profile) -> Unit) {
        val view = context.layoutInflater.inflate(R.layout.dialog_profile, null)
        val vSpinnerCommand = view.findViewById<SettingsSpinner>(R.id.profile_commandSpinner)
        val vSpinnerDuration = view.findViewById<SettingsSpinner>(R.id.profile_durationSpinner)
        val vSwitchCamera = view.findViewById<SettingsSwitch>(R.id.profile_cameraSwitch)
        val vSwitchCommand = view.findViewById<SettingsSwitch>(R.id.profile_commandSwitch)

        var mCommand = command
        var mIsCamera = isCamera
        var mIsCommand = isCommand
        var mDuration = millisVideoLength

        //  command on/off
        vSwitchCommand.vSwitch.setOnClickListener {
            mIsCommand = vSwitchCommand.vSwitch.isChecked
        }
        vSwitchCommand.vSwitch.isChecked = isCommand
        vSwitchCommand.visibility = if(isMaster) View.GONE else View.VISIBLE

        //  command
        vSpinnerCommand.vSpinner.config(context, headerCommand) {
            mCommand = Command.entries[it]
        }
        vSpinnerCommand.vSpinner.setSelection(command.ordinal)
        vSpinnerCommand.visibility = if(isMaster) View.VISIBLE else View.GONE

        //  duration
        vSpinnerDuration.vSpinner.config(context, headerDuration) {
            mDuration = optionsDuration[it]
        }
        vSpinnerDuration.vSpinner.setSelection(optionsDuration.indexOf(millisVideoLength))

        //  camera
        vSwitchCamera.vSwitch.setOnClickListener {
            mIsCamera = vSwitchCamera.vSwitch.isChecked
        }
        vSwitchCamera.vSwitch.isChecked = isCamera

        AlertDialog.Builder(context)
            .setView(view)
            .setOnDismissListener {
                onDismiss(Profile(isMaster, mIsCamera, mIsCommand, mCommand, mDuration))
            }
            .show()
    }

    fun createStart(timeWant:Long):StartInstance {
        val delay = when(command)
        {
            Command.NONE -> 0L;
            Command.FULL -> 19_000L;
//            Command.SHORT -> 19_000L;
        }

        val timePreview = timeWant
        val timeExec = timePreview + delay

        return StartInstance(copy(), timePreview, timeExec)
    }

    enum class Command {
        NONE,
        FULL,
//        SHORT
    }

    companion object {
        val headerCommand = listOf("keins", "Voll")
        val optionsDuration = listOf(20_000L, 45_000L)
        val headerDuration = optionsDuration.map { "${it/1000L}s" }

        val default = Profile(true, true, true, Command.FULL, optionsDuration[1])
    }
}