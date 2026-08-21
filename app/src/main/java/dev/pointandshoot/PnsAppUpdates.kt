package dev.pointandshoot

import android.content.Context

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
            )
        result.markSeenVersion?.let { prefs.markVersionSeen(it) }
        result.markCheckedAt?.let { prefs.markChecked(it) }
        return result.prompt
    }

    fun markDonateSeen(context: Context, currentVersion: String) {
        PnsUpdatePrefs(context).markVersionSeen(currentVersion)
    }

    fun markUpdateDismissed(context: Context, version: String) {
        PnsUpdatePrefs(context).markChecked(System.currentTimeMillis(), version)
    }
}
