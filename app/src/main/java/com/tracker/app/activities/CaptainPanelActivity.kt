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
        if (!TrackerState.isCaptain()) { Toast.makeText(this, "Panel ini hanya untuk Road Captain", Toast.LENGTH_LONG).show(); finish(); return }
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
                        if (e is ApiClient.ApiError && e.code == 403) { Toast.makeText(this, "Anda bukan Road Captain lagi", Toast.LENGTH_LONG).show(); finish() }
                        else findViewById<TextView>(R.id.summary).text = "Gagal memuat data: ${e.message ?: "koneksi bermasalah"}"
                    }
            }
        }
    }

    /** Run a network call off the main thread (named bg to avoid clashing with Kotlin's run), then [done] on it; errors are shown as a toast. */
    private fun <T> bg(call: () -> T, done: (T) -> Unit) = thread {
        val r = runCatching(call)
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            r.onSuccess(done).onFailure { Toast.makeText(this, it.message ?: "Error", Toast.LENGTH_LONG).show() }
        }
    }

    private fun render() {
        val g = TrackerState.group
        findViewById<TextView>(R.id.summary).text = "${g?.name ?: ""}  •  ${members.size} anggota, $onlineCount online  •  Kode: ${g?.inviteCode ?: "-"}"
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
        val role = when (m.role) { "captain" -> "Road Captain"; "sweeper" -> "Sweeper"; else -> "Anggota" } + if (m.isOwner) " · Pemilik" else ""
        box.addView(line("${m.name}  •  $role", R.color.text_primary, 17f))
        box.addView(line(m.email))
        val status = when { !m.online -> "Offline"; !m.sharing -> "Online · lokasi dijeda"; else -> "Online" }
        box.addView(line("Status: $status", if (m.online) R.color.ok_green else R.color.text_secondary))
        m.lastSeenMs?.let { box.addView(line("Update lokasi: ${ago(it)}")) }
        m.speedKmh?.let { box.addView(line("Kecepatan: $it km/jam")) }
        m.distanceToCaptainM?.let { box.addView(line("Jarak ke Captain: ${dist(it)}")) }
        m.joinedAt?.let { box.addView(line("Bergabung: ${SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(it))}")) }
        if (m.muted) box.addView(line("Dibisukan (suara & chat)", R.color.danger))
        if (m.role != "captain") box.addView(Button(this).apply { text = "Kelola"; setOnClickListener { manage(m) } })
        return box
    }

    private fun ago(ms: Long): String = when { ms < 5_000 -> "baru saja"; ms < 60_000 -> "${ms / 1000} dtk lalu"; else -> "${ms / 60_000} mnt lalu" }
    private fun dist(m: Int): String = if (m < 1000) "$m m" else String.format(Locale.US, "%.1f km", m / 1000.0)

    private fun manage(m: AdminMember) {
        val gid = Prefs.groupId ?: return
        val items = arrayOf(
            if (m.muted) "Aktifkan suara & chat" else "Bisukan (suara & chat)",
            if (m.role == "sweeper") "Lepas peran Sweeper" else "Jadikan Sweeper",
            "Jadikan Road Captain",
            "Keluarkan dari grup",
        )
        AlertDialog.Builder(this).setTitle(m.name).setItems(items) { _, w ->
            when (w) {
                0 -> bg({ ApiClient.muteMember(gid, m.id, !m.muted) }) { list -> members = list; render() }
                1 -> bg({ ApiClient.patchGroup(gid, JSONObject().put("sweeperId", if (m.role == "sweeper") JSONObject.NULL else m.id)) }) { g -> TrackerState.group = g; load() }
                2 -> confirm("Jadikan ${m.name} Road Captain? Panel ini akan hilang dari akun Anda.") {
                    bg({ ApiClient.patchGroup(gid, JSONObject().put("captainId", m.id)) }) { g -> TrackerState.group = g; Toast.makeText(this, "${m.name} sekarang Road Captain", Toast.LENGTH_LONG).show(); finish() }
                }
                else -> confirm("Keluarkan ${m.name} dari grup?") { bg({ ApiClient.kick(gid, m.id) }) { load() } }
            }
        }.setNegativeButton("Batal", null).show()
    }

    private fun confirm(msg: String, ok: () -> Unit) =
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("Ya") { _, _ -> ok() }.setNegativeButton("Batal", null).show()

    /** Sends a chat message marked as an announcement (the server only honours the flag for the captain). */
    private fun announce() {
        val et = EditText(this).apply { hint = "Pesan untuk semua anggota"; maxLines = 3 }
        AlertDialog.Builder(this).setTitle("Umumkan ke grup").setView(et).setPositiveButton("Kirim") { _, _ ->
            val text = et.text.toString().trim()
            if (text.isNotEmpty()) {
                val ok = TrackerService.instance?.sendChat(text, announce = true) ?: false
                Toast.makeText(this, if (ok) "Pengumuman terkirim" else "Belum terhubung ke grup (mulai tracking dulu)", Toast.LENGTH_LONG).show()
            }
        }.setNegativeButton("Batal", null).show()
    }
}
