package dev.pointandshoot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager

object PnsAppUpdates {
    fun evaluateOnLaunch(
        context: Context,
        now: Long = System.currentTimeMillis(),
        fetchLatest: () -> PnsProductUpdate.GithubRelease? = {
            PnsGithubRelease.fetchLatest(context)
        },
    ): PnsProductUpdate.LaunchPrompt {
        val prefs = PnsUpdatePrefs(context)
        val current = PnsAppInfo.versionName(context)
        val result =
            PnsProductUpdate.evaluateLaunch(
                currentVersion = current,
                lastSeenVersion = prefs.lastSeenVersion(),
                lastCheckAt = prefs.lastCheckAt(),
                dismissedVersion = prefs.dismissedVersion(),
                now = now,
                fetchLatest = fetchLatest,
                skipNetwork = shouldSkipAutomaticNetwork(context),
            )
        result.markSeenVersion?.let { prefs.markVersionSeen(it) }
        result.markCheckedAt?.let { prefs.markChecked(it) }
        result.markKnownGithubVersion?.let { prefs.markKnownGithubVersion(it) }
        result.markReleaseNotes?.let { prefs.markReleaseNotes(it) }
        return result.prompt
    }

    fun evaluateManualCheck(
        context: Context,
        now: Long = System.currentTimeMillis(),
        fetchLatest: () -> PnsProductUpdate.GithubRelease? = {
            PnsGithubRelease.fetchLatest(context)
        },
    ): PnsProductUpdate.LaunchEvaluation {
        val prefs = PnsUpdatePrefs(context)
        val current = PnsAppInfo.versionName(context)
        val result =
            PnsProductUpdate.evaluateLaunch(
                currentVersion = current,
                lastSeenVersion = prefs.lastSeenVersion(),
                lastCheckAt = prefs.lastCheckAt(),
                dismissedVersion = prefs.dismissedVersion(),
                now = now,
                fetchLatest = fetchLatest,
                skipNetwork = false,
                forceCheck = true,
            )
        result.markSeenVersion?.let { prefs.markVersionSeen(it) }
        result.markCheckedAt?.let { prefs.markChecked(it) }
        result.markKnownGithubVersion?.let { prefs.markKnownGithubVersion(it) }
        result.markReleaseNotes?.let { prefs.markReleaseNotes(it) }
        return result
    }

    fun markDonateSeen(context: Context, currentVersion: String) {
        PnsUpdatePrefs(context).markVersionSeen(currentVersion)
    }

    fun markUpdateDismissed(context: Context, version: String) {
        PnsUpdatePrefs(context).markChecked(System.currentTimeMillis(), version)
    }

    /** Automatic checks skip when offline, metered, saver, Wi‑Fi-only, or an install is running. */
    fun shouldSkipAutomaticNetwork(context: Context): Boolean {
        if (PnsApkInstaller.isInstallInFlight()) return true
        if (isPowerSaveMode(context)) return true
        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true
        if (PnsUpdatePrefs(context).wifiOnlyAutomatic() &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        ) {
            return true
        }
        return cm.isActiveNetworkMetered
    }

    internal fun isPowerSaveMode(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isPowerSaveMode
    }

    fun manualCheckFailureMessage(): String =
        when (PnsGithubRelease.lastStatus) {
            PnsGithubRelease.Status.RateLimited -> "GitHub rate limit. Try again later."
            else -> "Couldn't reach GitHub."
        }
}
