package com.tracker.app.fragments

import android.annotation.SuppressLint
import android.Manifest
import android.view.*
import android.os.Bundle
import android.widget.*
import androidx.fragment.app.Fragment
import com.tracker.app.R
import com.tracker.app.activities.MainActivity
import com.tracker.app.services.TrackerService
import com.tracker.app.services.TrackerState
import com.tracker.app.utils.Permissions
import org.json.JSONObject

class TalkFragment : Fragment() {
    private lateinit var v: View
    private val refresh = { render() }
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_talk, c, false).also { v = it }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, s: Bundle?) {
        v.findViewById<View>(R.id.ptt).setOnTouchListener { b, e ->
            val svc = TrackerService.instance
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (svc == null) Toast.makeText(context, "Mulai tracking di tab Group dulu", Toast.LENGTH_SHORT).show()
                    else Permissions.request(activity as MainActivity, Manifest.permission.RECORD_AUDIO, R.string.perm_mic_why, 20) { b.isPressed = true; svc.pttDown() }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { b.isPressed = false; svc?.pttUp() }
            }
            true
        }
        v.findViewById<ToggleButton>(R.id.mute).setOnCheckedChangeListener { _, on -> TrackerService.instance?.streamer?.muted = on }
        v.findViewById<SeekBar>(R.id.volume).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { TrackerService.instance?.streamer?.volume = p / 100f }
            override fun onStartTrackingTouch(s: SeekBar) {}; override fun onStopTrackingTouch(s: SeekBar) {}
        })
        v.findViewById<View>(R.id.sos).setOnLongClickListener {   // long-press avoids accidental SOS
            TrackerService.instance?.send(JSONObject().put("t", "sos").put("message", "SOS - butuh bantuan")); Toast.makeText(context, "SOS terkirim", Toast.LENGTH_LONG).show(); true
        }
        v.findViewById<View>(R.id.sos).setOnClickListener { Toast.makeText(context, "Tahan tombol SOS untuk mengirim", Toast.LENGTH_SHORT).show() }
    }
    override fun onStart() { super.onStart(); TrackerState.listen(refresh); render() }
    override fun onStop() { super.onStop(); TrackerState.unlisten(refresh) }
    private fun render() {
        if (!isAdded) return
        v.findViewById<TextView>(R.id.conn).text = if (TrackerState.online) "Terhubung" else "Offline — mencoba menyambung ulang"
        val h = TrackerState.floorHolder
        v.findViewById<TextView>(R.id.speaker).text = if (h == null) "Channel idle" else "🎙 " + (TrackerState.group?.members?.firstOrNull { it.id == h }?.name ?: "Rider") + " berbicara"
    }
}
