package dev.pointandshoot.fleet

import android.os.Build
import org.json.JSONObject

/**
 * Canonical fleet capability artifact (Milestone **16.0**).
 *
 * Structured slices are queryable by agents and host scripts; heavy dumps live under [KEY_APPENDIX].
 */
object FleetDeviceMatrix {
    const val SCHEMA_VERSION: Int = 2
    const val SCHEMA_VERSION_MIN: Int = 1
    const val SCAN_ORDERING_VERSION: Int = 1

    const val KEY_SCHEMA_VERSION = "schemaVersion"
    const val KEY_SCAN_META = "scanMeta"
    const val KEY_DEVICE = "device"
    const val KEY_CAMERAS = "cameras"
    const val KEY_PRODUCT = "product"
    const val KEY_CAMERA_X = "cameraX"
    const val KEY_RUNTIME_PROBES = "runtimeProbes"
    const val KEY_COMPLIANCE_ROLLUP = "complianceRollup"
    const val KEY_ENCODER = "encoder"
    const val KEY_CAPABILITY_CATALOG = "capabilityCatalog"
    const val KEY_CATALOG_VERSION = "catalogVersion"
    const val KEY_APPENDIX = "appendix"

    enum class ScanTier(val json: String) {
        QUICK("quick"),
        FULL("full"),
    }

    fun isValidRoot(root: JSONObject): Boolean {
        val schema = root.optInt(KEY_SCHEMA_VERSION, -1)
        return schema in SCHEMA_VERSION_MIN..SCHEMA_VERSION &&
            root.has(KEY_SCAN_META) &&
            root.has(KEY_DEVICE) &&
            root.optJSONArray(KEY_CAMERAS) != null
    }

    fun deviceBlock(manufacturer: String, model: String, device: String): JSONObject =
        JSONObject().apply {
            put("manufacturer", manufacturer)
            put("model", model)
            put("device", device)
        }

    fun scanMeta(
        scanTier: ScanTier,
        appVersionCode: Long,
        sdkInt: Int,
        securityPatch: String,
        fingerprintSha256Prefix: String,
        scanDurationMs: Long,
        scanOrderingVersion: Int = SCAN_ORDERING_VERSION,
        mediaPerformanceClass: Int? = null,
        firstApiLevel: Int? = null,
        vendorApiLevel: Int? = null,
    ): JSONObject =
        JSONObject().apply {
            put("generatedAtEpochMs", System.currentTimeMillis())
            put("scanTier", scanTier.json)
            put("appVersionCode", appVersionCode)
            put("sdkInt", sdkInt)
            put("securityPatch", securityPatch)
            put("fingerprintSha256Prefix", fingerprintSha256Prefix)
            put("scanDurationMs", scanDurationMs)
            put("scanOrderingVersion", scanOrderingVersion)
            put("mediaPerformanceClass", mediaPerformanceClass ?: JSONObject.NULL)
            put("firstApiLevel", firstApiLevel ?: JSONObject.NULL)
            put("vendorApiLevel", vendorApiLevel ?: JSONObject.NULL)
        }

    fun emptyAppendix(): JSONObject =
        JSONObject().apply {
            put("note", "quick tier — full stream map and key lists added in scan tier full (16.1)")
        }

    fun parseScanTier(root: JSONObject): ScanTier? =
        when (root.optJSONObject(KEY_SCAN_META)?.optString("scanTier")) {
            ScanTier.QUICK.json -> ScanTier.QUICK
            ScanTier.FULL.json -> ScanTier.FULL
            else -> null
        }

    fun fingerprintFromRoot(root: JSONObject): String? =
        root.optJSONObject(KEY_SCAN_META)?.optString("fingerprintSha256Prefix")?.takeIf { it.isNotEmpty() }

    fun appVersionCodeFromRoot(root: JSONObject): Long =
        root.optJSONObject(KEY_SCAN_META)?.optLong("appVersionCode", -1L) ?: -1L

    fun cameraCount(root: JSONObject): Int = root.optJSONArray(KEY_CAMERAS)?.length() ?: 0

    /** True when quick tier only, deep caps absent, or catalog not yet built (Milestone **17.3** banner). */
    fun needsFullRescan(root: JSONObject?): Boolean {
        if (root == null) return true
        if (parseScanTier(root) != ScanTier.FULL) return true
        val deep =
            root.optJSONObject(KEY_APPENDIX)?.optJSONObject("deepCaps")
                ?: return true
        val cams = deep.optJSONArray("cameras")
        if (cams == null || cams.length() == 0) return true
        val catalog = root.optJSONArray(KEY_CAPABILITY_CATALOG)
        return catalog == null || catalog.length() == 0
    }

    fun withCatalogIfMissing(root: JSONObject): JSONObject =
        if (root.has(KEY_CAPABILITY_CATALOG)) root else root

    fun defaultDeviceBlock(): JSONObject =
        deviceBlock(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
        )
}
