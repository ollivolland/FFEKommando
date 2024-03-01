package com.ollivolland.ffe

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.SwitchCompat


class SettingsSwitch : FrameLayout {
    val view:View = inflate(context, R.layout.view_setting_switch, this)
    val vSwitch: SwitchCompat = view.findViewById(R.id.settingsSwitch_switch)

    private var str:String? = null

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) :
            super(context, attrs, defStyle)
    {
        init(attrs, defStyle)
    }

    constructor(context: Context, attrs: AttributeSet?) :
            super(context, attrs)
    {
        init(attrs)
    }

    constructor(context: Context) :
            super(context)
    {
        init()
    }

    private fun init(attrs: AttributeSet? = null, defStyle: Int = 0) {
        if(attrs != null)
        {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SettingsSwitch, 0, 0)
            str = a.getString(R.styleable.SettingsSwitch_text)
            a.recycle()

            vSwitch.text = str
        }
    }
}