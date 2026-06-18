package dev.pointandshoot.fleet

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import dev.pointandshoot.DeviceCameraCapabilityCache
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONObject

/**
 * Persists [FleetDeviceMatrix] JSON under app-private storage (Milestone **16.0**).
 */
object FleetDeviceMatrixStore {
    private const val TAG = "PNS.FleetMatrix"
    const val MATRIX_FILE_NAME = "fleet_device_matrix.json"
    const val SUMMARY_FILE_NAME = "fleet_device_capability_summary.md"
    const val HISTORY_DIR_NAME = "fleet_device_matrix_history"

    fun matrixFile(context: Context): File =
        File(context.applicationContext.filesDir, MATRIX_FILE_NAME)

    fun historyDir(context: Context): File =
        File(context.applicationContext.filesDir, HISTORY_DIR_NAME).also { it.mkdirs() }

    fun currentVersionCode(context: Context): Long {
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

    fun liveFingerprintPrefix(): String =
        DeviceCameraCapabilityCache.fingerprintSha256Prefix(Build.FINGERPRINT)

    fun liveCameraIdRosterSha256Prefix(context: Context): String? {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
        val joined = runCatching { cm.cameraIdList.sorted().joinToString(",") }.getOrNull() ?: return null
        if (joined.isBlank()) return null
        return sha256Prefix(joined)
    }

    /**
     * Returns cached matrix when [schemaVersion], fingerprint prefix, and [appVersionCode] match.
     */
    fun loadValid(context: Context): JSONObject? {
        val file = matrixFile(context)
        if (!file.exists()) return null
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        if (!FleetDeviceMatrix.isValidRoot(root)) return null
        if (FleetDeviceMatrixSchemaValidator.validate(root) !is FleetDeviceMatrixSchemaValidator.Result.Ok) return null
        val meta = root.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META) ?: return null
        if (meta.optString("fingerprintSha256Prefix") != liveFingerprintPrefix()) return null
        if (meta.optLong("appVersionCode", -1L) != currentVersionCode(context)) return null
        val storedRoster = meta.optString("cameraIdRosterSha256Prefix").takeIf { it.isNotBlank() }
        if (storedRoster != null && storedRoster != liveCameraIdRosterSha256Prefix(context)) return null
        val storedPolicy = meta.optString("fleetPolicyId").takeIf { it.isNotBlank() }
        val livePolicy = FleetDevicePolicySelector.active(context.applicationContext).policyId ?: "generic"
        if (storedPolicy != null && storedPolicy != livePolicy) return null
        return root
    }


    fun summaryFile(context: Context): File =
        File(context.applicationContext.filesDir, SUMMARY_FILE_NAME)

    /** Persist matrix JSON + human-readable summary markdown (Milestone **17.1**). */
    fun saveWithArtifacts(context: Context, root: JSONObject, rotatePreviousToHistory: Boolean = false) {
        val withCatalog =
            if (root.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG)) {
                root
            } else {
                CameraCapabilityCatalogBuilder.attachTo(root)
            }
        save(context, withCatalog, rotatePreviousToHistory)
        writeTextAtomically(summaryFile(context), FleetCapabilitySummaryMarkdown.render(withCatalog))
    }

    fun save(context: Context, root: JSONObject, rotatePreviousToHistory: Boolean = false) {
        when (val validation = FleetDeviceMatrixSchemaValidator.validate(root)) {
            is FleetDeviceMatrixSchemaValidator.Result.Fail -> {
                Log.e(TAG, "matrix save rejected: ${validation.message}")
                return
            }
            is FleetDeviceMatrixSchemaValidator.Result.Ok -> Unit
        }
        val app = context.applicationContext
        val file = matrixFile(app)
        if (rotatePreviousToHistory && file.exists()) {
            rotateToHistory(app, file)
        }
        writeTextAtomically(file, root.toString(2))
    }

    fun rotateToHistory(context: Context, source: File = matrixFile(context)) {
        if (!source.exists()) return
        val stamp = Instant.now().toString().replace(":", "-")
        val dest = File(historyDir(context), "fleet_device_matrix_$stamp.json")
        source.copyTo(dest, overwrite = true)
    }

    fun lastScanEpochMs(context: Context): Long =
        loadValid(context)
            ?.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)
            ?.optLong("generatedAtEpochMs", 0L)
            ?: 0L

    fun summaryLine(context: Context): String {
        val root = loadValid(context)
        val tier = root?.let { FleetDeviceMatrix.parseScanTier(it)?.json } ?: "none"
        val cams = root?.let { FleetDeviceMatrix.cameraCount(it) } ?: 0
        val age = lastScanEpochMs(context)
        return when {
            root == null ->
                "Fleet matrix: none or stale — Rescan from Diagnostics hub (Device capability matrix, 16.2)."
            else ->
                "Fleet matrix: tier=$tier cameras=$cams saved=${Instant.ofEpochMilli(age)}"
        }
    }

    fun loadLatestHistory(context: Context): JSONObject? {
        val dir = historyDir(context)
        val latest =
            dir.listFiles { f -> f.name.startsWith("fleet_device_matrix_") && f.name.endsWith(".json") }
                ?.maxByOrNull { it.lastModified() }
                ?: return null
        return runCatching { JSONObject(latest.readText()) }.getOrNull()
    }

    /** Test hook: whether [stored] would load on this install. */
    internal fun isStillValid(
        stored: JSONObject,
        liveFingerprintPrefix: String,
        liveVersionCode: Long,
        liveCameraIdRosterSha256Prefix: String? = null,
        livePolicyId: String? = null,
    ): Boolean {
        if (!FleetDeviceMatrix.isValidRoot(stored)) return false
        val meta = stored.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META) ?: return false
        if (meta.optString("fingerprintSha256Prefix") != liveFingerprintPrefix) return false
        if (meta.optLong("appVersionCode", -1L) != liveVersionCode) return false
        val storedRoster = meta.optString("cameraIdRosterSha256Prefix").takeIf { it.isNotBlank() }
        if (storedRoster != null && liveCameraIdRosterSha256Prefix != null && storedRoster != liveCameraIdRosterSha256Prefix) {
            return false
        }
        val storedPolicy = meta.optString("fleetPolicyId").takeIf { it.isNotBlank() }
        if (storedPolicy != null && livePolicyId != null && storedPolicy != livePolicyId) return false
        return true
    }

    private fun writeTextAtomically(target: File, text: String) {
        val dir = target.parentFile ?: return
        dir.mkdirs()
        val temp = File(dir, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun sha256Prefix(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.take(6).joinToString("") { b -> "%02x".format(b) }
    }
}
