package dev.pointandshoot

import android.content.Context
import android.util.Log

/**
 * Crash-loop guard for experimental app-breaking lanes.
 */
object ExperimentalSafeModeStore {
    private const val TAG = "PNS.SafeMode"
    private const val PREFS = "pns_experimental_safe_mode"
    private const val KEY_LAUNCH_ATTEMPTS = "launch_attempts"
    private const val KEY_SAFE_MODE_ACTIVE = "safe_mode_active"
    private const val KEY_LAST_TRIGGER_REASON = "last_trigger_reason"
    private const val MAX_FAILED_LAUNCH_ATTEMPTS = 3

    fun recordAppLaunchAttempt(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val attempts = prefs.getInt(KEY_LAUNCH_ATTEMPTS, 0) + 1
        val shouldTriggerSafeMode = attempts >= MAX_FAILED_LAUNCH_ATTEMPTS
        prefs.edit()
            .putInt(KEY_LAUNCH_ATTEMPTS, attempts)
            .putBoolean(KEY_SAFE_MODE_ACTIVE, shouldTriggerSafeMode || prefs.getBoolean(KEY_SAFE_MODE_ACTIVE, false))
            .apply()
        if (shouldTriggerSafeMode) {
            prefs.edit().putString(KEY_LAST_TRIGGER_REASON, "launch_attempt_threshold").apply()
            Log.w(TAG, "safeMode=on reason=launch_attempt_threshold attempts=$attempts")
        } else {
            Log.i(TAG, "launchAttempt attempts=$attempts safeMode=${prefs.getBoolean(KEY_SAFE_MODE_ACTIVE, false)}")
        }
    }

    fun markAppLaunchHealthy(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAUNCH_ATTEMPTS, 0)
            .apply()
    }

    fun isSafeModeActive(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SAFE_MODE_ACTIVE, false)

    fun forceSafeMode(context: Context, reason: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SAFE_MODE_ACTIVE, true)
            .putString(KEY_LAST_TRIGGER_REASON, reason)
            .apply()
        Log.w(TAG, "safeMode=on reason=$reason")
    }

    fun clearSafeMode(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SAFE_MODE_ACTIVE, false)
            .putInt(KEY_LAUNCH_ATTEMPTS, 0)
            .apply()
        Log.i(TAG, "safeMode=off")
    }

    fun disableExperimentalFlags(context: Context) {
        val current = HudSettings.load(context.applicationContext)
        if (!current.enableExperimentalAppBreakingFeatures &&
            !current.enableExperimentalMaxResolutionUnlock &&
            !current.enableExperimentalVendorSessionKeys
        ) {
            return
        }
        HudSettings.save(
            context.applicationContext,
            current.copy(
                enableExperimentalAppBreakingFeatures = false,
                enableExperimentalMaxResolutionUnlock = false,
                enableExperimentalVendorSessionKeys = false,
            ),
        )
        Log.w(TAG, "safeMode disabled experimental flags")
    }
}

