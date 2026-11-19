package com.tracker.app.fragments

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tracker.app.R
import com.tracker.app.services.TrackerState
import java.text.SimpleDateFormat
import java.util.*

class AlertsFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_alerts, c, false)
    override fun onViewCreated(v: View, s: Bundle?) {
        val f = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        v.findViewById<TextView>(R.id.list).text = TrackerState.alerts.reversed().joinToString("\n\n") {
            val km = String.format(Locale.US, "%.1f", it.distanceM / 1000.0)
            "${f.format(Date(it.ts))}  " + when (it.type) {
                "sos" -> "SOS dari ${it.name ?: "Rider"}: ${it.message.orEmpty()}"
                "rejoined" -> "${it.name ?: "Rider"} kembali ke rombongan"
                else -> "${it.name ?: "Rider"} tertinggal ($km KM)"
            }
        }.ifEmpty { "Belum ada peringatan" }
    }
}
