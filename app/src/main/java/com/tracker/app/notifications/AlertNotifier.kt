package com.tracker.app.notifications

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tracker.app.R
import com.tracker.app.activities.AlertActivity
import com.tracker.app.activities.MainActivity
import com.tracker.app.utils.Prefs

object AlertNotifier {
    const val CH_TRACKING = "tracking"; const val CH_ALERT = "alert"; const val CH_SOS = "sos"
    const val ACTION_ACK = "com.tracker.app.ACK"

    fun createChannels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TRACKING, "Tracking", NotificationManager.IMPORTANCE_LOW))
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        // Custom TRACKER ringtone: drop res/raw/tracker_ring.ogg and it is picked up; otherwise the system ringtone is used.
        val resId = c.resources.getIdentifier("tracker_ring", "raw", c.packageName)
        val sound = if (resId != 0) android.net.Uri.parse("android.resource://${c.packageName}/$resId") else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        for ((id, name) in listOf(CH_ALERT to "Rider left behind", CH_SOS to "SOS")) {
            nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(sound, attrs); enableVibration(true); vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
    }

    fun foreground(c: Context, text: String): Notification =
        NotificationCompat.Builder(c, CH_TRACKING).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("TRACKER active").setContentText(text).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build()

    /** Incoming-call style alert. Uses a full-screen intent where Android permits; otherwise heads-up. No real phone call is placed. */
    fun straggler(c: Context, id: Int, name: String, distanceM: Int, userId: String, sos: Boolean = false, message: String? = null) {
        val km = String.format(java.util.Locale.US, "%.1f", distanceM / 1000.0)
        val title = if (sos) "SOS! $name needs help" else "PERINGATAN! RIDER TERTINGGAL"
        val body = if (sos) (message ?: "Emergency") else "Nama: $name\nJarak: $km KM\nStatus: Terpisah dari rombongan."
        val open = PendingIntent.getActivity(c, id, Intent(c, AlertActivity::class.java).putExtra("userId", userId).putExtra("title", title).putExtra("body", body).putExtra("sos", sos)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val map = PendingIntent.getActivity(c, id + 1, Intent(c, MainActivity::class.java).putExtra("focus", userId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val ack = PendingIntent.getBroadcast(c, id + 2, Intent(ACTION_ACK).setPackage(c.packageName).putExtra("userId", userId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(c, if (sos) CH_SOS else CH_ALERT).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(body.lineSequence().drop(1).joinToString(" · ").ifEmpty { body })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX).setAutoCancel(true).setOnlyAlertOnce(false)
            .addAction(0, "Open", open).addAction(0, "Locate", map).addAction(0, "Confirm", ack)
            .setFullScreenIntent(open, true)
        if (!Prefs.alertSound) b.setSilent(true)
        c.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }
}
