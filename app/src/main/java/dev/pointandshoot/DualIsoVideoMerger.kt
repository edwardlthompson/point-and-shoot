package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

/**
 * Sprint **15.38** — dual-ISO HDR merge stub (real merge deferred; see BUILD_PLAN future features).
 */
object DualIsoVideoMerger {
    private const val TAG = "PNS.DualIso"

    /** Pass-through merge until log-domain blend + HLG remap ships. */
    fun merge(frame: ByteArray): ByteArray = frame

    fun isSupportedOnDevice(multiResMapPresent: Boolean): Boolean = multiResMapPresent

    /**
     * True when [CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP] is non-null
     * (API 31+ only).
     */
    fun probeMultiResStreamConfigurationMap(chars: CameraCharacteristics?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (chars == null) return false
        return chars.get(CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP) != null
    }

    /** Probe wide / primary rear camera via [CameraManager]. */
    fun probeMultiResFromContext(context: Context): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
            val cm = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = cm.cameraIdList.toList()
            if (ids.isEmpty()) return false
            val roles = BackCameraRoleResolver.resolve(cm, ids)
            val active =
                roles.wide
                    ?: ids.firstOrNull { it != "1" }
                    ?: ids.first()
            probeMultiResStreamConfigurationMap(cm.getCameraCharacteristics(active))
        }.getOrDefault(false)

    fun logSessionProbe(multiResSupported: Boolean, dualIsoEnabled: Boolean) {
        Log.i(TAG, "multiResSupported=$multiResSupported dualIsoEnabled=$dualIsoEnabled")
    }
}
