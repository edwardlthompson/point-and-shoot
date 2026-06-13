package dev.pointandshoot.fleet

import android.content.Context

/** Optional AnTuTu score included with leaderboard parity submissions. */
object LeaderboardAntutuPrefs {
    private const val PREFS = "pns_leaderboard_antutu"
    private const val MIN_TOTAL_SCORE = 500_000
    private const val KEY_TOTAL = "total"
    private const val KEY_CPU = "cpu"
    private const val KEY_GPU = "gpu"
    private const val KEY_MEM = "mem"
    private const val KEY_UX = "ux"
    private const val KEY_APP_VERSION = "app_version"

    data class Score(
        val total: Int,
        val cpu: Int? = null,
        val gpu: Int? = null,
        val mem: Int? = null,
        val ux: Int? = null,
        val antutuAppVersion: String? = null,
    )

    fun read(context: Context): Score? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_TOTAL)) return null
        val total = p.getInt(KEY_TOTAL, 0)
        if (total < MIN_TOTAL_SCORE) return null
        return Score(
            total = total,
            cpu = p.getInt(KEY_CPU, 0).takeIf { it > 0 },
            gpu = p.getInt(KEY_GPU, 0).takeIf { it > 0 },
            mem = p.getInt(KEY_MEM, 0).takeIf { it > 0 },
            ux = p.getInt(KEY_UX, 0).takeIf { it > 0 },
            antutuAppVersion = p.getString(KEY_APP_VERSION, null)?.takeIf { it.isNotBlank() },
        )
    }

    fun save(context: Context, score: Score?) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (score == null || score.total < MIN_TOTAL_SCORE) {
            p.clear()
        } else {
            p.putInt(KEY_TOTAL, score.total)
            score.cpu?.let { p.putInt(KEY_CPU, it) } ?: p.remove(KEY_CPU)
            score.gpu?.let { p.putInt(KEY_GPU, it) } ?: p.remove(KEY_GPU)
            score.mem?.let { p.putInt(KEY_MEM, it) } ?: p.remove(KEY_MEM)
            score.ux?.let { p.putInt(KEY_UX, it) } ?: p.remove(KEY_UX)
            score.antutuAppVersion?.let { p.putString(KEY_APP_VERSION, it) } ?: p.remove(KEY_APP_VERSION)
        }
        p.apply()
    }
}
