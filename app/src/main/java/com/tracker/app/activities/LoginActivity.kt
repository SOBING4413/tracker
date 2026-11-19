package com.tracker.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.tracker.app.R
import com.tracker.app.network.ApiClient
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_login)
        val email = findViewById<EditText>(R.id.email); val pw = findViewById<EditText>(R.id.password); val name = findViewById<EditText>(R.id.displayName)
        val err = findViewById<TextView>(R.id.error)
        fun go(register: Boolean) {
            err.text = ""
            val e = email.text.toString().trim(); val p = pw.text.toString(); val n = name.text.toString().trim()
            if (register && n.isEmpty()) { err.text = "Enter a display name to register"; return }
            thread {
                val result = runCatching { if (register) ApiClient.register(e, p, n) else ApiClient.login(e, p) }
                runOnUiThread {
                    result.onSuccess { startActivity(Intent(this, MainActivity::class.java)); finish() }
                        .onFailure { err.text = it.message ?: "Network error" }
                }
            }
        }
        findViewById<Button>(R.id.btnLogin).setOnClickListener { go(false) }
        findViewById<Button>(R.id.btnRegister).setOnClickListener { go(true) }
    }
}
