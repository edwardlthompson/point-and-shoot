package dev.pointandshoot

import android.content.Intent

/**
 * Pure launch-routing helpers for [MainActivity] (JVM-testable).
 *
 * Safe Mode disables experimental unlock flags and may toast — it must **not** remap the
 * cold-start destination to the Engineering hub (`probehub`).
 */
object LaunchScreenPolicy {
    /**
     * True when the system home launcher started the activity (icon tap).
     * Home launches set [Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED]; typical ADB
     * `am start -n … --activity-clear-task` does not.
     */
    fun isHomeLauncherIconLaunch(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.action != Intent.ACTION_MAIN) return false
        if (!intent.hasCategory(Intent.CATEGORY_LAUNCHER)) return false
        return intent.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED != 0
    }

    /**
     * Effective `pns_screen` after home-icon sticky-extra hygiene.
     * Callers must not remap Safe Mode to [PNS_SCREEN_PROBE_HUB] — pass the normal
     * gate/resolved screen; [safeModeActive] is documented for call-site clarity only.
     */
    @Suppress("UNUSED_PARAMETER")
    fun effectiveLaunchScreen(
        gateOrResolvedScreen: String?,
        safeModeActive: Boolean,
        homeLauncherIconLaunch: Boolean,
    ): String? {
        if (homeLauncherIconLaunch) return null
        return gateOrResolvedScreen
    }
}
