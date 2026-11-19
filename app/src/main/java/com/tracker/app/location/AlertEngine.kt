package com.tracker.app.location

import kotlin.math.*

data class Fix(val userId: String, val lat: Double, val lng: Double, val ts: Long, val isCaptain: Boolean = false)

fun haversineM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6371008.8
    val dLat = Math.toRadians(bLat - aLat); val dLng = Math.toRadians(bLng - aLng)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(min(1.0, sqrt(h)))
}

/**
 * Client-side mirror of backend/src/services/alertEngine.js (server is authoritative; this is used for
 * instant local feedback and offline-tolerant operation). Same semantics: captain-seeded connectivity
 * chain, 90% hysteresis, stale members ignored.
 */
class AlertEngine(var thresholdM: Int = 1100, private val clearRatio: Double = 0.9, private val staleMs: Long = 60_000) {
    private val separated = HashSet<String>()

    /** Returns map userId -> distance (m) for members currently separated from the main group. */
    fun evaluate(members: List<Fix>, now: Long): Map<String, Int> {
        val active = members.filter { now - it.ts <= staleMs }
        val out = HashMap<String, Int>()
        if (active.isEmpty()) { separated.clear(); return out }
        fun grow(seed: Fix): Set<String> {
            val main = hashSetOf(seed.userId); val q = ArrayDeque(listOf(seed))
            while (q.isNotEmpty()) {
                val cur = q.removeFirst()
                for (o in active) {
                    if (o.userId in main) continue
                    val lim = if (o.userId in separated) thresholdM * clearRatio else thresholdM.toDouble()
                    if (haversineM(cur.lat, cur.lng, o.lat, o.lng) <= lim) { main.add(o.userId); q.add(o) }
                }
            }
            return main
        }
        val captain = active.firstOrNull { it.isCaptain }
        val main = if (captain != null) grow(captain) else active.map { grow(it) }.maxByOrNull { it.size }!!
        val mainFixes = active.filter { it.userId in main }
        separated.retainAll(active.map { it.userId }.toSet())
        for (m in active) {
            if (m.userId in main) { separated.remove(m.userId); continue }
            separated.add(m.userId)
            out[m.userId] = mainFixes.minOf { haversineM(m.lat, m.lng, it.lat, it.lng) }.roundToInt()
        }
        return out
    }
}
