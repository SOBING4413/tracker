package com.tracker.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tracker.app.R
import com.tracker.app.services.TrackerService
import org.json.JSONObject

/** Full-screen, call-like alert shown over the lock screen where Android allows it. */
class AlertActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_alert)
        val uid = intent.getStringExtra("userId")
        findViewById<TextView>(R.id.alertTitle).text = intent.getStringExtra("title")
        findViewById<TextView>(R.id.alertBody).text = intent.getStringExtra("body")
        findViewById<Button>(R.id.btnConfirm).setOnClickListener { TrackerService.instance?.send(JSONObject().put("t", "ack").put("userId", uid)); finish() }
        findViewById<Button>(R.id.btnLocate).setOnClickListener { startActivity(Intent(this, MainActivity::class.java).putExtra("focus", uid)); finish() }
    }
}
