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

    fun lastKnownGithubVersion(): String? = prefs.getString(KEY_KNOWN_GITHUB, null)

    fun lastReleaseNotes(): String? = prefs.getString(KEY_RELEASE_NOTES, null)

    fun etag(): String? = prefs.getString(KEY_ETAG, null)

    fun cachedReleaseJson(): String? = prefs.getString(KEY_CACHED_JSON, null)

    fun wifiOnlyAutomatic(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnlyAutomatic(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

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

    fun markKnownGithubVersion(version: String) {
        if (version.isBlank()) return
        prefs.edit().putString(KEY_KNOWN_GITHUB, version.trim()).apply()
    }

    fun markReleaseNotes(notes: String) {
        prefs.edit().putString(KEY_RELEASE_NOTES, notes).apply()
    }

    @Synchronized
    fun clearPendingIfAlreadyInstalled(installedVersion: String): Boolean {
        if (!PnsProductUpdate.pendingVersionAlreadyInstalled(
                peekPendingInstall()?.expectedVersion,
                installedVersion,
            )
        ) {
            return false
        }
        clearPendingInstall()
        return true
    }

    @Synchronized
    fun takePendingInstall(): PnsApkInstaller.Request? {
        val request = peekPendingInstall() ?: return null
        clearPendingInstall()
        return request
    }

    @Synchronized
    fun peekPendingInstall(): PnsApkInstaller.Request? {
        val url = prefs.getString(KEY_PENDING_URL, null) ?: return null
        val sha = prefs.getString(KEY_PENDING_SHA, null)
        val version = prefs.getString(KEY_PENDING_VER, null)
        val size = prefs.getLong(KEY_PENDING_SIZE, 0L)
        return PnsApkInstaller.Request(
            url = url,
            sha256Url = sha?.takeIf { it.isNotBlank() },
            expectedVersion = version?.takeIf { it.isNotBlank() },
            sizeBytes = size,
        )
    }

    @Synchronized
    fun savePendingInstall(request: PnsApkInstaller.Request) {
        prefs.edit()
            .putString(KEY_PENDING_URL, request.url)
            .putString(KEY_PENDING_SHA, request.sha256Url)
            .putString(KEY_PENDING_VER, request.expectedVersion)
            .putLong(KEY_PENDING_SIZE, request.sizeBytes)
            .apply()
    }

    @Synchronized
    fun clearPendingInstall() {
        prefs.edit()
            .remove(KEY_PENDING_URL)
            .remove(KEY_PENDING_SHA)
            .remove(KEY_PENDING_VER)
            .remove(KEY_PENDING_SIZE)
            .apply()
    }

    fun saveFetchCache(etag: String?, json: String) {
        val editor = prefs.edit().putString(KEY_CACHED_JSON, json)
        if (!etag.isNullOrBlank()) {
            editor.putString(KEY_ETAG, etag)
        }
        editor.apply()
    }

    companion object {
        const val PREFS = "pns_updates"
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val KEY_LAST_SEEN = "last_seen_version"
        private const val KEY_DISMISSED = "dismissed_version"
        private const val KEY_KNOWN_GITHUB = "known_github_version"
        private const val KEY_RELEASE_NOTES = "release_notes"
        private const val KEY_ETAG = "releases_etag"
        private const val KEY_CACHED_JSON = "releases_json"
        private const val KEY_PENDING_URL = "pending_install_url"
        private const val KEY_PENDING_SHA = "pending_install_sha"
        private const val KEY_PENDING_VER = "pending_install_version"
        private const val KEY_PENDING_SIZE = "pending_install_size"
        private const val KEY_WIFI_ONLY = "wifi_only_automatic"
    }
}
