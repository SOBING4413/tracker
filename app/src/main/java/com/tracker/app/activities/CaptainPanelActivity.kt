package com.tracker.app.activities

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tracker.app.R
import com.tracker.app.models.AdminMember
import com.tracker.app.network.ApiClient
import com.tracker.app.services.TrackerService
import com.tracker.app.services.TrackerState
import com.tracker.app.utils.Prefs
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

/**
 * Road Captain panel: who is in the group (role, online, speed, distance to the captain, join time),
 * mute/unmute, sweeper, hand over captain, remove, announcements and the alert distance.
 * Only reachable for the current captain; the server (GET /groups/:id/admin) re-checks on every request,
 * and this screen closes by itself if the captain role is lost.
 */
class CaptainPanelActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private val thresholds = listOf(500, 1100, 1500, 2000)
    private var members: List<AdminMember> = emptyList()
    private var onlineCount = 0
    private val poll = object : Runnable { override fun run() { load(); main.postDelayed(this, 5_000) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_captain)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAnnounce).setOnClickListener { announce() }
        val spin = findViewById<Spinner>(R.id.threshold)
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, thresholds.map { "$it m" })
        var current = TrackerState.group?.thresholdM ?: Prefs.thresholdM
        spin.setSelection(thresholds.indexOf(current).coerceAtLeast(0))
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, w: View?, pos: Int, id: Long) {
                val m = thresholds[pos]; if (m == current) return; current = m
                val gid = Prefs.groupId ?: return
                bg({ ApiClient.patchGroup(gid, JSONObject().put("thresholdM", m)) }) { g -> TrackerState.group = g; Prefs.thresholdM = m; render() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    override fun onStart() {
        super.onStart()
        if (!TrackerState.isCaptain()) { Toast.makeText(this, getString(R.string.captain_only), Toast.LENGTH_LONG).show(); finish(); return }
        main.post(poll)
    }
    override fun onStop() { super.onStop(); main.removeCallbacks(poll) }

    private fun load() {
        val gid = Prefs.groupId ?: return finish()
        thread {
            val r = runCatching { ApiClient.getAdmin(gid) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                r.onSuccess { members = it.members; onlineCount = it.onlineCount; render() }
                    .onFailure { e ->
                        if (e is ApiClient.ApiError && e.code == 403) { Toast.makeText(this, getString(R.string.captain_no_longer), Toast.LENGTH_LONG).show(); finish() }
                        else findViewById<TextView>(R.id.summary).text = getString(R.string.load_failed, e.message ?: getString(R.string.conn_problem))
                    }
            }
        }
    }

    /** Run a network call off the main thread (named bg to avoid clashing with Kotlin's run), then [done] on it; errors are shown as a toast. */
    private fun <T> bg(call: () -> T, done: (T) -> Unit) = thread {
        val r = runCatching(call)
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            r.onSuccess(done).onFailure { Toast.makeText(this, it.message ?: getString(R.string.generic_error), Toast.LENGTH_LONG).show() }
        }
    }

    private fun render() {
        val g = TrackerState.group
        findViewById<TextView>(R.id.summary).text = getString(R.string.captain_summary, g?.name ?: "", members.size, onlineCount, g?.inviteCode ?: "-")
        val box = findViewById<LinearLayout>(R.id.memberList); box.removeAllViews()
        val order = compareBy<AdminMember>({ it.role != "captain" }, { !it.online }, { it.name.lowercase() })
        members.sortedWith(order).forEach { box.addView(card(it)) }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun line(text: String, color: Int = R.color.text_secondary, size: Float = 14f) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(resources.getColor(color, null))
    }

    private fun card(m: AdminMember): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(resources.getColor(R.color.surface_dark, null)) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(6)) }
        }
        val role = getString(when (m.role) { "captain" -> R.string.role_captain; "sweeper" -> R.string.role_sweeper; else -> R.string.role_member }) + if (m.isOwner) getString(R.string.owner_suffix) else ""
        box.addView(line("${m.name}  •  $role", R.color.text_primary, 17f))
        box.addView(line(m.email))
        val status = getString(when { !m.online -> R.string.offline; !m.sharing -> R.string.online_paused; else -> R.string.online })
        box.addView(line(getString(R.string.captain_status, status), if (m.online) R.color.ok_green else R.color.text_secondary))
        m.lastSeenMs?.let { box.addView(line(getString(R.string.captain_last_update, ago(it)))) }
        m.speedKmh?.let { box.addView(line(getString(R.string.captain_speed, it.toString()))) }
        m.distanceToCaptainM?.let { box.addView(line(getString(R.string.captain_distance, dist(it)))) }
        m.joinedAt?.let { box.addView(line(getString(R.string.captain_joined, SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(it))))) }
        if (m.muted) box.addView(line(getString(R.string.captain_muted_label), R.color.danger))
        if (m.role != "captain") box.addView(Button(this).apply { text = getString(R.string.manage); setOnClickListener { manage(m) } })
        return box
    }

    private fun ago(ms: Long): String = when { ms < 5_000 -> getString(R.string.ago_now); ms < 60_000 -> getString(R.string.ago_sec, ms / 1000); else -> getString(R.string.ago_min, ms / 60_000) }
    private fun dist(m: Int): String = if (m < 1000) "$m m" else String.format(Locale.US, "%.1f km", m / 1000.0)

    private fun manage(m: AdminMember) {
        val gid = Prefs.groupId ?: return
        val items = arrayOf(
            getString(if (m.muted) R.string.mute_member_off else R.string.mute_member_on),
            getString(if (m.role == "sweeper") R.string.remove_sweeper else R.string.make_sweeper),
            getString(R.string.make_captain),
            getString(R.string.kick_member),
        )
        AlertDialog.Builder(this).setTitle(m.name).setItems(items) { _, w ->
            when (w) {
                0 -> bg({ ApiClient.muteMember(gid, m.id, !m.muted) }) { list -> members = list; render() }
                1 -> bg({ ApiClient.patchGroup(gid, JSONObject().put("sweeperId", if (m.role == "sweeper") JSONObject.NULL else m.id)) }) { g -> TrackerState.group = g; load() }
                2 -> confirm(getString(R.string.confirm_make_captain, m.name)) {
                    bg({ ApiClient.patchGroup(gid, JSONObject().put("captainId", m.id)) }) { g -> TrackerState.group = g; Toast.makeText(this, getString(R.string.now_captain, m.name), Toast.LENGTH_LONG).show(); finish() }
                }
                else -> confirm(getString(R.string.confirm_kick, m.name)) { bg({ ApiClient.kick(gid, m.id) }) { load() } }
            }
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun confirm(msg: String, ok: () -> Unit) =
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton(R.string.yes) { _, _ -> ok() }.setNegativeButton(android.R.string.cancel, null).show()

    /** Sends a chat message marked as an announcement (the server only honours the flag for the captain). */
    private fun announce() {
        val et = EditText(this).apply { hint = getString(R.string.announce_hint); maxLines = 3 }
        AlertDialog.Builder(this).setTitle(R.string.announce_title).setView(et).setPositiveButton(R.string.send) { _, _ ->
            val text = et.text.toString().trim()
            if (text.isNotEmpty()) {
                val ok = TrackerService.instance?.sendChat(text, announce = true) ?: false
                Toast.makeText(this, getString(if (ok) R.string.announce_sent else R.string.announce_not_connected), Toast.LENGTH_LONG).show()
            }
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
}
