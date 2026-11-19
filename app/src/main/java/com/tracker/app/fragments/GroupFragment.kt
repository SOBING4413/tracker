package com.tracker.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.tracker.app.R
import com.tracker.app.activities.CaptainPanelActivity
import com.tracker.app.activities.MainActivity
import com.tracker.app.models.Group
import com.tracker.app.network.ApiClient
import com.tracker.app.services.TrackerService
import com.tracker.app.services.TrackerState
import com.tracker.app.utils.Permissions
import com.tracker.app.utils.Prefs
import org.json.JSONObject
import kotlin.concurrent.thread

class GroupFragment : Fragment() {
    private lateinit var v: View
    private val refresh = { render() }
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_group, c, false).also { v = it }

    override fun onViewCreated(view: View, s: Bundle?) {
        v.findViewById<Button>(R.id.btnCreate).setOnClickListener { prompt(getString(R.string.group_name_prompt)) { n -> submit { ApiClient.createGroup(n) } } }
        v.findViewById<Button>(R.id.btnJoin).setOnClickListener { prompt(getString(R.string.invite_code_prompt)) { c -> submit { ApiClient.joinGroup(c.trim()) } } }
        v.findViewById<Button>(R.id.btnShare).setOnClickListener {
            TrackerState.group?.let { g -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, getString(R.string.share_invite, g.name, g.inviteCode)), getString(R.string.share_code_chooser))) }
        }
        v.findViewById<Button>(R.id.btnTrack).setOnClickListener {
            val act = requireActivity() as MainActivity
            if (TrackerService.instance != null) { act.startService(Intent(act, TrackerService::class.java).setAction(TrackerService.ACTION_STOP)) }
            else Permissions.requestLocationFlow(act) { androidx.core.content.ContextCompat.startForegroundService(act, Intent(act, TrackerService::class.java).setAction(TrackerService.ACTION_START)) }
            v.postDelayed({ render() }, 400)
        }
        v.findViewById<Button>(R.id.btnCaptain).setOnClickListener {
            if (TrackerState.isCaptain()) startActivity(Intent(requireContext(), CaptainPanelActivity::class.java))
        }
        v.findViewById<Button>(R.id.btnEnd).setOnClickListener {
            val g = TrackerState.group ?: return@setOnClickListener
            thread { runCatching { ApiClient.endGroup(g.id) }; activity?.runOnUiThread { Prefs.groupId = null; TrackerState.group = null; render() } }
        }
        Prefs.groupId?.let { id -> if (TrackerState.group == null) thread { runCatching { ApiClient.getGroup(id) }.onSuccess { g -> activity?.runOnUiThread { TrackerState.group = g; render() } } } }
    }
    override fun onStart() { super.onStart(); TrackerState.listen(refresh); render() }
    override fun onStop() { super.onStop(); TrackerState.unlisten(refresh) }

    private fun submit(block: () -> Group) = thread {
        val r = runCatching(block)
        activity?.runOnUiThread {
            r.onSuccess { g -> Prefs.groupId = g.id; TrackerState.group = g; render() }
                .onFailure { Toast.makeText(context, it.message ?: getString(R.string.generic_error), Toast.LENGTH_LONG).show() }
        }
    }
    private fun prompt(title: String, ok: (String) -> Unit) {
        val et = EditText(context)
        AlertDialog.Builder(requireContext()).setTitle(title).setView(et).setPositiveButton(android.R.string.ok) { _, _ -> et.text.toString().takeIf { it.isNotBlank() }?.let(ok) }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun render() {
        if (!isAdded) return
        val g = TrackerState.group
        v.findViewById<TextView>(R.id.status).text = getString(R.string.group_status, getString(if (TrackerState.gpsOk) R.string.gps_ok else R.string.gps_unavailable), getString(if (TrackerService.instance == null) R.string.conn_inactive else if (TrackerState.online) R.string.conn_online else R.string.conn_offline))
        val has = g != null
        v.findViewById<View>(R.id.btnCreate).visibility = if (has) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.btnJoin).visibility = if (has) View.GONE else View.VISIBLE
        listOf(R.id.btnShare, R.id.btnTrack, R.id.btnEnd).forEach { v.findViewById<View>(it).visibility = if (has) View.VISIBLE else View.GONE }
        // The captain panel exists only for the current Road Captain (and the server re-checks every request).
        v.findViewById<View>(R.id.btnCaptain).visibility = if (has && TrackerState.isCaptain()) View.VISIBLE else View.GONE
        v.findViewById<Button>(R.id.btnTrack).text = getString(if (TrackerService.instance != null) R.string.tracking_stop else R.string.tracking_start)
        val box = v.findViewById<LinearLayout>(R.id.members); box.removeAllViews()
        if (g == null) { v.findViewById<TextView>(R.id.groupInfo).text = getString(R.string.group_none); return }
        v.findViewById<TextView>(R.id.groupInfo).text = getString(R.string.group_info, g.name, g.inviteCode, TrackerState.positions.size, g.members.size, g.thresholdM)
        for (m in g.members) {
            val role = when (m.id) { g.captainId -> " (${getString(R.string.role_captain)})"; g.sweeperId -> " (${getString(R.string.role_sweeper)})"; else -> "" }
            val status = getString(if (TrackerState.positions.containsKey(m.id)) R.string.online else R.string.offline)
            box.addView(TextView(context).apply {
                text = m.name + role + "  |  " + status; textSize = 17f; setPadding(0, 18, 0, 18); setTextColor(resources.getColor(R.color.text_primary, null))
                setOnLongClickListener { manage(g, m.id); true }
            })
        }
    }

    /** Long-press a member: Road Captain / Sweeper / remove (server enforces captain/owner authorization). */
    private fun manage(g: Group, uid: String) {
        AlertDialog.Builder(requireContext()).setItems(arrayOf(getString(R.string.make_captain), getString(R.string.make_sweeper), getString(R.string.kick_member))) { _, w ->
            thread {
                val r = runCatching {
                    when (w) {
                        0 -> ApiClient.patchGroup(g.id, JSONObject().put("captainId", uid))
                        1 -> ApiClient.patchGroup(g.id, JSONObject().put("sweeperId", uid))
                        else -> { ApiClient.kick(g.id, uid); ApiClient.getGroup(g.id) }
                    }
                }
                activity?.runOnUiThread { r.onSuccess { TrackerState.group = it; render() }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() } }
            }
        }.show()
    }
}
