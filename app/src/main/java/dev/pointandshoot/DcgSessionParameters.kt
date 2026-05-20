package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.util.Range

/**
 * Sprint **13.4** — attach Qualcomm **`EnableHDRDCGMode`** on REGULAR session create when DCG video
 * is requested (HUD research toggle, ADB `pns_preview_video_dcg`, or DCG video format).
 */
object DcgSessionParameters {
    private const val TAG = "PNS.DcgSession"
    const val VENDOR_ENABLE_HDR_DCG_MODE =
        "org.codeaurora.qcamera3.sessionParameters.EnableHDRDCGMode"
    private const val VENDOR_ENABLE_AF_BRACKETING =
        "org.codeaurora.qcamera3.sessionParameters.EnableAFBracketing"

    fun shouldAttach(
        enableResearchDcgHdr: Boolean,
        adbPreviewVideoDcg: Boolean,
    ): Boolean = enableResearchDcgHdr || adbPreviewVideoDcg

    /**
     * Optional REGULAR-session template (API 33+). Returns null when no vendor keys apply.
     */
    fun buildSessionParametersTemplate(
        camera: CameraDevice,
        characteristics: CameraCharacteristics,
        camId: String,
        prefs: HudSettings,
        previewFpsRange: Range<Int>?,
        attachDcg: Boolean,
        attachAfBracketing: Boolean,
    ): CaptureRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (!attachDcg && !attachAfBracketing) return null
        val b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        var any = false
        if (attachDcg) {
            val dcg =
                VendorKeyGuard.trySetVendorSessionEnable(
                    b,
                    characteristics,
                    VENDOR_ENABLE_HDR_DCG_MODE,
                )
            if (dcg != null) {
                any = true
                Log.i(TAG, "sessionTemplate EnableHDRDCGMode type=$dcg cam=$camId")
            } else {
                Log.w(TAG, "sessionTemplate EnableHDRDCGMode not advertised cam=$camId")
            }
        }
        if (attachAfBracketing) {
            val af =
                VendorKeyGuard.trySetVendorSessionEnable(
                    b,
                    characteristics,
                    VENDOR_ENABLE_AF_BRACKETING,
                )
            if (af != null) {
                any = true
                Log.i(TAG, "sessionTemplate EnableAFBracketing type=$af cam=$camId")
            }
        }
        if (!any) return null
        PreviewAeAntibanding.applyToRequest(b, characteristics)
        PreviewStabilization.applyToRequest(
            b,
            characteristics,
            prefs,
            previewFpsRange = previewFpsRange,
            manualSensor = false,
            isStillCapture = false,
        )
        return b.build()
    }
}
