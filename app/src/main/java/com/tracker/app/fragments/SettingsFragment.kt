package com.tracker.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.tracker.app.R
import com.tracker.app.activities.LoginActivity
import com.tracker.app.network.ApiClient
import com.tracker.app.services.TrackerService
import com.tracker.app.services.TrackerState
import com.tracker.app.utils.Prefs
import org.json.JSONObject
import kotlin.concurrent.thread

class SettingsFragment : Fragment() {
    private val intervals = listOf(2, 5, 10, 30, 60)
    private val thresholds = listOf(500, 1100, 1500, 2000)
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_settings, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        fun spin(id: Int, items: List<Int>, unit: String, cur: Int, onPick: (Int) -> Unit) = v.findViewById<Spinner>(id).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items.map { "$it $unit" })
            setSelection(items.indexOf(cur).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, w: View?, pos: Int, id: Long) { if (items[pos] != cur) onPick(items[pos]) }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        spin(R.id.gpsInterval, intervals, "detik", Prefs.gpsIntervalSec) { Prefs.gpsIntervalSec = it; Toast.makeText(context, "Berlaku saat tracking dimulai ulang", Toast.LENGTH_SHORT).show() }
        spin(R.id.threshold, thresholds, "m", TrackerState.group?.thresholdM ?: Prefs.thresholdM) { m ->
            Prefs.thresholdM = m
            TrackerState.group?.let { g -> thread { val r = runCatching { ApiClient.patchGroup(g.id, JSONObject().put("thresholdM", m)) }
                activity?.runOnUiThread { r.onSuccess { TrackerState.group = it }.onFailure { e -> Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() } } } }
        }
        v.findViewById<Switch>(R.id.sound).apply { isChecked = Prefs.alertSound; setOnCheckedChangeListener { _, b -> Prefs.alertSound = b } }
        v.findViewById<Switch>(R.id.vibrate).apply { isChecked = Prefs.alertVibrate; setOnCheckedChangeListener { _, b -> Prefs.alertVibrate = b } }
        v.findViewById<Switch>(R.id.share).apply { isChecked = Prefs.shareLocation; setOnCheckedChangeListener { _, b ->
            Prefs.shareLocation = b; TrackerService.instance?.send(JSONObject().put("t", "share").put("on", b)) } }
        v.findViewById<View>(R.id.logout).setOnClickListener { signOut() }
        v.findViewById<View>(R.id.deleteAccount).setOnClickListener {
            thread { val r = runCatching { ApiClient.deleteAccount() }; activity?.runOnUiThread { r.onSuccess { signOut() }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() } } }
        }
    }
    private fun signOut() {
        val a = requireActivity()
        a.startService(Intent(a, TrackerService::class.java).setAction(TrackerService.ACTION_STOP))
        Prefs.clearSession(); a.startActivity(Intent(a, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
