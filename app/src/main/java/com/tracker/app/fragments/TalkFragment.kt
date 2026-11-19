package com.tracker.app.fragments

import android.annotation.SuppressLint
import android.Manifest
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.os.Bundle
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.tracker.app.R
import com.tracker.app.activities.MainActivity
import com.tracker.app.models.ChatMessage
import com.tracker.app.services.TrackerService
import com.tracker.app.services.TrackerState
import com.tracker.app.utils.Permissions
import com.tracker.app.utils.Prefs
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/** Two sub-tabs: "Suara" (push-to-talk or always-on mic, mute, volume, SOS) and "Chat" (group chat). */
class TalkFragment : Fragment() {
    private lateinit var v: View
    private val refresh = { render() }
    private var binding = false            // true while render() sets control values, so listeners ignore it
    private var showingChat = false
    private var renderedLastId: String? = null
    private var renderedCount = -1

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_talk, c, false).also { v = it }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, s: Bundle?) {
        val tabs = v.findViewById<TabLayout>(R.id.talkTabs)
        tabs.addTab(tabs.newTab().setText("Suara")); tabs.addTab(tabs.newTab().setText("Chat"))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { showingChat = t.position == 1; render() }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
        if (TrackerState.wantChatTab) { TrackerState.wantChatTab = false; tabs.getTabAt(1)?.select() }

        // ---- voice ----
        v.findViewById<View>(R.id.ptt).setOnTouchListener { b, e ->
            val svc = TrackerService.instance
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (Prefs.openMic) Toast.makeText(context, "Mic selalu aktif: cukup bicara, atau matikan mode itu untuk push-to-talk", Toast.LENGTH_SHORT).show()
                    else if (svc == null) Toast.makeText(context, "Mulai tracking di tab Group dulu", Toast.LENGTH_SHORT).show()
                    else Permissions.request(activity as MainActivity, Manifest.permission.RECORD_AUDIO, R.string.perm_mic_why, 20) { b.isPressed = true; svc.pttDown() }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { b.isPressed = false; if (!Prefs.openMic) svc?.pttUp() }
            }
            true
        }
        v.findViewById<Switch>(R.id.openMic).setOnCheckedChangeListener { _, on ->
            if (binding) return@setOnCheckedChangeListener
            val svc = TrackerService.instance
            if (!on) { Prefs.openMic = false; svc?.setOpenMic(false); render(); return@setOnCheckedChangeListener }
            Permissions.request(activity as MainActivity, Manifest.permission.RECORD_AUDIO, R.string.perm_mic_why, 21) {
                Prefs.openMic = true
                if (svc != null && !svc.setOpenMic(true)) { Prefs.openMic = false; Toast.makeText(context, "Mic tidak bisa diaktifkan (sedang dipakai aplikasi lain?)", Toast.LENGTH_LONG).show() }
                render()
            }
            render()   // izin belum diberikan / dialog tampil: kembalikan saklar sesuai status sebenarnya
        }
        v.findViewById<SeekBar>(R.id.vox).apply {
            progress = Prefs.voxLevel
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { if (u) { Prefs.voxLevel = p; TrackerService.instance?.streamer?.sensitivity = p / 100f } }
                override fun onStartTrackingTouch(s: SeekBar) {}; override fun onStopTrackingTouch(s: SeekBar) {}
            })
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

        // ---- chat ----
        val input = v.findViewById<EditText>(R.id.chatInput)
        val send = {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                val svc = TrackerService.instance
                if (svc == null || !svc.sendChat(text)) Toast.makeText(context, "Belum terhubung ke grup", Toast.LENGTH_SHORT).show()
                else input.setText("")
            }
        }
        v.findViewById<View>(R.id.chatSend).setOnClickListener { send() }
        input.setOnEditorActionListener { _, _, _ -> send(); true }
    }
    override fun onStart() { super.onStart(); TrackerState.listen(refresh); render() }
    override fun onStop() { super.onStop(); TrackerState.unlisten(refresh) }

    private fun render() {
        if (!isAdded) return
        v.findViewById<View>(R.id.voicePane).visibility = if (showingChat) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.chatPane).visibility = if (showingChat) View.VISIBLE else View.GONE
        if (showingChat) renderChat() else renderVoice()
    }

    private fun renderVoice() {
        v.findViewById<TextView>(R.id.conn).text = TrackerState.connLabel()
        val h = TrackerState.floorHolder
        v.findViewById<TextView>(R.id.speaker).text = if (h == null) "Channel idle" else (TrackerState.group?.members?.firstOrNull { it.id == h }?.name ?: "Rider") + " sedang berbicara"
        val open = Prefs.openMic
        binding = true
        v.findViewById<Switch>(R.id.openMic).isChecked = open
        binding = false
        v.findViewById<View>(R.id.voxRow).visibility = if (open) View.VISIBLE else View.GONE
        v.findViewById<Button>(R.id.ptt).apply {
            isEnabled = !open
            text = if (open) "MIC AKTIF\nbicara saja" else "TAHAN\nUNTUK BICARA"
        }
        v.findViewById<TextView>(R.id.openMicHint).text = when {
            TrackerState.isMuted() -> "Anda dibisukan oleh Captain: suara dan chat dinonaktifkan."
            open && TrackerService.instance == null -> "Mic selalu aktif akan jalan setelah tracking dimulai."
            open -> "Mic mendeteksi suara Anda dan memancar otomatis. Hanya satu orang bisa bicara sekaligus; Captain bisa memotong. Turunkan sensitivitas jika terpicu angin/mesin."
            else -> "Mode push-to-talk: tahan tombol untuk bicara."
        }
    }

    private fun renderChat() {
        TrackerState.unreadChat = 0
        val muted = TrackerState.isMuted()
        v.findViewById<View>(R.id.chatMuted).visibility = if (muted) View.VISIBLE else View.GONE
        v.findViewById<EditText>(R.id.chatInput).isEnabled = !muted
        v.findViewById<View>(R.id.chatSend).isEnabled = !muted
        val list = TrackerState.chat
        if (list.size == renderedCount && list.lastOrNull()?.id == renderedLastId) return   // nothing new
        renderedCount = list.size; renderedLastId = list.lastOrNull()?.id
        val box = v.findViewById<LinearLayout>(R.id.chatList); box.removeAllViews()
        if (list.isEmpty()) box.addView(TextView(requireContext()).apply { text = "Belum ada pesan. Tulis sesuatu untuk grup."; setTextColor(resources.getColor(R.color.text_secondary, null)) })
        list.forEach { box.addView(bubble(it)) }
        val sv = v.findViewById<ScrollView>(R.id.chatScroll); sv.post { sv.fullScroll(View.FOCUS_DOWN) }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private fun bubble(m: ChatMessage): View {
        val mine = m.userId == Prefs.userId
        val head = when { m.announce -> "PENGUMUMAN · ${m.name}"; mine -> "Anda"; else -> m.name }
        return TextView(requireContext()).apply {
            text = "$head  ${timeFmt.format(Date(m.ts))}\n${m.text}"
            textSize = 15f; setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextColor(resources.getColor(if (m.announce) R.color.bg_dark else R.color.text_primary, null))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (m.announce) resources.getColor(R.color.accent, null) else if (mine) 0xFF2B3A2F.toInt() else resources.getColor(R.color.surface_dark, null))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (mine) Gravity.END else Gravity.START; setMargins(0, dp(4), 0, dp(4))
            }
        }
    }
}
