package com.tracker.app.fragments

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tracker.app.R
import com.tracker.app.location.haversineM
import com.tracker.app.services.TrackerState
import com.tracker.app.utils.Prefs
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapFragment : Fragment() {
    private lateinit var map: MapView
    private lateinit var info: TextView
    private var centered = false
    private val refresh = { draw() }
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_map, c, false).also {
        map = it.findViewById(R.id.map); info = it.findViewById(R.id.mapInfo)
        map.setTileSource(TileSourceFactory.MAPNIK); map.setMultiTouchControls(true); map.controller.setZoom(15.0)
    }
    override fun onResume() { super.onResume(); map.onResume(); TrackerState.listen(refresh); draw() }
    override fun onPause() { super.onPause(); map.onPause(); TrackerState.unlisten(refresh) }

    private fun draw() {
        if (!isAdded) return
        map.overlays.clear()
        val g = TrackerState.group; val now = System.currentTimeMillis()
        val me = TrackerState.positions[Prefs.userId]
        val separated = TrackerState.alerts.filter { it.type == "separated" || it.type == "repeat" }.map { it.userId }.toSet() -
            TrackerState.alerts.filter { it.type == "rejoined" }.map { it.userId }.toSet()
        val focus = activity?.intent?.getStringExtra("focus")
        for (p in TrackerState.positions.values) {
            val age = (now - p.ts) / 1000
            val role = when (p.userId) { g?.captainId -> "Captain"; g?.sweeperId -> "Sweeper"; else -> "" }
            val dist = me?.let { haversineM(it.lat, it.lng, p.lat, p.lng).toInt() }
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(p.lat, p.lng); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = (if (p.userId in separated) "⚠ " else "") + p.name + (if (role.isNotEmpty()) " ($role)" else "")
                snippet = "${p.speed?.let { "%.0f km/h".format(it * 3.6) } ?: "-"}  •  ${p.heading?.let { "%.0f°".format(it) } ?: "-"}  •  ${age}s lalu" + (dist?.let { "  •  $it m" } ?: "")
                if (p.userId == focus) showInfoWindow()
            })
        }
        (focus?.let { TrackerState.positions[it] } ?: me ?: TrackerState.positions.values.firstOrNull())?.let { if (!centered || focus != null) { map.controller.setCenter(GeoPoint(it.lat, it.lng)); centered = true } }
        info.text = if (separated.isNotEmpty()) "⚠ Rider tertinggal: " + separated.joinToString { TrackerState.positions[it]?.name ?: "?" } else "Rombongan menyatu • ${TrackerState.positions.size} online"
        map.invalidate()
    }
}
