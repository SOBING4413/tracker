package com.tracker.app.activities

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.tracker.app.R
import com.tracker.app.utils.Prefs

/** Loading screen: logo fade/scale-in, a green progress bar and rotating status text. */
class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var progressAnim: ObjectAnimator? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_splash)

        listOf(R.id.logoIcon, R.id.logo, R.id.tagline).forEachIndexed { i, id ->
            findViewById<android.view.View>(id).apply {
                alpha = 0f; scaleX = .85f; scaleY = .85f
                animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(i * 120L).setDuration(600).start()
            }
        }

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        findViewById<TextView>(R.id.version).text = if (version != null) getString(R.string.version_fmt, version) else ""

        val bar = findViewById<LinearProgressIndicator>(R.id.progress)
        progressAnim = ObjectAnimator.ofInt(bar, "progress", 0, 100).apply {
            duration = LOAD_MS; interpolator = AccelerateDecelerateInterpolator(); start()
        }

        val label = findViewById<TextView>(R.id.loadingText)
        val steps = listOf(R.string.splash_step1, R.string.splash_step2, R.string.splash_step3).map { getString(it) }
        steps.forEachIndexed { i, text -> handler.postDelayed({ label.text = text }, LOAD_MS * i / steps.size) }

        handler.postDelayed({
            if (isFinishing) return@postDelayed
            startActivity(Intent(this, if (Prefs.token != null) MainActivity::class.java else LoginActivity::class.java)); finish()
        }, LOAD_MS + 200)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); progressAnim?.cancel(); super.onDestroy()
    }

    private companion object { const val LOAD_MS = 2200L }
}
