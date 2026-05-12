package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log

/**
 * User-facing flash / torch modes for preview + still [CaptureRequest] wiring.
 *
 * See `BUILD_PLAN.md` Sprint 9.12 / 4.4; HAL truth is always [CameraCharacteristics.FLASH_INFO_AVAILABLE]
 * and [CameraCharacteristics.getAvailableCaptureRequestKeys].
 */
enum class PreviewFlashMode {
    Off,
    Auto,
    On,
    Torch,
    ;

    fun cycle(): PreviewFlashMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStorageOrdinal(i: Int): PreviewFlashMode =
            entries.getOrNull(i.coerceIn(0, entries.lastIndex)) ?: Auto
    }
}

object PreviewFlashPolicy {
    private const val TAG = "PNS.FlashPolicy"

    fun flashHardwareAvailable(chars: CameraCharacteristics): Boolean =
        chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

    fun isBackCamera(chars: CameraCharacteristics): Boolean =
        chars.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK

    private fun hasFlashModeKey(chars: CameraCharacteristics): Boolean =
        chars.availableCaptureRequestKeys?.contains(CaptureRequest.FLASH_MODE) == true

    private fun safeFlashOff(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        if (!hasFlashModeKey(chars)) return
        runCatching { req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF) }
            .onFailure { Log.w(TAG, "FLASH_MODE off: ${it.message}") }
    }

    /**
     * AE mode for the generic "AE on" program (Auto / S / BKT preview paths) before torch keys.
     * Returns null when [CONTROL_AE_MODE_ON] is unavailable.
     */
    fun aeModeForAutoProgramWithFlashPref(
        aeModes: IntArray,
        flashMode: PreviewFlashMode,
    ): Int? {
        if (!aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON)) return null
        return when (flashMode) {
            PreviewFlashMode.Off -> CaptureRequest.CONTROL_AE_MODE_ON
            PreviewFlashMode.Auto ->
                when {
                    aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) ->
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    else -> CaptureRequest.CONTROL_AE_MODE_ON
                }
            PreviewFlashMode.On ->
                when {
                    aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH) ->
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    else -> CaptureRequest.CONTROL_AE_MODE_ON
                }
            PreviewFlashMode.Torch -> CaptureRequest.CONTROL_AE_MODE_ON
        }
    }

    /**
     * Torch / [CaptureRequest.FLASH_MODE] at the end of preview request assembly (after AE is set).
     * Highlight (H) + vendor highlight AE: only honors **Torch** vs off; does not replace AE mode.
     */
    fun applyPreviewFlashHardwareKeys(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        flashMode: PreviewFlashMode,
        commandDialMode: CommandDialMode,
        manualSensor: Boolean,
    ) {
        if (manualSensor || !hasFlashModeKey(chars)) {
            if (hasFlashModeKey(chars)) safeFlashOff(req, chars)
            return
        }
        if (!flashHardwareAvailable(chars) || !isBackCamera(chars)) {
            safeFlashOff(req, chars)
            return
        }
        val aeMode =
            runCatching { req.get(CaptureRequest.CONTROL_AE_MODE) }.getOrNull()
        val highlightStyleH =
            commandDialMode == CommandDialMode.H &&
                aeMode != null &&
                aeMode != CaptureRequest.CONTROL_AE_MODE_ON &&
                aeMode != CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH &&
                aeMode != CaptureRequest.CONTROL_AE_MODE_OFF
        if (highlightStyleH) {
            when (flashMode) {
                PreviewFlashMode.Torch ->
                    runCatching { req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH) }
                        .onFailure { Log.w(TAG, "torch (H highlight): ${it.message}") }
                else -> safeFlashOff(req, chars)
            }
            return
        }
        when (flashMode) {
            PreviewFlashMode.Torch ->
                runCatching { req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH) }
                    .onFailure { Log.w(TAG, "torch: ${it.message}") }
            PreviewFlashMode.On -> {
                val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
                if (aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)) {
                    safeFlashOff(req, chars)
                } else {
                    runCatching { req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH) }
                        .onFailure { Log.w(TAG, "torch (On fallback): ${it.message}") }
                }
            }
            PreviewFlashMode.Off, PreviewFlashMode.Auto -> safeFlashOff(req, chars)
        }
    }

    /**
     * Single still capture: fire flash when user asked for flash / auto / torch (RAW path still benefits
     * from preflash metering when JPEG companion exists).
     */
    fun applyStillFlashKeys(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        flashMode: PreviewFlashMode,
        manualSensorStill: Boolean,
    ) {
        if (manualSensorStill || !hasFlashModeKey(chars)) {
            if (hasFlashModeKey(chars)) safeFlashOff(req, chars)
            return
        }
        if (!flashHardwareAvailable(chars) || !isBackCamera(chars)) {
            safeFlashOff(req, chars)
            return
        }
        when (flashMode) {
            PreviewFlashMode.Off -> safeFlashOff(req, chars)
            PreviewFlashMode.Auto, PreviewFlashMode.On, PreviewFlashMode.Torch -> {
                runCatching { req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE) }
                    .onFailure { Log.w(TAG, "still SINGLE: ${it.message}") }
            }
        }
    }
}
