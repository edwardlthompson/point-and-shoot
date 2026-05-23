package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.Log
import android.util.Size
/**
 * Secondary rear camera for a live (non-WYSIWYG) finder during MediaCodec HFR record.
 *
 * Primary record camera uses encoder-only constrained high-speed; this monitor opens a
 * different back camera at ~30 fps (typically wide while tele records).
 */
object HfrRecordMonitorSupport {
    const val TAG = "PNS.HfrMonitor"

    /** GLES / monitor session buffer target (~960×540 class). */
    val MONITOR_BUFFER_TARGET = Size(960, 540)

    const val MONITOR_TARGET_FPS = 30

    fun pickMonitorCameraId(
        cm: CameraManager,
        recordCameraId: String,
        allIds: List<String>,
    ): String? {
        val roles = BackCameraRoleResolver.resolve(cm, allIds)
        return pickMonitorCameraId(roles, recordCameraId, allIds, cm)
    }

    internal fun pickMonitorCameraId(
        roles: BackCameraRoleResolver.Roles,
        recordCameraId: String,
        allIds: List<String>,
        cm: CameraManager? = null,
    ): String? {
        val wide = roles.wide
        val uw = roles.ultraWide
        val tele = roles.tele
        val longTele = roles.longTele
        // Prefer wide for tele / UW record; when wide is recording, fall back to UW + UV crop.
        val pick =
            when {
                wide != null && wide != recordCameraId -> wide
                recordCameraId == wide -> uw ?: tele ?: longTele
                else ->
                    wide?.takeIf { it != recordCameraId }
                        ?: uw?.takeIf { it != recordCameraId }
                        ?: tele?.takeIf { it != recordCameraId }
            }
        if (pick != null) return pick
        return allIds.firstOrNull { id ->
            id != recordCameraId &&
                (cm == null || isBackCamera(cm, id))
        }
    }

    fun pickMonitorPreviewSize(cm: CameraManager, monitorId: String): Size {
        val map: android.hardware.camera2.params.StreamConfigurationMap? =
            runCatching {
                cm.getCameraCharacteristics(monitorId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            }.getOrNull()
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
        if (sizes.isEmpty()) return MONITOR_BUFFER_TARGET
        val target = MONITOR_BUFFER_TARGET
        val maxArea = target.width.toLong() * target.height * 2
        return sizes
            .filter { it.width.toLong() * it.height <= maxArea }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.minByOrNull { dist(it, target) }
            ?: target
    }

    fun canRunConcurrent(cm: CameraManager, recordId: String, monitorId: String): Boolean {
        if (recordId.isBlank() || monitorId.isBlank() || recordId == monitorId) return false
        val sets =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { cm.concurrentCameraIds }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
        if (sets.isEmpty()) return true
        return sets.any { recordId in it && monitorId in it }
    }

    private fun isBackCamera(cm: CameraManager, id: String): Boolean {
        val facing =
            runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            }.getOrNull()
        return facing == CameraCharacteristics.LENS_FACING_BACK
    }

    private fun dist(a: Size, b: Size): Long {
        val dw = (a.width - b.width).toLong()
        val dh = (a.height - b.height).toLong()
        return dw * dw + dh * dh
    }
}
