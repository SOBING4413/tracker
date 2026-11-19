package com.tracker.app.services

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.tracker.app.communication.VoiceStreamer
import com.tracker.app.models.*
import com.tracker.app.network.ApiClient
import com.tracker.app.network.RealtimeClient
import com.tracker.app.notifications.AlertNotifier
import com.tracker.app.utils.Prefs
import org.json.JSONObject
import kotlin.concurrent.thread

/** Foreground service: GPS publishing (works in background), realtime connection, alerts, PTT audio. */
class TrackerService : Service() {
    companion object {
        const val ACTION_START = "start"; const val ACTION_STOP = "stop"; const val ACTION_ACK = "ack"
        @Volatile var instance: TrackerService? = null
    }
    private var rt: RealtimeClient? = null
    private lateinit var fused: FusedLocationProviderClient
    private var voice: VoiceStreamer? = null
    private var callback: LocationCallback? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    // Short network blips shouldn't flash "offline" in the UI: only report offline if it lasts > 3 s.
    private val markOffline = Runnable { TrackerState.online = false; TrackerState.notifyChanged() }
    private var lastLoc: Location? = null
    private var lastSentAt = 0L
    // While a rider stands still the GPS reports nothing (min-distance filter), and the server would treat them as offline
    // after 60 s. Re-sending the last fix every 20 s keeps stationary riders "live" for the straggler detection.
    private val heartbeat = object : Runnable {
        override fun run() {
            if (rt == null) return
            val l = lastLoc
            if (l != null && TrackerState.gpsOk && System.currentTimeMillis() - TrackerState.lastFixTs < 5 * 60_000 && System.currentTimeMillis() - lastSentAt >= 20_000) sendFix(l, stationary = true)
            main.postDelayed(this, 20_000)
        }
    }
    val streamer get() = voice

    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); instance = this; fused = LocationServices.getFusedLocationProviderClient(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_ACK -> intent.getStringExtra("userId")?.let { send(JSONObject().put("t", "ack").put("userId", it)) }
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        val gid = Prefs.groupId ?: return stopSelf()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (Build.VERSION.SDK_INT >= 30 && has(Manifest.permission.RECORD_AUDIO)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        ServiceCompat.startForeground(this, 1, AlertNotifier.foreground(this, "Sharing location with your group"), if (Build.VERSION.SDK_INT >= 29) type else 0)
        if (rt != null) return
        loadGroup(gid)
        voice = VoiceStreamer(this) { rt?.sendBytes(it) ?: false }
        rt = RealtimeClient(applicationContext, gid, ::onMessage, { on ->
            main.removeCallbacks(markOffline)
            if (on) { TrackerState.online = true; TrackerState.notifyChanged(); lastLoc?.let { sendFix(it, stationary = true) } } else main.postDelayed(markOffline, 3_000)
        }, { voice?.play(it) }, onRejected = ::onRejected).also { it.start() }
        startLocation()
        main.removeCallbacks(heartbeat); main.postDelayed(heartbeat, 20_000)
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun startLocation() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) { TrackerState.gpsOk = false; TrackerState.notifyChanged(); return }
        val sec = Prefs.gpsIntervalSec.coerceIn(2, 60)
        // Battery: balanced accuracy when interval is long, high accuracy only for short intervals; min-distance filter avoids idle updates.
        val prio = if (sec <= 5) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val req = LocationRequest.Builder(prio, sec * 1000L).setMinUpdateDistanceMeters(3f).build()
        callback = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) { r.lastLocation?.let(::publish) }
            override fun onLocationAvailability(a: LocationAvailability) { TrackerState.gpsOk = a.isLocationAvailable; TrackerState.notifyChanged() }
        }
        try { fused.requestLocationUpdates(req, callback!!, mainLooper) } catch (e: SecurityException) { TrackerState.gpsOk = false }
    }

    private fun publish(l: Location) {
        TrackerState.lastFixTs = System.currentTimeMillis(); TrackerState.gpsOk = true; lastLoc = l
        if (!Prefs.shareLocation) return
        sendFix(l)
        TrackerState.notifyChanged()
    }

    private fun sendFix(l: Location, stationary: Boolean = false) {
        if (!Prefs.shareLocation) return
        lastSentAt = System.currentTimeMillis()
        send(JSONObject().put("t", "location").put("lat", l.latitude).put("lng", l.longitude)
            .put("speed", if (stationary) 0.0 else if (l.hasSpeed()) l.speed.toDouble() else JSONObject.NULL)
            .put("heading", if (l.hasBearing()) l.bearing.toDouble() else JSONObject.NULL))
    }

    /** Load the group, retrying a few times: right after a cold start (e.g. Render waking up) the first call can fail. */
    private fun loadGroup(gid: String) = thread {
        for (attempt in 0 until 5) {
            val r = runCatching { ApiClient.getGroup(gid) }
            val e = r.exceptionOrNull()
            if (r.isSuccess) { main.post { TrackerState.group = r.getOrNull(); TrackerState.notifyChanged() }; return@thread }
            if (e is ApiClient.ApiError && e.code == 403) { main.post { leaveGroupLocally() }; return@thread }
            if (e is ApiClient.ApiError && e.code == 401) return@thread   // ApiClient.onUnauthorized logs us out
            Thread.sleep(3_000L * (attempt + 1))
        }
    }

    /** The server closed our connection with 4003: find out why instead of reconnecting forever. */
    private fun onRejected() {
        val gid = Prefs.groupId ?: return
        thread {
            val r = runCatching { ApiClient.getGroup(gid) }
            val e = r.exceptionOrNull()
            // 401 is handled globally (ApiClient.onUnauthorized). Network errors: keep retrying.
            if ((e is ApiClient.ApiError && e.code == 403) || r.getOrNull()?.active == false) main.post { leaveGroupLocally() }
        }
    }

    private fun leaveGroupLocally() {
        android.widget.Toast.makeText(this, "Grup sudah berakhir atau Anda tidak lagi menjadi anggota", android.widget.Toast.LENGTH_LONG).show()
        Prefs.groupId = null; TrackerState.group = null; shutdown()
    }

    fun send(o: JSONObject) = rt?.send(o) ?: false

    private fun onMessage(m: JSONObject) {
        val me = Prefs.userId
        when (m.optString("t")) {
            "snapshot" -> m.getJSONArray("positions").let { a -> for (i in 0 until a.length()) putPos(a.getJSONObject(i)) }
            "location" -> putPos(m)
            "presence" -> if (!m.getBoolean("online")) TrackerState.positions.remove(m.getString("userId"))
            "floor" -> TrackerState.floorHolder = m.optString("userId").ifEmpty { null }.takeIf { !m.isNull("userId") }
            "alert" -> {
                val type = m.getString("type"); val uid = m.getString("userId")
                if (type == "rejoined") { TrackerState.addAlert(AlertItem(type, uid, m.optString("name"), 0, System.currentTimeMillis())) }
                else {
                    val item = AlertItem(type, uid, m.optString("name"), m.getInt("distanceM"), System.currentTimeMillis())
                    TrackerState.addAlert(item)
                    AlertNotifier.straggler(this, uid.hashCode(), item.name ?: "Rider", item.distanceM, uid)
                }
            }
            "sos" -> {
                val uid = m.getString("userId")
                TrackerState.addAlert(AlertItem("sos", uid, m.optString("name"), 0, System.currentTimeMillis(), m.optString("message")))
                if (uid != me) AlertNotifier.straggler(this, uid.hashCode() + 7, m.optString("name"), 0, uid, true, m.optString("message"))
            }
        }
        TrackerState.notifyChanged()
    }

    private fun putPos(o: JSONObject) {
        val id = o.getString("userId")
        TrackerState.positions[id] = Position(id, o.optString("name"), o.getDouble("lat"), o.getDouble("lng"),
            o.optDouble("speed").takeIf { !o.isNull("speed") && !it.isNaN() }, o.optDouble("heading").takeIf { !o.isNull("heading") && !it.isNaN() }, o.getLong("ts"), o.optBoolean("isCaptain"))
    }

    fun pttDown() { send(JSONObject().put("t", "ptt_start")); voice?.takeIf { has(Manifest.permission.RECORD_AUDIO) }?.let { it.routeToBluetoothIfAvailable(); it.startTalking() } }
    fun pttUp() { voice?.stopTalking(); send(JSONObject().put("t", "ptt_stop")) }

    private fun shutdown() {
        main.removeCallbacks(markOffline); main.removeCallbacks(heartbeat); lastLoc = null; callback?.let { fused.removeLocationUpdates(it) }; rt?.stop(); rt = null; voice?.release(); TrackerState.reset(); TrackerState.notifyChanged()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { instance = null; super.onDestroy() }
}
