package com.ollivolland.ffekommando

import android.app.Activity

class DefaultCameraConfig(
    var isCamera: Boolean,
    var isCommand: Boolean,
    var commandBuilder: String,
    var millisDelay: Long,
    var millisVideoDuration: Long) {

    fun generateInstance(activity: Activity, correctedTimeStartCamera: Long) : CameraInstance
    {
        val commandWrapper = MyCommand[commandBuilder, activity]
        return CameraInstance(
            isCamera = isCamera,
            isCommand = isCommand,
            commandFullName = commandWrapper.name,
            correctedTimeStartCamera = correctedTimeStartCamera,
            correctedTimeCommandExecuted = correctedTimeStartCamera + commandWrapper.time,
            millisVideoLength = millisVideoDuration
        )
    }

    companion object {
        val default = DefaultCameraConfig(
            isCamera = false,
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
    val correctedTimeCommandExecuted:Long,
    val millisVideoLength:Long)