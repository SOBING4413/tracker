package com.tracker.app.notifications

import android.app.NotificationManager
import android.content.*
import com.tracker.app.services.TrackerService

class AckReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val uid = i.getStringExtra("userId") ?: return
        c.startService(Intent(c, TrackerService::class.java).setAction(TrackerService.ACTION_ACK).putExtra("userId", uid))
        c.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
