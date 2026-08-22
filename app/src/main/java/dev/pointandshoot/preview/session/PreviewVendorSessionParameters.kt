package dev.pointandshoot.preview.session

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Range
import android.util.Size
import dev.pointandshoot.DcgSessionParameters
import dev.pointandshoot.HudSettings
import dev.pointandshoot.UltraHd60RecordSupport
import dev.pointandshoot.UltraHd60SessionParameters
import dev.pointandshoot.VendorKeyGuard

/**
 * REGULAR-session vendor template: UHD60 (13.x), **EnableHDRDCGMode** (13.4), AF bracketing (10.6),
 * experimental max-res unlock keys — extracted from `PreviewEngineScreen` (H.CRI-5 slice 4).
 */
object PreviewVendorSessionParameters {
    data class Input(
        val characteristics: CameraCharacteristics,
        val camId: String,
        val prefs: HudSettings,
        val streamConfigurationMap: android.hardware.camera2.params.StreamConfigurationMap?,
        val recordSize: Size,
        val desiredFps: Int,
        val inAppVideoRecordingArmed: Boolean,
        val recorderPresent: Boolean,
        val adbPreviewVideoDcg: Boolean,
        val wantsRawVideoLane: Boolean,
        val previewFpsRange: Range<Int>?,
        val experimentalVendorSessionAllowed: Boolean,
        val maxResSweepSessionKeys: List<String>,
        val webcamUhd60: Boolean = false,
    )

    fun sessionSweepKeys(
        camId: String,
        attachExperimentalVendorSession: Boolean,
        maxResSweepSessionKeys: List<String>,
    ): List<String> =
        if (attachExperimentalVendorSession && camId == "2") {
            maxResSweepSessionKeys
        } else {
            emptyList()
        }

    fun build(
        camera: CameraDevice,
        input: Input,
        onInfoLog: (String) -> Unit,
        onMaxResSweepProbe: (keyName: String, present: Boolean, settable: Boolean, hitType: String?) -> Unit,
    ): CaptureRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val ch = input.characteristics
        val camId = input.camId
        val prefs = input.prefs
        val map = input.streamConfigurationMap
        if (
            UltraHd60SessionParameters.shouldAttach(
                recordSize = input.recordSize,
                desiredFps = input.desiredFps,
                inAppVideoRecordingArmed = input.inAppVideoRecordingArmed,
                recorderPresent = input.recorderPresent,
                map = map,
                webcamUhd60 = input.webcamUhd60,
            )
        ) {
            val fpsRange = UltraHd60RecordSupport.recordFpsRange(input.desiredFps)
            val uhd =
                UltraHd60SessionParameters.buildSessionParametersTemplate(
                    camera = camera,
                    characteristics = ch,
                    camId = camId,
                    prefs = prefs,
                    recordFpsRange = fpsRange,
                )
            if (uhd != null) {
                onInfoLog(
                    "uhd60SessionTemplate aeFps=${fpsRange.lower}-${fpsRange.upper} " +
                        "record=${input.recordSize.width}x${input.recordSize.height} cam=$camId",
                )
            }
            return uhd
        }
        val attachDcg =
            DcgSessionParameters.shouldAttach(
                enableResearchDcgHdr = prefs.enableResearchDcgHDR,
                adbPreviewVideoDcg = input.adbPreviewVideoDcg,
            ) && !input.wantsRawVideoLane
        val attachExperimentalVendorSession = input.experimentalVendorSessionAllowed
        val sessionSweepKeys =
            sessionSweepKeys(camId, attachExperimentalVendorSession, input.maxResSweepSessionKeys)
        val template =
            DcgSessionParameters.buildSessionParametersTemplate(
                camera = camera,
                characteristics = ch,
                camId = camId,
                prefs = prefs,
                previewFpsRange = input.previewFpsRange,
                attachDcg = attachDcg,
                attachAfBracketing = prefs.enableResearchAfBracketing,
                attachExperimentalVendorSession = attachExperimentalVendorSession,
                extraExperimentalSessionKeys = sessionSweepKeys,
            )
        if (template != null && attachDcg) {
            onInfoLog("dcgSessionTemplate=EnableHDRDCGMode cam=$camId")
        }
        if (template != null && attachExperimentalVendorSession) {
            onInfoLog("maxResUnlock sessionVendorTemplateApplied cam=$camId")
        }
        if (sessionSweepKeys.isNotEmpty()) {
            for (keyName in sessionSweepKeys) {
                val present = VendorKeyGuard.captureSessionKey(ch, keyName) != null
                val b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val hit = VendorKeyGuard.trySetVendorSessionEnable(b, ch, keyName)
                val settable = hit != null
                onMaxResSweepProbe(keyName, present, settable, hit)
                onInfoLog(
                    "maxResSweep scope=session cam=$camId key=$keyName present=$present " +
                        "settable=$settable type=${hit ?: "none"}",
                )
            }
        }
        return template
    }
}
