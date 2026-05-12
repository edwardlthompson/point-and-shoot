package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log

/**
 * Optional [CaptureRequest.CONTROL_AUTOFRAMING] for preview when the HAL advertises support
 * (`BUILD_PLAN.md` Milestone 4 Sprint 4.4). Default **off** in [HudSettings] so it never fights the
 * app's own face / tap metering unless the operator enables it.
 */
object PreviewAutoFraming {
    private const val TAG = "PNS.AutoFraming"

    fun applyIfAvailable(
        builder: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        settings: HudSettings,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (!settings.enableAutoFraming) return
        val keys = chars.availableCaptureRequestKeys ?: return
        if (!keys.contains(CaptureRequest.CONTROL_AUTOFRAMING)) return
        val supported =
            chars.get(CameraCharacteristics.CONTROL_AUTOFRAMING_AVAILABLE) ?: false
        if (supported != true) return
        runCatching { builder.set(CaptureRequest.CONTROL_AUTOFRAMING, CaptureRequest.CONTROL_AUTOFRAMING_ON) }
            .onFailure { Log.w(TAG, "CONTROL_AUTOFRAMING: ${it.message}") }
    }
}
