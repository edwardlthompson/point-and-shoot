package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log

/**
 * User-facing flash / torch modes for preview + still [CaptureRequest] wiring.
 *
 * See `BUILD_PLAN.md` Sprint 9.12 / 4.4; HAL truth is always [CameraCharacteristics.FLASH_INFO_AVAILABLE]
 * and [CameraCharacteristics.getAvailableCaptureRequestKeys].
 *
 * **Variable strength (API 35+):** when [CaptureRequest.FLASH_STRENGTH_LEVEL] is advertised, torch and
 * still flash requests set it from [CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL] clamped to
 * [CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL]. Torch stays on the active [android.hardware.camera2.CameraDevice]
 * session ([CaptureRequest.FLASH_MODE_TORCH]); [android.hardware.camera2.CameraManager.turnOnTorchWithStrengthLevel]
 * is not used here to avoid double-driving the LED while a preview session holds the camera.
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

    /** [CaptureRequest.FLASH_STRENGTH_LEVEL] is lint-gated to API 35+ in current platform stubs. */
    private const val FLASH_STRENGTH_LEVEL_MIN_API = 35

    /**
     * Milestone 10 Sprint **10.12**: Highlight (H) is **expose-for-highlights** metering — flash / torch
     * fight that program and surprise users; keep LED off for preview + stills while the dial is H.
     */
    fun highlightDialSuppressesFlashAndTorch(commandDialMode: CommandDialMode): Boolean =
        commandDialMode == CommandDialMode.H

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
     * Picks a request [CaptureRequest.FLASH_STRENGTH_LEVEL] from HAL default/max (API 35+ metadata).
     * Exposed for JVM unit tests.
     */
    internal fun flashStrengthLevelForHardware(defaultLevel: Int?, maxLevel: Int): Int? {
        if (maxLevel < 1) return null
        val base = (defaultLevel ?: maxLevel).coerceIn(1, maxLevel)
        return base.coerceIn(1, maxLevel)
    }

    private fun tryApplyFlashStrengthLevel(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        // FLASH_STRENGTH_LEVEL is lint-gated to API 35+ despite related FLASH_INFO_STRENGTH_* keys on 33+.
        if (Build.VERSION.SDK_INT < FLASH_STRENGTH_LEVEL_MIN_API) return
        if (!flashHardwareAvailable(chars) || !isBackCamera(chars)) return
        if (chars.availableCaptureRequestKeys?.contains(CaptureRequest.FLASH_STRENGTH_LEVEL) != true) return
        val maxLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: return
        val defaultLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL)
        val level = flashStrengthLevelForHardware(defaultLevel, maxLevel) ?: return
        runCatching {
            req.set(CaptureRequest.FLASH_STRENGTH_LEVEL, level)
        }.onFailure { Log.w(TAG, "FLASH_STRENGTH_LEVEL: ${it.message}") }
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
     * Highlight (H): **no** flash or torch ([highlightDialSuppressesFlashAndTorch]); other dials honor
     * [PreviewFlashMode] (torch / on fallback / off).
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
        if (highlightDialSuppressesFlashAndTorch(commandDialMode)) {
            safeFlashOff(req, chars)
            return
        }
        when (flashMode) {
            PreviewFlashMode.Torch -> {
                runCatching { req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH) }
                    .onFailure { Log.w(TAG, "torch: ${it.message}") }
                tryApplyFlashStrengthLevel(req, chars)
            }
            PreviewFlashMode.On -> {
                val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
                if (aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)) {
                    safeFlashOff(req, chars)
                } else {
                    runCatching { req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH) }
                        .onFailure { Log.w(TAG, "torch (On fallback): ${it.message}") }
                    tryApplyFlashStrengthLevel(req, chars)
                }
            }
            PreviewFlashMode.Off, PreviewFlashMode.Auto -> safeFlashOff(req, chars)
        }
    }

    /**
     * Single still capture: fire flash when user asked for flash / auto / torch (RAW path still benefits
     * from preflash metering when JPEG companion exists). Highlight (H): LED stays off — same as preview
     * ([highlightDialSuppressesFlashAndTorch]).
     */
    fun applyStillFlashKeys(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        flashMode: PreviewFlashMode,
        manualSensorStill: Boolean,
        commandDialMode: CommandDialMode,
    ) {
        if (manualSensorStill || !hasFlashModeKey(chars)) {
            if (hasFlashModeKey(chars)) safeFlashOff(req, chars)
            return
        }
        if (!flashHardwareAvailable(chars) || !isBackCamera(chars)) {
            safeFlashOff(req, chars)
            return
        }
        if (highlightDialSuppressesFlashAndTorch(commandDialMode)) {
            safeFlashOff(req, chars)
            return
        }
        when (flashMode) {
            PreviewFlashMode.Off -> safeFlashOff(req, chars)
            PreviewFlashMode.Auto, PreviewFlashMode.On, PreviewFlashMode.Torch -> {
                runCatching { req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE) }
                    .onFailure { Log.w(TAG, "still SINGLE: ${it.message}") }
                tryApplyFlashStrengthLevel(req, chars)
            }
        }
    }
}
