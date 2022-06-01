package com.ollivolland.ffekommando

import android.app.Activity

class CameraConfig(
    var isCamera: Boolean,
    var isCommand: Boolean,
    var commandBuilder: String,
    var millisDelay: Long,
    var millisVideoDuration: Long) {

    fun generateInstance(activity: Activity, correctedTimeStartCamera: Long) : CameraInstance
    {
        val commandWrapper = CommandWrapper[commandBuilder, activity]
        return CameraInstance(
            isCamera = isCamera,
            isCommand = isCommand,
            commandFullName = commandWrapper.name,
            correctedTimeStartCamera = correctedTimeStartCamera,
            correctedTimeCommandStart = correctedTimeStartCamera + COMMAND_DELAY,
            correctedTimeCommandExecuted = correctedTimeStartCamera + COMMAND_DELAY + commandWrapper.time,
            millisVideoLength = millisVideoDuration
        )
    }

    companion object {
        const val COMMAND_DELAY = 1_000L
        val default = CameraConfig(isCamera = false,
            isCommand = false,
            commandBuilder = "feuerwehr",
            millisDelay = 5_000L,
            millisVideoDuration = 10_000L)
    }
}

class CameraInstance(
    val isCamera: Boolean,
    val isCommand: Boolean,
    val commandFullName: String,
    val correctedTimeStartCamera:Long,
    val correctedTimeCommandStart:Long,
    val correctedTimeCommandExecuted:Long,
    val millisVideoLength:Long)