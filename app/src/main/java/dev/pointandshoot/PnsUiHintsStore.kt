package dev.pointandshoot

import android.content.Context

/**
 * One-shot UX hints (immersive gestures, etc.). Separate from [WelcomePrefs] so welcome versioning
 * does not wipe hint state.
 */
object PnsUiHintsStore {
    private const val PREFS = "pns_ui_hints"
    private const val KEY_IMMERSIVE_GESTURE_TIP = "seen_immersive_gesture_tip"
    private const val KEY_FRONT_REAR_SPOTLIGHT = "seen_front_rear_spotlight"
    private const val KEY_FLASH_LONG_PRESS_MENU_TIP = "seen_flash_long_press_menu_tip"

    fun hasSeenImmersiveGestureTip(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IMMERSIVE_GESTURE_TIP, false)
    }

    fun markImmersiveGestureTipSeen(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IMMERSIVE_GESTURE_TIP, true)
            .apply()
    }

    fun hasSeenFrontRearSpotlight(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FRONT_REAR_SPOTLIGHT, false)
    }

    fun markFrontRearSpotlightSeen(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FRONT_REAR_SPOTLIGHT, true)
            .apply()
    }

    fun hasSeenFlashLongPressMenuTip(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FLASH_LONG_PRESS_MENU_TIP, false)
    }

    fun markFlashLongPressMenuTipSeen(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FLASH_LONG_PRESS_MENU_TIP, true)
            .apply()
    }
}
