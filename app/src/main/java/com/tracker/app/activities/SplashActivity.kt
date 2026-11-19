package com.tracker.app.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.tracker.app.R
import com.tracker.app.utils.Prefs

class SplashActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_splash)
        findViewById<android.view.View>(R.id.logo).apply { alpha = 0f; scaleX = .8f; scaleY = .8f; animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start() }
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, if (Prefs.token != null) MainActivity::class.java else LoginActivity::class.java)); finish()
        }, 1100)
    }
}
