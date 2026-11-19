package com.tracker.app

import android.app.Application
import com.tracker.app.notifications.AlertNotifier
import com.tracker.app.utils.Prefs

class TrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AlertNotifier.createChannels(this)
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
    }
}
