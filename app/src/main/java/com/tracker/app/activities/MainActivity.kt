package com.tracker.app.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tracker.app.R
import com.tracker.app.fragments.*
import com.tracker.app.services.TrackerState

class MainActivity : AppCompatActivity() {
    private val refresh = { updateBadge() }
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_main)
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener {
            show(when (it.itemId) { R.id.nav_map -> MapFragment(); R.id.nav_talk -> TalkFragment(); R.id.nav_alerts -> AlertsFragment(); R.id.nav_settings -> SettingsFragment(); else -> GroupFragment() }); true
        }
        if (b == null) nav.selectedItemId = target(intent)
    }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = target(i) }
    override fun onStart() { super.onStart(); TrackerState.listen(refresh); updateBadge() }
    override fun onStop() { super.onStop(); TrackerState.unlisten(refresh) }

    /** Which tab an intent asks for: a chat notification opens Talk > Chat, an alert opens the map, otherwise Group. */
    private fun target(i: Intent): Int = when {
        i.hasExtra("openChat") -> { TrackerState.wantChatTab = true; R.id.nav_talk }
        i.hasExtra("focus") -> R.id.nav_map
        else -> R.id.nav_group
    }
    /** Unread chat messages show as a badge on the Talk tab. */
    private fun updateBadge() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav); val n = TrackerState.unreadChat
        if (n > 0) nav.getOrCreateBadge(R.id.nav_talk).number = n else nav.removeBadge(R.id.nav_talk)
    }
    private fun show(f: Fragment) = supportFragmentManager.beginTransaction().setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out).replace(R.id.container, f).commit()
}
