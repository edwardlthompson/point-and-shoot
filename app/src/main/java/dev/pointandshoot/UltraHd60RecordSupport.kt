package dev.pointandshoot

import android.graphics.SurfaceTexture
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.util.Log
import android.util.Range
import android.util.Size

/**
 * True **4K @ 60 fps** in-app video on Qualcomm fleet devices.
 *
 * REGULAR preview at 3840×2160 caps AE near **30 fps** ([CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES]
 * omits 60 on CPH2583-class wide cameras) even when the encoder advertises **3840×2160@60**.
 *
 * Shipped path on fleet (CPH2583-class):
 * - **Interleaved** REGULAR session: max **60 fps** SurfaceTexture preview + **MediaRecorder** @ 4K
 *   on the **same** record camera (4K ST alone caps ~30 fps).
 * - Session parameters: [CONTROL_AE_TARGET_FPS_RANGE] **60–60** + optional **dynamicFPSConfig**.
 * - Encoder-only + monitor finder is **HFR MC only** — REGULAR MR-only sessions receive no frames.
 */
object UltraHd60RecordSupport {
    const val TAG = "PNS.UltraHd60"

    const val UHD_WIDTH = 3840
    const val UHD_HEIGHT = 2160
    const val TARGET_FPS = 60

    /** Minimum measured unique fps to treat delivery as honest 60 (USB gates). */
    const val MIN_MEASURED_UNIQUE_FPS = 52

    fun isUltraHdSize(width: Int, height: Int): Boolean =
        width >= UHD_WIDTH && height >= UHD_HEIGHT

    fun maxFpsFromMinFrameDurationNs(minFrameDurationNs: Long?): Double? {
        if (minFrameDurationNs == null || minFrameDurationNs <= 0L) return null
        return 1_000_000_000.0 / minFrameDurationNs.toDouble()
    }

    fun maxOutputFps(
        map: StreamConfigurationMap?,
        outputClass: Class<*>,
        width: Int,
        height: Int,
    ): Double? {
        if (map == null || width <= 0 || height <= 0) return null
        val size = Size(width, height)
        val durationNs =
            runCatching { map.getOutputMinFrameDuration(outputClass, size) }.getOrNull()
                ?: return null
        return maxFpsFromMinFrameDurationNs(durationNs)
    }

    /**
     * Catalog / picker: offer 3840×2160 @ 60 when MR output exists and the H.264 encoder has an
     * exact performance point (fleet probe), independent of preview AE ranges.
     */
    fun isCatalogTierSupported(
        map: StreamConfigurationMap?,
        width: Int,
        height: Int,
        fps: Int,
    ): Boolean {
        if (!isUltraHdSize(width, height) || fps != TARGET_FPS) return true
        if (!InAppVideoRecordingSupport.supportsMediaRecorderOutputSize(map, width, height)) {
            return false
        }
        return MediaCodecCapabilityProbe.hasExactH264PerformancePoint(width, height, fps)
    }

    /**
     * True when catalog + HAL say **3840×2160 @ 60** is valid but **4K SurfaceTexture** preview
     * cannot sustain 60 fps (fleet wide cam AE omits 60 in preview ranges).
     */
    fun needsUltraHd60Delivery(
        recordSize: Size,
        desiredFps: Int,
        map: StreamConfigurationMap?,
        inAppVideoRecordingArmed: Boolean,
        recorderPresent: Boolean,
    ): Boolean {
        if (!inAppVideoRecordingArmed || !recorderPresent) return false
        if (desiredFps != TARGET_FPS) return false
        if (!isUltraHdSize(recordSize.width, recordSize.height)) return false
        if (!isCatalogTierSupported(map, recordSize.width, recordSize.height, TARGET_FPS)) {
            return false
        }
        val previewMax =
            maxOutputFps(map, SurfaceTexture::class.java, recordSize.width, recordSize.height)
                ?: return true
        return previewMax < MIN_MEASURED_UNIQUE_FPS
    }

    /**
     * Preview size for **interleaved** UHD60: max SurfaceTexture tier whose HAL min-duration
     * supports ≥ [MIN_MEASURED_UNIQUE_FPS] while MR records at 4K on the same camera.
     */
    fun pickInterleavedPreviewSize(map: StreamConfigurationMap?): Size? {
        if (map == null) return null
        val sizes =
            runCatching { map.getOutputSizes(SurfaceTexture::class.java)?.toList() }
                .getOrNull()
                .orEmpty()
        return sizes
            .filter { s ->
                (maxOutputFps(map, SurfaceTexture::class.java, s.width, s.height) ?: 0.0) >=
                    MIN_MEASURED_UNIQUE_FPS
            }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.firstOrNull { it.width == 1920 && it.height == 1080 }
    }

    /**
     * Encoder-only + monitor finder (HFR MC path only). UHD60 **H.264** uses interleaved
     * preview + MR on the record camera — REGULAR encoder-only MR sessions do not receive
     * frames on CPH2583-class HALs.
     */
    fun wantsEncoderOnlyRecord(
        recordSize: Size,
        desiredFps: Int,
        map: StreamConfigurationMap?,
        inAppVideoRecordingArmed: Boolean,
        recorderPresent: Boolean,
    ): Boolean = false

    fun recordFpsRange(desiredFps: Int): Range<Int> =
        Range(desiredFps.coerceAtLeast(15), desiredFps.coerceAtMost(TARGET_FPS))

    fun logSessionDecision(
        recordSize: Size,
        desiredFps: Int,
        previewMaxFps: Double?,
        encoderOnly: Boolean,
    ) {
        Log.i(
            TAG,
            "uhd60 record=${recordSize.width}x${recordSize.height} targetFps=$desiredFps " +
                "previewMaxFps=${previewMaxFps?.let { "%.1f".format(it) } ?: "?"} " +
                "encoderOnly=$encoderOnly",
        )
    }
}
