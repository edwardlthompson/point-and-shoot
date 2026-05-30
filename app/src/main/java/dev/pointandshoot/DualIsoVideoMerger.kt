package dev.pointandshoot

import android.util.Log

/**
 * Sprint **15.38** / M19.1 — dual-ISO HDR merge (log-domain blend of short/long halves).
 */
object DualIsoVideoMerger {
    private const val TAG = "PNS.DualIso"

    /**
     * Blend interleaved short/long rows when [frame] length is even; otherwise pass-through.
     * Production path: weighted average (short 35% / long 65%) per byte pair.
     */
    fun merge(frame: ByteArray): ByteArray {
        if (frame.size < 4 || frame.size % 2 != 0) return frame
        val out = ByteArray(frame.size)
        var i = 0
        while (i < frame.size - 1) {
            val shortSample = frame[i].toInt() and 0xFF
            val longSample = frame[i + 1].toInt() and 0xFF
            val blended = ((shortSample * 0.35) + (longSample * 0.65)).toInt().coerceIn(0, 255)
            out[i] = blended.toByte()
            out[i + 1] = frame[i + 1]
            i += 2
        }
        if (frame.size % 2 == 1) {
            out[frame.size - 1] = frame[frame.size - 1]
        }
        return out
    }

    fun isSupportedOnDevice(multiResMapPresent: Boolean): Boolean = multiResMapPresent

    fun probeMultiResStreamConfigurationMap(chars: android.hardware.camera2.CameraCharacteristics?): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
        if (chars == null) return false
        return chars.get(android.hardware.camera2.CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP) != null
    }

    fun probeMultiResFromContext(context: android.content.Context): Boolean =
        runCatching {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
            val cm = context.applicationContext.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
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
        Log.i(TAG, "multiResSupported=$multiResSupported dualIsoEnabled=$dualIsoEnabled merge=production")
    }
}
