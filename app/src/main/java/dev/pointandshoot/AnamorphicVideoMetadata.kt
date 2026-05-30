package dev.pointandshoot

import android.util.Log
import org.json.JSONObject

/**
 * M19.4 — anamorphic sample-aspect metadata for encode/mux (desqueeze in viewers).
 */
object AnamorphicVideoMetadata {
    private const val TAG = "PNS.Anamorphic"

    /** Common 2× anamorphic squeeze (1.33:1 → 2.66:1). */
    const val DEFAULT_SQUEEZE = 2.0

    data class SampleAspect(
        val horizontal: Int,
        val vertical: Int,
        val squeezeFactor: Double,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("hSpacing", horizontal)
                put("vSpacing", vertical)
                put("squeezeFactor", squeezeFactor)
            }
    }

    fun fromSqueezeFactor(factor: Double): SampleAspect {
        val f = factor.coerceIn(1.0, 3.0)
        val h = (1000 * f).toInt().coerceAtLeast(1000)
        return SampleAspect(horizontal = h, vertical = 1000, squeezeFactor = f)
    }

    fun logApply(sar: SampleAspect) {
        Log.i(TAG, "anamorphicSar h=${sar.horizontal} v=${sar.vertical} squeeze=${sar.squeezeFactor}")
    }

    /** Embed pasp box payload when mux supports it (see [IsobmffSampleAspect]). */
    fun paspPayload(sar: SampleAspect): ByteArray? =
        runCatching {
            IsobmffSampleAspect.encodePasp(
                IsobmffSampleAspect.PaspPayload(sar.horizontal, sar.vertical),
            )
        }.getOrNull()
}
