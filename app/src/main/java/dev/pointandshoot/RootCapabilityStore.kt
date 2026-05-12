package dev.pointandshoot

import android.content.Context

/**
 * Persists the last-known [RootCapability.RootState] from [RootSettingsScreen]
 * so preview/capture paths can consult root-eligibility without forking `su`.
 *
 * Stage-2 **Granted** / **Denied** outcomes survive process death; stage-1 static
 * outcomes are also persisted when the user taps Re-probe. This mirrors the
 * drawer UX contract: silent SU prompts on cold start stay forbidden, but
 * features that already gated on "user tapped Grant Su once" can stay enabled.
 */
object RootCapabilityStore {

    private const val PREFS = "pns_root_capability"
    private const val KEY_ROOT_STATE = "last_root_state"

    fun loadOrUnknown(context: Context): RootCapability.RootState {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ROOT_STATE, null)
            ?: return RootCapability.RootState.Unknown
        return runCatching {
            RootCapability.RootState.valueOf(raw)
        }.getOrDefault(RootCapability.RootState.Unknown)
    }

    fun save(context: Context, state: RootCapability.RootState) {
        if (state == RootCapability.RootState.Unknown) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOT_STATE, state.name)
            .apply()
    }
}
