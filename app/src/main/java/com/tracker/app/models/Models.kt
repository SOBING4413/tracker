package com.tracker.app.models

data class Member(val id: String, val name: String, val photoUrl: String?)
data class Group(
    val id: String, val name: String, val inviteCode: String,
    val captainId: String?, val sweeperId: String?, val thresholdM: Int, val members: List<Member>, val active: Boolean = true,
)
data class Position(
    val userId: String, val name: String, val lat: Double, val lng: Double,
    val speed: Double?, val heading: Double?, val ts: Long, val isCaptain: Boolean,
)
data class AlertItem(val type: String, val userId: String?, val name: String?, val distanceM: Int, val ts: Long, val message: String? = null)

data class ChatMessage(val id: String, val userId: String, val name: String, val text: String, val ts: Long, val announce: Boolean)
/** One row of the Road Captain panel (server: GET /groups/:id/admin). */
data class AdminMember(
    val id: String, val name: String, val email: String, val role: String, val isOwner: Boolean,
    val online: Boolean, val sharing: Boolean, val muted: Boolean,
    val speedKmh: Int?, val lastSeenMs: Long?, val distanceToCaptainM: Int?, val joinedAt: Long?,
)
data class AdminView(val members: List<AdminMember>, val onlineCount: Int)
