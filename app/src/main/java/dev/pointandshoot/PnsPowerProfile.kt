@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context

/** One switch for preview FPS / capture ambition. */
enum class PnsPowerProfile(val storageId: String, val label: String, val fpsCap: Int?) {
    Performance("performance", "Performance", null),
    Balanced("balanced", "Balanced", 60),
    Endurance("endurance", "Endurance", 30),
    ;

    companion object {
        private const val PREFS = "pns_power_profile"
        private const val KEY = "profile"

        fun fromStorage(id: String?): PnsPowerProfile =
            entries.firstOrNull { it.storageId == id } ?: Balanced

        fun load(context: Context): PnsPowerProfile =
            fromStorage(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, Balanced.storageId),
            )

        fun save(context: Context, profile: PnsPowerProfile) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, profile.storageId)
                .apply()
        }

        fun applyCap(userFps: Int, profile: PnsPowerProfile): Int {
            val cap = profile.fpsCap ?: return userFps
            return minOf(userFps, cap)
        }
    }
}
