package dev.pointandshoot

import android.view.Surface

/**
 * MediaCodec HFR uses [android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession].
 *
 * Shipped record path: **encoder-only** HS on the record camera + [HfrRecordMonitorSupport] finder
 * (~30 fps YUV on a second rear camera). Dual-surface interleaved HS did not feed the encoder on
 * CPH2655-class devices.
 */
object HfrInterleavedPreviewSupport {
    const val TAG = "PNS.HfrInterleaved"

    fun wantsInterleavedSession(
        desiredFps: Int,
        wantsMediaCodecPath: Boolean,
        dualVideoActive: Boolean,
    ): Boolean = desiredFps >= 120 && wantsMediaCodecPath && !dualVideoActive

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
