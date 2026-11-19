package com.tracker.app.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tracker.app.R
import com.tracker.app.fragments.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_main)
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener {
            show(when (it.itemId) { R.id.nav_map -> MapFragment(); R.id.nav_talk -> TalkFragment(); R.id.nav_alerts -> AlertsFragment(); R.id.nav_settings -> SettingsFragment(); else -> GroupFragment() }); true
        }
        if (b == null) nav.selectedItemId = if (intent.hasExtra("focus")) R.id.nav_map else R.id.nav_group
    }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); if (i.hasExtra("focus")) findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_map }
    private fun show(f: Fragment) = supportFragmentManager.beginTransaction().setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out).replace(R.id.container, f).commit()
}
