package com.tracker.app.services

import com.tracker.app.models.*
import com.tracker.app.utils.Prefs

/** Process-wide observable state shared between TrackerService and the UI (all callbacks on the main thread). */
object TrackerState {
    var group: Group? = null
    val positions = LinkedHashMap<String, Position>()
    val alerts = ArrayList<AlertItem>()
    var online = false
    val chat = ArrayList<ChatMessage>()
    /** Members muted by the Road Captain (no voice, no chat). */
    val mutedIds = HashSet<String>()
    var unreadChat = 0
    /** Set by MainActivity when a chat notification is tapped; the Talk tab consumes it and opens its Chat sub-tab. */
    var wantChatTab = false
    var floorHolder: String? = null
    var gpsOk = true
    var lastFixTs = 0L
    /** Connection label for the UI; "offline" is only claimed while tracking is actually running. */
    fun connLabel(): String = when {
        TrackerService.instance == null -> "Tracking belum dimulai"
        online -> "Terhubung"
        else -> "Offline — mencoba menyambung ulang"
    }
    /** True only for the current Road Captain: gates the captain panel (the server re-checks every request). */
    fun isCaptain(): Boolean { val me = Prefs.userId ?: return false; return group?.captainId == me }
    fun isMuted(): Boolean { val me = Prefs.userId ?: return false; return me in mutedIds }
    fun addChat(m: ChatMessage): Boolean {
        if (chat.any { it.id == m.id }) return false     // the server re-sends recent messages on every reconnect
        chat.add(m); if (chat.size > 300) chat.subList(0, chat.size - 300).clear(); return true
    }
    /** True while some screen of the app is visible (used to decide between an in-app update and a notification). */
    fun uiVisible() = listeners.isNotEmpty()
    fun addAlert(a: AlertItem) { alerts.add(a); if (alerts.size > 500) alerts.subList(0, alerts.size - 500).clear() }
    private val listeners = HashSet<() -> Unit>()
    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }
    fun notifyChanged() { listeners.toList().forEach { it() } }
    fun reset() { group = null; positions.clear(); alerts.clear(); online = false; floorHolder = null; chat.clear(); mutedIds.clear(); unreadChat = 0 }
}
