package dev.pointandshoot.fleet

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dev.pointandshoot.FleetCameraStartupScan
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Detects when Camera2 on device proves >12 MP per rear sensor without manifest/GSMArena fiction.
 * Inverse of [ResolutionBetrayal]: celebrates aftermarket access to full sensor resolution.
 */
object Camera2FullMpBreakthrough {
    const val MP_THRESHOLD = 13.0
    internal const val MP_ROUND_FACTOR = 10.0
    private const val PIXELS_PER_MP = 1_000_000.0

    enum class EvidenceTier(val wire: String) {
        DEFAULT("default"),
        MAXRES_MAP("maxres_map"),
        CAPTURE("capture"),
    }

    data class CameraEvidence(
        val cameraId: String,
        val provenMp: Double,
        val tier: EvidenceTier,
    )

    fun evaluateFromStillEntries(
        entries: List<FleetCameraStartupScan.StillResolutionAdvertisedEntry>,
        cm: CameraManager,
    ): List<CameraEvidence> =
        entries.mapNotNull { entry ->
            evidenceFromStillEntry(entry, cm)
        }

    private fun evidenceFromStillEntry(
        entry: FleetCameraStartupScan.StillResolutionAdvertisedEntry,
        cm: CameraManager,
    ): CameraEvidence? {
        if (!isRearCamera(cm, entry.cameraId)) return null
        val defaultMp = mp(entry.defaultJpeg).coerceAtLeast(mp(entry.defaultRawSensor))
        if (defaultMp >= MP_THRESHOLD) {
            return CameraEvidence(entry.cameraId, defaultMp, EvidenceTier.DEFAULT)
        }
        val maxResMp =
            listOf(
                mp(entry.maxResMapJpeg),
                mp(entry.maxResMapRawSensor),
            ).maxOrNull() ?: 0.0
        return if (entry.hasLargerThanDefault && maxResMp >= MP_THRESHOLD) {
            CameraEvidence(entry.cameraId, maxResMp, EvidenceTier.MAXRES_MAP)
        } else {
            null
        }
    }

    fun evaluateFromMatrix(matrix: JSONObject): List<CameraEvidence> {
        val product = matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT) ?: return emptyList()
        val arr = product.optJSONArray("stillResolutionAdvertised") ?: return emptyList()
        val out = mutableListOf<CameraEvidence>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            val cameraId = o?.optString("cameraId", "").orEmpty()
            if (o != null && cameraId.isNotBlank()) {
                evidenceFromStillJson(o, cameraId)?.let { out.add(it) }
            }
        }
        return out
    }

    private fun evidenceFromStillJson(o: JSONObject, cameraId: String): CameraEvidence? {
        val defaultMp = defaultMpFromJson(o)
        if (defaultMp >= MP_THRESHOLD) {
            return CameraEvidence(cameraId, defaultMp, EvidenceTier.DEFAULT)
        }
        val hasLarger = o.optBoolean("hasLargerThanDefault", false)
        val maxResMp =
            listOf(
                mpFromSizeJson(o.optJSONObject("maxResMapJpeg")),
                mpFromSizeJson(o.optJSONObject("maxResMapRawSensor")),
            ).maxOrNull() ?: 0.0
        return if (hasLarger && maxResMp >= MP_THRESHOLD) {
            CameraEvidence(cameraId, maxResMp, EvidenceTier.MAXRES_MAP)
        } else {
            null
        }
    }

    fun toSummaryJson(
        evidences: List<CameraEvidence>,
        captureEvidences: List<CameraEvidence> = emptyList(),
    ): JSONObject {
        val merged = mergeCapture(evidences, captureEvidences)
        val proven = merged.isNotEmpty()
        val maxMp = merged.maxOfOrNull { it.provenMp } ?: 0.0
        val tier =
            merged.maxByOrNull { tierRank(it.tier) }?.tier?.wire
        return JSONObject().apply {
            put("proven", proven)
            put("cameraCount", merged.size)
            put("maxMpPerSensor", if (maxMp > 0.0) (maxMp * MP_ROUND_FACTOR).roundToInt() / MP_ROUND_FACTOR else JSONObject.NULL)
            put("evidenceTier", tier ?: JSONObject.NULL)
            put(
                "cameras",
                JSONArray().apply {
                    merged.forEach { e ->
                        put(
                            JSONObject().apply {
                                put("cameraId", e.cameraId)
                                put("provenMp", (e.provenMp * MP_ROUND_FACTOR).roundToInt() / MP_ROUND_FACTOR)
                                put("evidenceTier", e.tier.wire)
                            },
                        )
                    }
                },
            )
        }
    }

    internal fun mergeCapture(
        hal: List<CameraEvidence>,
        capture: List<CameraEvidence>,
    ): List<CameraEvidence> {
        if (capture.isEmpty()) return hal
        val byId = hal.associateBy { it.cameraId }.toMutableMap()
        capture.forEach { c ->
            val existing = byId[c.cameraId]
            if (existing == null || tierRank(c.tier) > tierRank(existing.tier) || c.provenMp > existing.provenMp) {
                byId[c.cameraId] = c
            }
        }
        return byId.values.sortedBy { it.cameraId }
    }

    private fun tierRank(tier: EvidenceTier): Int = tier.ordinal + 1

    private fun isRearCamera(cm: CameraManager, cameraId: String): Boolean {
        val facing =
            runCatching {
                cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING)
            }.getOrNull()
        return facing == CameraCharacteristics.LENS_FACING_BACK
    }

    private fun defaultMpFromJson(o: JSONObject): Double =
        listOf(
            mpFromSizeJson(o.optJSONObject("defaultJpeg")),
            mpFromSizeJson(o.optJSONObject("defaultRawSensor")),
        ).maxOrNull()?.coerceAtLeast(0.0) ?: 0.0

    private fun mpFromSizeJson(size: JSONObject?): Double {
        if (size == null) return 0.0
        val mp = size.optDouble("mp", Double.NaN)
        if (mp.isFinite() && mp > 0.0) return mp
        val w = size.optInt("width", 0)
        val h = size.optInt("height", 0)
        if (w <= 0 || h <= 0) return 0.0
        return (w.toLong() * h.toLong()) / PIXELS_PER_MP
    }

    private fun mp(size: android.util.Size?): Double {
        if (size == null || size.width <= 0 || size.height <= 0) return 0.0
        return (size.width.toLong() * size.height.toLong()) / PIXELS_PER_MP
    }
}
