package com.tracker.app.utils

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("tracker", Context.MODE_PRIVATE) }
    var token: String? get() = sp.getString("token", null); set(v) = sp.edit().putString("token", v).apply()
    var userId: String? get() = sp.getString("uid", null); set(v) = sp.edit().putString("uid", v).apply()
    var userName: String? get() = sp.getString("uname", null); set(v) = sp.edit().putString("uname", v).apply()
    var groupId: String? get() = sp.getString("gid", null); set(v) = sp.edit().putString("gid", v).apply()
    var gpsIntervalSec: Int get() = sp.getInt("gps_int", 5); set(v) = sp.edit().putInt("gps_int", v).apply()
    var thresholdM: Int get() = sp.getInt("thr", 1100); set(v) = sp.edit().putInt("thr", v).apply()
    var alertSound: Boolean get() = sp.getBoolean("snd", true); set(v) = sp.edit().putBoolean("snd", v).apply()
    var alertVibrate: Boolean get() = sp.getBoolean("vib", true); set(v) = sp.edit().putBoolean("vib", v).apply()
    /** Voice mode: false = push-to-talk (hold the button), true = always-on mic (voice-activated). */
    var openMic: Boolean get() = sp.getBoolean("open_mic", false); set(v) = sp.edit().putBoolean("open_mic", v).apply()
    /** Always-on mic sensitivity 0..100 (higher = triggers on quieter speech). */
    var voxLevel: Int get() = sp.getInt("vox_level", 50); set(v) = sp.edit().putInt("vox_level", v).apply()
    var shareLocation: Boolean get() = sp.getBoolean("share", true); set(v) = sp.edit().putBoolean("share", v).apply()
    fun clearSession() = sp.edit().remove("token").remove("uid").remove("uname").remove("gid").apply()
}
