package dev.pointandshoot

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.time.Instant
import org.json.JSONObject

/**
 * Persists [DeviceCameraCapabilityCache] JSON from the last successful probe hub shallow scan
 * (Milestone **10.1** persistence row). Uses app-private [SharedPreferences] (no new Gradle deps).
 */
object ShallowCapabilityCacheStore {
    private const val PREFS_NAME = "pns_shallow_capability_cache"

    const val KEY_JSON = "cache_root_json"
    const val KEY_RESCAN_SEQ = "rescan_seq"

    private const val KEY_FP = "saved_fingerprint_prefix"
    private const val KEY_VC = "saved_app_version_code"
    private const val KEY_TIME_MS = "saved_at_epoch_ms"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun currentVersionCode(context: Context): Long {
        val pm = context.packageManager
        val pInfo =
            runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }.getOrNull() ?: return -1L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
    }

    fun saveAfterProbe(context: Context, root: JSONObject) {
        prefs(context)
            .edit()
            .putString(KEY_JSON, root.toString())
            .putString(KEY_FP, DeviceCameraCapabilityCache.fingerprintSha256Prefix(Build.FINGERPRINT))
            .putLong(KEY_VC, currentVersionCode(context))
            .putLong(KEY_TIME_MS, System.currentTimeMillis())
            .apply()
    }

    /**
     * Returns the last cached shallow JSON if [schemaVersion], build fingerprint prefix, and
     * [appVersionCode] still match this install.
     */
    fun loadValidCachedRoot(context: Context): JSONObject? {
        val p = prefs(context)
        val raw = p.getString(KEY_JSON, null) ?: return null
        val storedFp = p.getString(KEY_FP, null) ?: return null
        val liveFp = DeviceCameraCapabilityCache.fingerprintSha256Prefix(Build.FINGERPRINT)
        if (storedFp != liveFp) return null
        val storedVc = p.getLong(KEY_VC, -1L)
        if (storedVc != currentVersionCode(context)) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optInt("schemaVersion", -1) != DeviceCameraCapabilityCache.SCHEMA_VERSION) {
            return null
        }
        return root
    }

    fun lastScanEpochMs(context: Context): Long = prefs(context).getLong(KEY_TIME_MS, 0L)

    fun requestShallowScanRescan(context: Context) {
        val p = prefs(context)
        val next = p.getLong(KEY_RESCAN_SEQ, 0L) + 1L
        p.edit().putLong(KEY_RESCAN_SEQ, next).apply()
    }

    fun readRescanSeq(context: Context): Long = prefs(context).getLong(KEY_RESCAN_SEQ, 0L)

    fun lastScanSummaryLine(context: Context): String {
        val age = lastScanEpochMs(context)
        val root = loadValidCachedRoot(context)
        val cams = root?.optJSONArray("cameras")?.length() ?: 0
        return when {
            age <= 0L -> "Cached shallow scan: none yet (open Diagnostics hub once)."
            root == null -> "Cached shallow scan: stale or incompatible — use Rescan after visiting Diagnostics hub."
            else ->
                "Cached shallow scan: cameras=$cams saved=${Instant.ofEpochMilli(age)} " +
                    "(fingerprint+version match)"
        }
    }
}
