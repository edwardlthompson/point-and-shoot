package dev.pointandshoot.fleet

import org.json.JSONObject
import java.security.MessageDigest

/** Stable public device slug (matches host `Get-DeviceSlug` in pns_leaderboard_common.ps1). */
object LeaderboardDeviceSlug {
    fun deviceKey(matrix: JSONObject): String {
        val device = matrix.optJSONObject(FleetDeviceMatrix.KEY_DEVICE)
        val manufacturer = device?.optString("manufacturer").orEmpty()
        val model = device?.optString("model").orEmpty()
        val fp =
            matrix.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)?.optString("fingerprintSha256Prefix").orEmpty()
        return "$manufacturer|$model|$fp"
    }

    fun fromMatrix(matrix: JSONObject): String {
        val key = deviceKey(matrix)
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun publicDeviceUrl(slug: String, baseUrl: String): String? {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty() || slug.isBlank()) return null
        return "$base/#/device/$slug"
    }
}
