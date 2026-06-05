package dev.pointandshoot.fleet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runtime reads of [FleetDeviceMatrix] per-camera fields (Milestone **16.6**).
 *
 * Returns **null** when no valid on-device matrix exists — callers should fall back to live Camera2 probes.
 */
object FleetCapabilityGate {
    data class GateTriple(
        val advertised: Boolean,
        val sessionOk: Boolean,
        val appEnabled: Boolean,
    )

    fun loadMatrix(context: Context): JSONObject? = FleetDeviceMatrixStore.loadValid(context)

    fun findCamera(matrix: JSONObject, cameraId: String): JSONObject? {
        val arr = matrix.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return null
        for (i in 0 until arr.length()) {
            val cam = arr.getJSONObject(i)
            if (cam.optString("cameraId") == cameraId) return cam
        }
        return null
    }

    fun maxHfrAt1080(matrix: JSONObject, cameraId: String): Int? =
        findCamera(matrix, cameraId)?.optInt("hfrMaxFpsAt1080", 0)?.takeIf { it > 0 }

    fun maxHfrAt1080(context: Context, cameraId: String): Int? =
        loadMatrix(context)?.let { maxHfrAt1080(it, cameraId) }

    fun featureGate(matrix: JSONObject, cameraId: String, family: String): GateTriple? {
        val gate =
            findCamera(matrix, cameraId)?.optJSONObject("featureGates")?.optJSONObject(family)
                ?: matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                    ?.optJSONObject("concurrencyGates")
                    ?.optJSONObject(family)
                ?: return null
        return GateTriple(
            advertised = gate.optBoolean("advertised", false),
            sessionOk = gate.optBoolean("sessionOk", false),
            appEnabled = gate.optBoolean("appEnabled", false),
        )
    }

    fun featureGate(context: Context, cameraId: String, family: String): GateTriple? =
        loadMatrix(context)?.let { featureGate(it, cameraId, family) }

    fun isRawSessionOk(context: Context, cameraId: String): Boolean? =
        featureGate(context, cameraId, "raw")?.sessionOk

    fun isHfrSessionOk(context: Context, cameraId: String): Boolean? =
        featureGate(context, cameraId, "hfr")?.sessionOk

    fun isFourKRegularSessionOk(context: Context, cameraId: String): Boolean? =
        featureGate(context, cameraId, "fourKRegular")?.sessionOk

    fun isHfrAppEnabled(context: Context, cameraId: String): Boolean? =
        featureGate(context, cameraId, "hfr")?.appEnabled

    /** Matrix-backed HFR ceiling for FPS picker; null when matrix missing or camera unknown. */
    fun matrixHfrFpsCeiling(context: Context, cameraId: String): Int? =
        maxHfrAt1080(context, cameraId)

    fun policyId(matrix: JSONObject): String? =
        matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
            ?.optJSONObject("fleetProfiles")
            ?.optString("policyId")
            ?.takeIf { it.isNotEmpty() }

    fun cameraIds(matrix: JSONObject): List<String> {
        val arr = matrix.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return emptyList()
        return List(arr.length()) { i -> arr.getJSONObject(i).optString("cameraId") }
    }

    internal fun camerasArray(matrix: JSONObject): JSONArray? = matrix.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS)
}
