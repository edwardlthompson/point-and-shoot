package dev.pointandshoot

import android.content.Context

/**
 * Tracks whether the user has stepped through the permission welcome flow.
 * Bumped when copy/steps change so existing installs see the new explanations once.
 */
object WelcomePrefs {
    private const val PREFS = "pns_welcome_flow"
    private const val KEY_COMPLETED_AT_VERSION = "permission_onboarding_version"
    private const val CURRENT_FLOW_VERSION = 3

    fun hasCompletedPermissionOnboarding(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_COMPLETED_AT_VERSION, 0) >= CURRENT_FLOW_VERSION
    }

    fun markPermissionOnboardingComplete(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_COMPLETED_AT_VERSION, CURRENT_FLOW_VERSION)
            .apply()
    }

    /** Clears onboarding so the welcome flow shows again (debug / QA). */
    fun resetPermissionOnboardingForDebug(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_COMPLETED_AT_VERSION)
            .apply()
    }
}
