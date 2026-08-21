package dev.pointandshoot

import android.content.Context

/**
 * Device-local donate/update timestamps. Not Android-backed up and not settings-exported
 * (do not peer-sync).
 */
class PnsUpdatePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastCheckAt(): Long? = prefs.getLong(KEY_LAST_CHECK, -1L).takeIf { it > 0L }

    fun lastSeenVersion(): String? = prefs.getString(KEY_LAST_SEEN, null)

    fun dismissedVersion(): String? = prefs.getString(KEY_DISMISSED, null)

    fun markChecked(now: Long, dismissedVersion: String? = null) {
        val editor = prefs.edit().putLong(KEY_LAST_CHECK, now)
        if (!dismissedVersion.isNullOrBlank()) {
            editor.putString(KEY_DISMISSED, dismissedVersion)
        }
        editor.apply()
    }

    fun markVersionSeen(version: String) {
        prefs.edit().putString(KEY_LAST_SEEN, version).apply()
    }

    companion object {
        const val PREFS = "pns_updates"
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val KEY_LAST_SEEN = "last_seen_version"
        private const val KEY_DISMISSED = "dismissed_version"
    }
}
