package com.gps.wareply

import android.app.Application
import com.gps.wareply.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
