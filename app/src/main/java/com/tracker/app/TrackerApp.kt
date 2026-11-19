package com.tracker.app

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.tracker.app.activities.LoginActivity
import com.tracker.app.network.ApiClient
import com.tracker.app.notifications.AlertNotifier
import com.tracker.app.services.TrackerService
import com.tracker.app.utils.Prefs

class TrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AlertNotifier.createChannels(this)
        // Token rejected by the server (account/data gone): stop tracking, clear the session, go back to login.
        ApiClient.onUnauthorized = {
            Handler(Looper.getMainLooper()).post {
                if (Prefs.token != null) {
                    runCatching { startService(Intent(this, TrackerService::class.java).setAction(TrackerService.ACTION_STOP)) }
                    Prefs.clearSession()
                    runCatching { startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }
                }
            }
        }
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
    }
}
