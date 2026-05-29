package dev.pointandshoot.fleet

import android.content.Context

/**
 * Developer / automation prefs for fleet policy plugins (Milestone **16.4**).
 */
object FleetPolicyPreferences {
    private const val PREFS_NAME = "pns_fleet_policy"
    private const val KEY_LEGACY_OP13 = "legacy_op13_fleet_policy_enabled"

    fun legacyOp13Enabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LEGACY_OP13, false)

    fun setLegacyOp13Enabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LEGACY_OP13, enabled)
            .apply()
    }
}
