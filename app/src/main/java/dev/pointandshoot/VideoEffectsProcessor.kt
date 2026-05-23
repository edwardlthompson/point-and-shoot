package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Log
import android.util.Range

/**
 * Sprint **VF.2** — preview / in-app video stabilization facade (OIS + EIS hybrid).
 *
 * Delegates to [PreviewStabilization] for Camera2 keys and logs capability + applied state to
 * `PNS.VideoEffects` for ADB gates (`pns_video_stabilization_test.ps1`).
 */
object VideoEffectsProcessor {
    private const val TAG = "PNS.VideoEffects"

    @Volatile
    private var lastDiagElapsedMs = 0L

    /**
     * Apply OIS + preview EIS on repeating preview / video record requests.
     */
    fun applyToVideoPreviewRequest(
        builder: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        settings: HudSettings,
        previewFpsRange: Range<Int>?,
        manualSensor: Boolean,
        logDiag: Boolean = true,
    ) {
        PreviewStabilization.applyToRequest(
            builder = builder,
            chars = chars,
            settings = settings,
            previewFpsRange = previewFpsRange,
            manualSensor = manualSensor,
            isStillCapture = false,
        )
        if (logDiag) {
            maybeLogStabilizationDiag(chars, settings, previewFpsRange)
        }
    }

    internal fun stabilizationDiag(
        chars: CameraCharacteristics,
        settings: HudSettings,
        previewFpsRange: Range<Int>?,
    ): String {
        val oisAvail =
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
        val eisAvail =
            chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
        val hfrPreview =
            previewFpsRange != null && previewFpsRange.upper >= PreviewStabilization.HFR_PREVIEW_EIS_DISABLE_FPS
        val oisOn =
            settings.enableLensOpticalStabilization &&
                PreviewStabilization.pickOpticalStabilizationMode(oisAvail) != null
        val eisOn =
            settings.enableVideoStabilizationPreview &&
                !hfrPreview &&
                PreviewStabilization.pickVideoStabilizationMode(eisAvail) != null
        return buildString {
            append("oisOn=$oisOn eisOn=$eisOn hfrPreview=$hfrPreview ")
            append("oisAdvertised=${oisAvail.isNotEmpty()} eisAdvertised=${eisAvail.isNotEmpty()}")
        }
    }

    private fun maybeLogStabilizationDiag(
        chars: CameraCharacteristics,
        settings: HudSettings,
        previewFpsRange: Range<Int>?,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDiagElapsedMs < 3_000L) return
        lastDiagElapsedMs = now
        Log.i(TAG, "videoStabilization ${stabilizationDiag(chars, settings, previewFpsRange)}")
    }
}
