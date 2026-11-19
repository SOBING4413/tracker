package com.tracker.app.services

import com.tracker.app.models.*

/** Process-wide observable state shared between TrackerService and the UI (all callbacks on the main thread). */
object TrackerState {
    var group: Group? = null
    val positions = LinkedHashMap<String, Position>()
    val alerts = ArrayList<AlertItem>()
    var online = false
    var floorHolder: String? = null
    var gpsOk = true
    var lastFixTs = 0L
    /** Connection label for the UI; "offline" is only claimed while tracking is actually running. */
    fun connLabel(): String = when {
        TrackerService.instance == null -> "Tracking belum dimulai"
        online -> "Terhubung"
        else -> "Offline — mencoba menyambung ulang"
    }
    fun addAlert(a: AlertItem) { alerts.add(a); if (alerts.size > 500) alerts.subList(0, alerts.size - 500).clear() }
    private val listeners = HashSet<() -> Unit>()
    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }
    fun notifyChanged() { listeners.toList().forEach { it() } }
    fun reset() { group = null; positions.clear(); alerts.clear(); online = false; floorHolder = null }
}
