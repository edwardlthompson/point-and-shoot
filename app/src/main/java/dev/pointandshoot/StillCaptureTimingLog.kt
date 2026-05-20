package dev.pointandshoot

import android.os.SystemClock
import android.util.Log

/**
 * Sprint **13.8d** — shutter-to-save timing on [CaptureStillLog.TAG] for benchmark scripts.
 */
object StillCaptureTimingLog {
    private const val TAG = "PNS.CaptureStill"

    fun logDngSaved(
        stillMode: StillCaptureMode,
        requestedMode: StillCaptureMode,
        tRequestNs: Long,
        tRawAvailableNs: Long,
        tDngSavedNs: Long,
        label: String?,
    ) {
        val reqMs = deltaMs(tRequestNs, tRawAvailableNs)
        val saveMs = deltaMs(tRawAvailableNs, tDngSavedNs)
        val totalMs = deltaMs(tRequestNs, tDngSavedNs)
        Log.i(
            TAG,
            "still timing stillMode=$stillMode requestedMode=$requestedMode " +
                "label=${label ?: "-"} " +
                "t_request_to_raw_ms=$reqMs t_raw_to_dng_ms=$saveMs t_request_to_dng_ms=$totalMs",
        )
    }

    /** HDR still bracket — wall time from shutter to last DNG (all stops). */
    fun logHdrBracketSaved(
        frameCount: Int,
        tBeginNs: Long,
        label: String?,
    ) {
        val totalMs = deltaMs(tBeginNs, SystemClock.elapsedRealtimeNanos())
        Log.i(
            TAG,
            "still timing stillMode=HdrStill requestedMode=HdrStill " +
                "label=${label ?: "-"} " +
                "t_request_to_raw_ms=-1 t_raw_to_dng_ms=-1 t_request_to_dng_ms=$totalMs " +
                "hdr_frames=$frameCount",
        )
    }

    private fun deltaMs(startNs: Long, endNs: Long): Long =
        if (startNs > 0L && endNs >= startNs) {
            (endNs - startNs) / 1_000_000L
        } else {
            -1L
        }
}
