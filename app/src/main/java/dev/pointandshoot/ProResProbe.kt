package dev.pointandshoot

import android.media.MediaCodecList
import android.util.Log

/**
 * M19.4 — ProRes probe-only row (no hardware encode on Android fleet).
 */
object ProResProbe {
    private const val TAG = "PNS.ProRes"

    data class ProbeResult(
        val advertised: Boolean,
        val encoderNames: List<String>,
        val detail: String,
    )

    fun probeSync(): ProbeResult =
        runCatching { probeSyncInternal() }.getOrElse { e ->
            ProbeResult(
                advertised = false,
                encoderNames = emptyList(),
                detail = "probe failed: ${e.message ?: e.javaClass.simpleName}",
            )
        }

    private fun probeSyncInternal(): ProbeResult {
        val names =
            runCatching {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                    .filter { it.isNonAliasEncoder() }
                    .filter { info ->
                        info.supportedTypes.any { mime ->
                            mime.contains("prores", ignoreCase = true) ||
                                mime.contains("apple", ignoreCase = true)
                        }
                    }
                    .map { it.name }
            }.getOrDefault(emptyList())
        val advertised = names.isNotEmpty()
        val detail =
            if (advertised) {
                "encoders=${names.joinToString()}"
            } else {
                "no ProRes encoder in MediaCodecList — probe-only catalog row"
            }
        runCatching {
            Log.i(TAG, "proResProbe advertised=$advertised $detail")
        }
        return ProbeResult(advertised, names, detail)
    }
}
