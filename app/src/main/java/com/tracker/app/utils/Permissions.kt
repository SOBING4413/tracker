package com.tracker.app.utils

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tracker.app.R

/** Asks for each permission only when the feature needs it, with a rationale; guides to Settings after "don't ask again". */
object Permissions {
    fun has(a: AppCompatActivity, p: String) = ContextCompat.checkSelfPermission(a, p) == PackageManager.PERMISSION_GRANTED

    fun request(a: AppCompatActivity, perm: String, whyRes: Int, code: Int, onGranted: () -> Unit) {
        if (has(a, perm)) return onGranted()
        val permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(a, perm) && a.getPreferences(0).getBoolean("asked_$perm", false)
        if (permanentlyDenied) {
            AlertDialog.Builder(a).setMessage(R.string.perm_denied_settings)
                .setPositiveButton("Settings") { _, _ ->
                    a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
                }.setNegativeButton("Cancel", null).show()
            return
        }
        AlertDialog.Builder(a).setMessage(whyRes).setPositiveButton("Continue") { _, _ ->
            a.getPreferences(0).edit().putBoolean("asked_$perm", true).apply()
            ActivityCompat.requestPermissions(a, arrayOf(perm), code)
        }.setNegativeButton("Not now", null).show()
    }

    fun requestLocationFlow(a: AppCompatActivity, onReady: () -> Unit) =
        request(a, Manifest.permission.ACCESS_FINE_LOCATION, R.string.perm_location_why, 10) {
            if (Build.VERSION.SDK_INT >= 33) request(a, Manifest.permission.POST_NOTIFICATIONS, R.string.perm_notif_why, 11) {}
            // Background location must be requested separately, after foreground (Android 10+).
            if (Build.VERSION.SDK_INT >= 29) request(a, Manifest.permission.ACCESS_BACKGROUND_LOCATION, R.string.perm_bg_why, 12) {}
            onReady()
        }
}
