package com.tracker.app.models

data class Member(val id: String, val name: String, val photoUrl: String?)
data class Group(
    val id: String, val name: String, val inviteCode: String,
    val captainId: String?, val sweeperId: String?, val thresholdM: Int, val members: List<Member>,
)
data class Position(
    val userId: String, val name: String, val lat: Double, val lng: Double,
    val speed: Double?, val heading: Double?, val ts: Long, val isCaptain: Boolean,
)
data class AlertItem(val type: String, val userId: String?, val name: String?, val distanceM: Int, val ts: Long, val message: String? = null)
