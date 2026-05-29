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
            maybeLogStabilizationDiag(chars, settings, previewFpsRange, manualSensor)
        }
    }

    internal fun stabilizationDiag(
        chars: CameraCharacteristics,
        settings: HudSettings,
        previewFpsRange: Range<Int>?,
        manualSensor: Boolean = false,
    ): String {
        val state = parseStabilizationState(chars, settings, previewFpsRange, manualSensor)
        return buildString {
            append("oisOn=${state.oisOn} eisOn=${state.eisOn} hfrPreview=${state.hfrPreview} ")
            append("oisAdvertised=${state.oisAdvertised} eisAdvertised=${state.eisAdvertised}")
        }
    }

    /** Sprint **15.32** — readout STAB chip value; null hides the chip. */
    fun readoutLabel(
        chars: CameraCharacteristics,
        settings: HudSettings,
        previewFpsRange: Range<Int>?,
        manualSensor: Boolean = false,
    ): String? = readoutLabel(parseStabilizationState(chars, settings, previewFpsRange, manualSensor))

    fun readoutLabel(state: StabilizationState): String? =
        when {
            state.oisOn && state.eisOn -> "OIS+EIS"
            state.oisOn -> "OIS"
            state.eisOn -> "EIS"
            state.oisAdvertised || state.eisAdvertised -> "Off"
            else -> null
        }

    fun parseStabilizationState(
        chars: CameraCharacteristics,
        settings: HudSettings,
        previewFpsRange: Range<Int>?,
        manualSensor: Boolean = false,
    ): StabilizationState {
        val keys = chars.availableCaptureRequestKeys ?: emptyList()
        val oisAvail =
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
        val eisAvail =
            chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
        val oisKey = keys.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE)
        val eisKey = keys.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE)
        val hfrPreview =
            previewFpsRange != null && previewFpsRange.upper >= PreviewStabilization.HFR_PREVIEW_EIS_DISABLE_FPS
        val oisOn =
            settings.enableLensOpticalStabilization &&
                oisKey &&
                PreviewStabilization.resolveOpticalStabilizationMode(oisAvail, oisKey) != null
        val eisOn =
            settings.enableVideoStabilizationPreview &&
                !manualSensor &&
                !hfrPreview &&
                eisKey &&
                PreviewStabilization.pickVideoStabilizationMode(eisAvail) != null
        return StabilizationState(
            oisOn = oisOn,
            eisOn = eisOn,
            oisAdvertised = oisKey,
            eisAdvertised = eisKey,
            hfrPreview = hfrPreview,
        )
    }

    data class StabilizationState(
        val oisOn: Boolean,
        val eisOn: Boolean,
        val oisAdvertised: Boolean,
        val eisAdvertised: Boolean,
        val hfrPreview: Boolean = false,
    )

    private fun maybeLogStabilizationDiag(
        chars: CameraCharacteristics,
        settings: HudSettings,
        previewFpsRange: Range<Int>?,
        manualSensor: Boolean,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDiagElapsedMs < 3_000L) return
        lastDiagElapsedMs = now
        Log.i(TAG, "videoStabilization ${stabilizationDiag(chars, settings, previewFpsRange, manualSensor)}")
    }
}
