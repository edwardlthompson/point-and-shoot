package dev.pointandshoot.fleet

import dev.pointandshoot.MediaCodecCapabilityProbe
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cross-check matrix encoder slice vs catalog encoder inventory rows (Milestone **21.13d**).
 */
object FleetParityEncoderCrossCheck {
    fun build(matrix: JSONObject): JSONObject {
        val encoder = matrix.optJSONObject(FleetDeviceMatrix.KEY_ENCODER)
        val best = encoder?.optJSONArray("bestByCameraFps")
        val hasBest = best != null && best.length() > 0
        val catalogEncoders = CameraCapabilityCatalog.registry.filter { it.id.startsWith("encoder.") }
        val rows = JSONArray()
        for (row in catalogEncoders) {
            val slug = row.id.removePrefix("encoder.").replace('_', '.')
            val probeOk =
                when {
                    slug.contains("avc") -> true
                    slug.contains("hevc") -> hasBest
                    slug.contains("av1") -> MediaCodecCapabilityProbe.probeSyncSafe().supportsAv1
                    slug.contains("vp9") -> MediaCodecCapabilityProbe.probeSyncSafe().supportsVp9
                    else -> false
                }
            rows.put(
                JSONObject().apply {
                    put("catalogId", row.id)
                    put("encoderSlug", slug)
                    put("matrixHasBestByFps", hasBest)
                    put("probeOk", probeOk)
                    put("staleWarning", hasBest && !probeOk)
                },
            )
        }
        return JSONObject().apply {
            put("schema", "pns.parity_encoder_crosscheck.v1")
            put("encoderSource", encoder?.optString("source") ?: "missing")
            put("rows", rows)
        }
    }
}
