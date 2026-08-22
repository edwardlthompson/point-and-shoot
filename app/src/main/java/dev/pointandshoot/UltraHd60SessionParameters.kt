package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.util.Range
import android.util.Size

/**
 * Session parameters for **4K @ 60 fps** REGULAR record sessions on Qualcomm fleet devices.
 *
 * Sets [CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE] on the session template (advertised as a
 * session key on CPH2583) and best-effort **dynamicFPSConfig** when HAL lists it.
 */
object UltraHd60SessionParameters {
    private const val TAG = "PNS.UltraHd60"
    const val VENDOR_DYNAMIC_FPS_CONFIG =
        "org.codeaurora.qcamera3.sessionParameters.dynamicFPSConfig"

    fun shouldAttach(
        recordSize: Size,
        desiredFps: Int,
        inAppVideoRecordingArmed: Boolean,
        recorderPresent: Boolean,
        map: android.hardware.camera2.params.StreamConfigurationMap?,
        webcamUhd60: Boolean = false,
    ): Boolean {
        if (webcamUhd60 && desiredFps == UltraHd60RecordSupport.TARGET_FPS &&
            UltraHd60RecordSupport.isUltraHdSize(recordSize.width, recordSize.height)
        ) {
            return true
        }
        return UltraHd60RecordSupport.needsUltraHd60Delivery(
            recordSize = recordSize,
            desiredFps = desiredFps,
            map = map,
            inAppVideoRecordingArmed = inAppVideoRecordingArmed,
            recorderPresent = recorderPresent,
        )
    }

    fun buildSessionParametersTemplate(
        camera: CameraDevice,
        characteristics: CameraCharacteristics,
        camId: String,
        prefs: HudSettings,
        recordFpsRange: Range<Int>,
    ): CaptureRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val b = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        var any = false
        b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, recordFpsRange)
        any = true
        Log.i(
            TAG,
            "sessionTemplate aeTargetFps=${recordFpsRange.lower}-${recordFpsRange.upper} cam=$camId",
        )
        VendorKeyGuard.trySetVendorSessionEnable(
            b,
            characteristics,
            VENDOR_DYNAMIC_FPS_CONFIG,
        )?.let { kind ->
            any = true
            Log.i(TAG, "sessionTemplate dynamicFPSConfig type=$kind cam=$camId")
        }
        if (!any) return null
        PreviewAeAntibanding.applyToRequest(b, characteristics)
        PreviewStabilization.applyToRequest(
            b,
            characteristics,
            prefs,
            previewFpsRange = recordFpsRange,
            manualSensor = false,
            isStillCapture = false,
        )
        return b.build()
    }
}
