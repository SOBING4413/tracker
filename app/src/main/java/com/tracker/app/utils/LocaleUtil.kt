package com.tracker.app.utils

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tracker.app.R
import com.tracker.app.notifications.AlertNotifier

/** In-app language choice (system default / English / Indonesian). Source of truth is AppCompatDelegate's application locales. */
object LocaleUtil {
    private val tags = listOf("", "en", "id")

    /** Applies a language tag ("" = follow the system). Recreates visible activities. */
    fun apply(tag: String) = AppCompatDelegate.setApplicationLocales(
        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))

    /** Current choice as an index into [tags]. */
    private fun currentIndex(): Int {
        val l = AppCompatDelegate.getApplicationLocales()
        if (l.isEmpty) return 0
        return when (l[0]?.language) { "en" -> 1; "id", "in" -> 2; else -> 0 }
    }

    /** Context whose resources follow the chosen language; for Services/notifications that have no AppCompat activity. */
    fun wrap(c: Context): Context {
        val l = AppCompatDelegate.getApplicationLocales()
        val loc = if (l.isEmpty) null else l[0]
        if (loc == null) return c
        val cfg = Configuration(c.resources.configuration); cfg.setLocale(loc)
        return c.createConfigurationContext(cfg)
    }

    fun currentLabel(c: Context): String =
        c.getString(listOf(R.string.lang_system, R.string.lang_english, R.string.lang_indonesian)[currentIndex()])

    fun showPicker(a: AppCompatActivity, onChanged: () -> Unit = {}) {
        val names = arrayOf(a.getString(R.string.lang_system), a.getString(R.string.lang_english), a.getString(R.string.lang_indonesian))
        AlertDialog.Builder(a).setTitle(R.string.settings_language_title)
            .setSingleChoiceItems(names, currentIndex()) { d, which ->
                d.dismiss()
                if (which == currentIndex()) return@setSingleChoiceItems
                Prefs.lang = tags[which]
                apply(tags[which])
                AlertNotifier.createChannels(a.applicationContext)   // refresh channel names in the new language
                onChanged()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
}
