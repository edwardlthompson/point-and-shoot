package dev.pointandshoot

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Range

/**
 * Builds [HardwareCaps] from live [CameraCharacteristics] for the **active** preview camera
 * plus roster-aware signals (e.g. ultra-wide macro) — Sprint **5.3** / `CapabilityGate`.
 */
object HardwareCapsSnapshot {

    /** OPLUS close-up macro hint surfaced in probe JSON (`PROBE_RESULTS.md`). */
    const val VENDOR_MACRO_CLOSEUP_REQUEST: String = "com.oplus.macro.closeup.enable"

    fun build(
        cm: CameraManager,
        activeCameraId: String?,
        allCameraIds: List<String>,
    ): HardwareCaps {
        if (activeCameraId.isNullOrBlank()) {
            return HardwareCaps(
                hasRawCapability = false,
                has12BitDepth = false,
                has120FpsHfr = false,
                hasFaceDetectFull = false,
                hasPreviewHistogram = false,
                aeCompensationStepsAvailable = 0,
                hasMacroMode = false,
                has10BitHdrPipeline = false,
                hasOpticalStabilization = false,
                supportedCameraExtensionLabels = "",
                supportsYuvReprocessing = false,
                supportsPrivateReprocessing = false,
                reprocessMaxCaptureStall = null,
                reprocessEffectiveExposureRequestKey = false,
                activeApertureCount = 0,
            )
        }
        val cc =
            runCatching { cm.getCameraCharacteristics(activeCameraId) }.getOrNull()
                ?: return HardwareCaps(
                    hasRawCapability = false,
                    has12BitDepth = false,
                    has120FpsHfr = false,
                    hasFaceDetectFull = false,
                    hasPreviewHistogram = false,
                    aeCompensationStepsAvailable = 0,
                    hasMacroMode = false,
                    has10BitHdrPipeline = false,
                    hasOpticalStabilization = false,
                    supportedCameraExtensionLabels = "",
                    supportsYuvReprocessing = false,
                    supportsPrivateReprocessing = false,
                    reprocessMaxCaptureStall = null,
                    reprocessEffectiveExposureRequestKey = false,
                    activeApertureCount = 0,
                )

        val roles = BackCameraRoleResolver.resolve(cm, allCameraIds)
        val uwId = roles.ultraWide
        val uwSummary =
            uwId?.let { id ->
                runCatching {
                    LensInfoExtractor.extract(id, cm.getCameraCharacteristics(id))
                }.getOrNull()
            }

        val rawPick = RawCaptureSupport.pickRawOutput(cc)
        val hasRaw = rawPick != null
        val has12 =
            rawPick?.first == ImageFormat.RAW12 ||
                runCatching {
                    val map =
                        cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            ?: return@runCatching false
                    map.getOutputSizes(ImageFormat.RAW12)?.isNotEmpty() == true
                }.getOrDefault(false)

        val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val has120 = map != null && highSpeedMaxUpper(map) >= 120
        val faceModes =
            cc.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
                ?: intArrayOf()
        val hasFaceFull =
            faceModes.contains(android.hardware.camera2.CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL)

        val hasYuv =
            map != null &&
                runCatching { map.getOutputSizes(ImageFormat.YUV_420_888)?.isNotEmpty() == true }
                    .getOrDefault(false)

        val aeSteps = aeCompensationStepCount(cc)

        val uwMacro = uwSummary?.isMacroCapable == true
        val vendorMacro =
            uwId != null &&
                runCatching {
                    val uwCc = cm.getCameraCharacteristics(uwId)
                    VendorKeyGuard.isRequestKeyAvailable(uwCc, VENDOR_MACRO_CLOSEUP_REQUEST) ||
                        VendorKeyGuard.isSessionKeyAvailable(uwCc, VENDOR_MACRO_CLOSEUP_REQUEST)
                }.getOrDefault(false)
        val activeVendorMacro =
            runCatching {
                VendorKeyGuard.isRequestKeyAvailable(cc, VENDOR_MACRO_CLOSEUP_REQUEST) ||
                    VendorKeyGuard.isSessionKeyAvailable(cc, VENDOR_MACRO_CLOSEUP_REQUEST)
            }.getOrDefault(false)
        val hasMacro = uwMacro || vendorMacro || activeVendorMacro

        val has10Hdr =
            Build.VERSION.SDK_INT >= 33 &&
                runCatching {
                    cc.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES) != null
                }.getOrDefault(false)

        val lens = LensInfoExtractor.extract(activeCameraId, cc)

        val extIds = CameraExtensionSupport.supportedExtensionInts(cm, activeCameraId)
        val extensionLabels =
            if (extIds.isEmpty()) {
                ""
            } else {
                extIds.joinToString { CameraExtensionSupport.extensionLabel(it) }
            }

        return HardwareCaps(
            hasRawCapability = hasRaw,
            has12BitDepth = has12,
            has120FpsHfr = has120,
            hasFaceDetectFull = hasFaceFull,
            hasPreviewHistogram = hasYuv,
            aeCompensationStepsAvailable = aeSteps,
            hasMacroMode = hasMacro,
            has10BitHdrPipeline = has10Hdr,
            hasOpticalStabilization = lens.hasOpticalStabilization,
            supportedCameraExtensionLabels = extensionLabels,
            supportsYuvReprocessing = PreviewReprocessStillHints.supportsYuvReprocessing(cc),
            supportsPrivateReprocessing = PreviewReprocessStillHints.supportsPrivateReprocessing(cc),
            reprocessMaxCaptureStall = PreviewReprocessStillHints.reprocessMaxCaptureStall(cc),
            reprocessEffectiveExposureRequestKey = PreviewReprocessStillHints.reprocessEffectiveExposureKeyAdvertised(cc),
            activeApertureCount = PreviewApertureSupport.availableApertures(cc).size,
        )
    }

    private fun highSpeedMaxUpper(map: StreamConfigurationMap): Int {
        val sizes = runCatching { map.highSpeedVideoSizes }.getOrNull() ?: return 0
        var best = 0
        for (s in sizes) {
            val ranges =
                runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull() ?: continue
            for (r in ranges) {
                if (r.upper > best) best = r.upper
            }
        }
        return best
    }

    private fun aeCompensationStepCount(cc: CameraCharacteristics): Int {
        val range =
            cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                ?: return 0
        val stepRat = cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: return 0
        val span = range.upper - range.lower
        if (span == 0) return 0
        val denom = stepRat.denominator.coerceAtLeast(1)
        val stepFloat = stepRat.numerator.toFloat() / denom.toFloat()
        if (stepFloat <= 0f) return 1
        return (span / stepFloat).toInt().coerceAtLeast(1)
    }
}
