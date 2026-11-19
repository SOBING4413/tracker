package com.tracker.app.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tracker.app.R

/** Credits page: author, source code and community links. */
class CreditsActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_credits)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        findViewById<TextView>(R.id.creditsVersion).text = if (version != null) getString(R.string.version_fmt, version) else ""
        findViewById<android.view.View>(R.id.btnGithub).setOnClickListener { open(GITHUB) }
        findViewById<android.view.View>(R.id.btnRepo).setOnClickListener { open(REPO) }
        findViewById<android.view.View>(R.id.btnDiscord).setOnClickListener { open(DISCORD) }
        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun open(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (e: ActivityNotFoundException) { Toast.makeText(this, getString(R.string.credits_no_browser), Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val GITHUB = "https://github.com/SOBING4413"
        const val REPO = "https://github.com/SOBING4413/tracker"
        const val DISCORD = "https://discord.gg/9nsub2yx4V"
    }
}
