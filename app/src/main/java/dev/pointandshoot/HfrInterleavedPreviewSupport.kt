package dev.pointandshoot

import android.view.Surface

/**
 * MediaCodec HFR uses [android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession].
 *
 * Default for **4K @ ≥120** when the HAL lists **3840×2160** in [StreamConfigurationMap.highSpeedVideoSizes]:
 * **interleaved** preview + encoder on the record camera (matches MC encode size). Encoder-only HS +
 * [HfrRecordMonitorSupport] remains for sub-4K HS and when interleaved configure fails on legacy stacks.
 */
object HfrInterleavedPreviewSupport {
    const val TAG = "PNS.HfrInterleaved"

    fun wantsInterleavedSession(
        desiredFps: Int,
        wantsMediaCodecPath: Boolean,
        dualVideoActive: Boolean,
    ): Boolean = desiredFps >= 120 && wantsMediaCodecPath && !dualVideoActive

    /**
     * Skip encoder-only HS (single output + monitor camera) so session outputs are **[preview, encoder]**.
     */
    fun prefersInterleavedOverEncoderOnlyFor4KEncode(
        desiredFps: Int,
        wantsMediaCodecPath: Boolean,
        encodePrefWidth: Int,
        encodePrefHeight: Int,
        hsCaptureWidth: Int,
        hsCaptureHeight: Int,
        preferSub4kCapture: Boolean,
        forceInterleavedAfterConfigureFail: Boolean,
        encoderPriorityFallback: Boolean = false,
    ): Boolean {
        if (encoderPriorityFallback) return false
        if (forceInterleavedAfterConfigureFail) return true
        if (desiredFps < 120 || !wantsMediaCodecPath) return false
        if (preferSub4kCapture) return false
        // Encode pref drives MC WxH on Sony XQ-BE62 even when highSpeedVideoSizes omits 4K.
        if (encodePrefWidth >= 3840 && encodePrefHeight >= 2160) return true
        if (encodePrefWidth > 0 && encodePrefHeight > 0) return false
        return hsCaptureWidth >= 3840 && hsCaptureHeight >= 2160
    }

    /**
     * HS session outputs for interleaved mode: **[preview, encoder]** (preview first).
     * Never encoder-only — that starves the finder [Surface].
     */
    fun sessionOutputs(
        previewSurface: Surface?,
        encoderSurface: Surface?,
    ): List<Surface>? {
        val prev = previewSurface?.takeIf { it.isValid } ?: return null
        val enc = encoderSurface?.takeIf { it.isValid } ?: return null
        return listOf(prev, enc)
    }
}
