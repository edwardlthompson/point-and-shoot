package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structural validation for fleet matrix JSON — mirrors host
 * `scripts/fleet_matrix_schema_validate.py` (Milestone **16.12** / H.CRI-5).
 */
object FleetDeviceMatrixSchemaValidator {
    sealed class Result {
        data object Ok : Result()

        data class Fail(val message: String) : Result()
    }

    fun validate(root: JSONObject): Result {
        val message =
            checkSchemaVersion(root)
                ?: checkRootKeys(root)
                ?: checkScanMeta(root)
                ?: checkDevice(root)
                ?: checkCameras(root)
        return if (message == null) Result.Ok else Result.Fail(message)
    }

    private fun checkSchemaVersion(root: JSONObject): String? {
        val schema = root.optInt(FleetDeviceMatrix.KEY_SCHEMA_VERSION, -1)
        return if (schema in FleetDeviceMatrix.SCHEMA_VERSION_MIN..FleetDeviceMatrix.SCHEMA_VERSION) {
            null
        } else {
            "schemaVersion must be 1 or 2, got $schema"
        }
    }

    private fun checkRootKeys(root: JSONObject): String? {
        for (key in REQUIRED_ROOT) {
            if (!root.has(key)) return "missing root key $key"
        }
        return null
    }

    private fun checkScanMeta(root: JSONObject): String? {
        val meta = root.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META) ?: return "scanMeta must be object"
        for (key in REQUIRED_SCAN_META) {
            if (!meta.has(key)) return "scanMeta missing $key"
        }
        val tier = meta.optString("scanTier")
        return if (tier in VALID_SCAN_TIERS) null else "scanMeta.scanTier invalid: $tier"
    }

    private fun checkDevice(root: JSONObject): String? {
        val device = root.optJSONObject(FleetDeviceMatrix.KEY_DEVICE) ?: return "device must be object"
        for (key in REQUIRED_DEVICE) {
            if (!device.has(key)) return "device missing $key"
        }
        return null
    }

    private fun checkCameras(root: JSONObject): String? {
        val meta = root.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META) ?: return null
        val tier = meta.optString("scanTier")
        val cameras = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return "cameras must be array"
        if (cameras.length() == 0) return "cameras must be non-empty"
        val ids = mutableListOf<String>()
        for (i in 0 until cameras.length()) {
            val cam = cameras.optJSONObject(i) ?: return "cameras[$i] must be object"
            val cid = cam.optString("cameraId", "")
            if (cid.isEmpty()) return "cameras[$i] missing cameraId"
            ids.add(cid)
            if (!cam.has("hfrMaxFpsAt1080") && !cam.has("featureGates")) {
                return "cameras[$i] missing shallow or structured fields"
            }
        }
        if (tier == FleetDeviceMatrix.ScanTier.FULL.json) {
            val sorted = ids.sorted()
            if (ids != sorted) {
                return "full tier cameras must be sorted by cameraId (got $ids, want $sorted)"
            }
        }
        return null
    }

    private val REQUIRED_ROOT =
        arrayOf(
            FleetDeviceMatrix.KEY_SCHEMA_VERSION,
            FleetDeviceMatrix.KEY_SCAN_META,
            FleetDeviceMatrix.KEY_DEVICE,
            FleetDeviceMatrix.KEY_CAMERAS,
            FleetDeviceMatrix.KEY_PRODUCT,
            FleetDeviceMatrix.KEY_APPENDIX,
        )

    private val REQUIRED_SCAN_META =
        arrayOf(
            "scanTier",
            "appVersionCode",
            "sdkInt",
            "fingerprintSha256Prefix",
        )

    private val REQUIRED_DEVICE = arrayOf("manufacturer", "model", "device")

    private val VALID_SCAN_TIERS =
        setOf(
            FleetDeviceMatrix.ScanTier.QUICK.json,
            FleetDeviceMatrix.ScanTier.FULL.json,
        )
}
