package com.gps.wareply

import android.app.Application
import com.gps.wareply.data.Prefs
import com.gps.wareply.data.ConversationStore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ConversationStore.init(this)
    }
}
