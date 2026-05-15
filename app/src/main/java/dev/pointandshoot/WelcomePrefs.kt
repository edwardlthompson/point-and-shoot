package dev.pointandshoot

import android.content.Context

/**
 * Tracks whether the user has stepped through the permission welcome flow.
 * Bumped when copy/steps change so existing installs see the new explanations once.
 */
object WelcomePrefs {
    private const val PREFS = "pns_welcome_flow"
    private const val KEY_COMPLETED_AT_VERSION = "permission_onboarding_version"
    private const val KEY_SKIPPED_OPTIONAL_RECORD_AUDIO = "skipped_optional_record_audio"
    private const val KEY_SKIPPED_OPTIONAL_FINE_LOCATION = "skipped_optional_fine_location"
    private const val CURRENT_FLOW_VERSION = 4

    fun hasCompletedPermissionOnboarding(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_COMPLETED_AT_VERSION, 0) >= CURRENT_FLOW_VERSION
    }

    fun markPermissionOnboardingComplete(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_COMPLETED_AT_VERSION, CURRENT_FLOW_VERSION)
            .apply()
    }

    fun hasSkippedOptionalRecordAudio(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SKIPPED_OPTIONAL_RECORD_AUDIO, false)
    }

    fun markSkippedOptionalRecordAudio(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SKIPPED_OPTIONAL_RECORD_AUDIO, true)
            .apply()
    }

    fun hasSkippedOptionalFineLocation(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SKIPPED_OPTIONAL_FINE_LOCATION, false)
    }

    fun markSkippedOptionalFineLocation(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SKIPPED_OPTIONAL_FINE_LOCATION, true)
            .apply()
    }

    /** Clears onboarding so the welcome flow shows again (debug / QA). */
    fun resetPermissionOnboardingForDebug(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_COMPLETED_AT_VERSION)
            .remove(KEY_SKIPPED_OPTIONAL_RECORD_AUDIO)
            .remove(KEY_SKIPPED_OPTIONAL_FINE_LOCATION)
            .apply()
    }
}
