package dev.pointandshoot

import android.content.Context

/**
 * Opt-in for rooted devices: when [HighlightAeModeSupport] cannot resolve
 * `CONTROL_AE_MODE_ON_HIGHLIGHT_WEIGHTED` via reflection, still try **non-standard**
 * integers listed in [android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES]
 * (vendor/OEM extensions).
 *
 * Stored intent is **ignored** unless [RootCapabilityStore] reports [RootCapability.RootState.Granted];
 * the Root Only UI disables the switch until SU grant succeeds.
 */
object VendorHighlightAePrefs {

    private const val PREFS_NAME = "pns_vendor_highlight_ae"
    private const val KEY_TRY_EXTRA_MODES = "try_extra_highlight_ae_modes"

    fun isTryExtraModesEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TRY_EXTRA_MODES, false)

    fun setTryExtraModesEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TRY_EXTRA_MODES, enabled)
            .apply()
    }
}
