package com.ollivolland.ffe

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView


class SettingsSpinner : FrameLayout {
    val view:View = inflate(context, R.layout.view_setting_spinner, this)
    val vTitle:TextView = view.findViewById(R.id.settingsSpinner_title)
    val vSpinner: Spinner = view.findViewById(R.id.settingsSpinner_spinner)

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
            val a = context.obtainStyledAttributes(attrs, R.styleable.SettingsSpinner, 0, 0)
            str = a.getString(R.styleable.SettingsSpinner_text)
            a.recycle()

            vTitle.text = str
        }
    }
}