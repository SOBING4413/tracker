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
                "sos" -> getString(R.string.alerts_sos, it.name ?: getString(R.string.rider), it.message.orEmpty())
                "rejoined" -> getString(R.string.alerts_rejoined, it.name ?: getString(R.string.rider))
                else -> getString(R.string.alerts_separated, it.name ?: getString(R.string.rider), km)
            }
        }.ifEmpty { getString(R.string.alerts_none) }
    }
}
