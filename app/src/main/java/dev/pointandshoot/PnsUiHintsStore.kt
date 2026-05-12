package dev.pointandshoot

import android.content.Context

/**
 * One-shot UX hints (immersive gestures, etc.). Separate from [WelcomePrefs] so welcome versioning
 * does not wipe hint state.
 */
object PnsUiHintsStore {
    private const val PREFS = "pns_ui_hints"
    private const val KEY_IMMERSIVE_GESTURE_TIP = "seen_immersive_gesture_tip"

    fun hasSeenImmersiveGestureTip(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IMMERSIVE_GESTURE_TIP, false)
    }

    fun markImmersiveGestureTipSeen(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IMMERSIVE_GESTURE_TIP, true)
            .apply()
    }
}
