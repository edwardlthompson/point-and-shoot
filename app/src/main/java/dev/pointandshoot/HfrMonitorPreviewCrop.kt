package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Center-crop UV bounds on the HFR monitor stream so a **wide** (or UW fallback) finder
 * approximates the field of view of the camera that is recording at high speed.
 */
data class HfrMonitorTextureCrop(
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
) {
    init {
        require(u0 in 0f..1f && u1 in 0f..1f && v0 in 0f..1f && v1 in 0f..1f) { "UV crop out of range" }
        require(u1 > u0 && v1 > v0) { "UV crop must be non-empty" }
    }

    companion object {
        val FULL = HfrMonitorTextureCrop(0f, 0f, 1f, 1f)

        fun centeredSpan(span: Float): HfrMonitorTextureCrop {
            val s = span.coerceIn(0.01f, 1f)
            val inset = (1f - s) / 2f
            return HfrMonitorTextureCrop(inset, inset, 1f - inset, 1f - inset)
        }
    }
}

object HfrMonitorPreviewCrop {
    fun nativeFocalLengthMm(cm: CameraManager, cameraId: String): Float? {
        val fl =
            runCatching {
                cm.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            }.getOrNull()
        return fl?.firstOrNull()?.takeIf { it > 0f }
    }

    /** Row-equivalent focal (mm) when HAL lengths are missing or unusable for FOV ratios. */
    internal fun roleEquivalentFocalMm(
        cameraId: String,
        roles: BackCameraRoleResolver.Roles,
        focalCropMode: FocalMode?,
        applyDigitalCrop: Boolean,
    ): Float? =
        when (cameraId) {
            roles.ultraWide -> 14f
            roles.wide ->
                when (focalCropMode) {
                    FocalMode.Street35 -> 35f
                    FocalMode.Standard50 -> 50f
                    else -> 23f
                }
            roles.tele, roles.longTele ->
                when (focalCropMode) {
                    FocalMode.Portrait85 -> 85f
                    FocalMode.LongTele150 -> 150f
                    else -> 73f
                }
            else -> null
        }?.let { base ->
            if (applyDigitalCrop && focalCropMode != null) {
                val zoom =
                    when (cameraId) {
                        roles.wide ->
                            if (focalCropMode in setOf(FocalMode.Street35, FocalMode.Standard50)) {
                                focalCropMode.zoomFactor
                            } else {
                                1.0
                            }
                        roles.tele, roles.longTele ->
                            if (focalCropMode in setOf(FocalMode.Portrait85, FocalMode.LongTele150)) {
                                focalCropMode.zoomFactor
                            } else {
                                1.0
                            }
                        else -> 1.0
                    }
                (base * zoom).toFloat()
            } else {
                base
            }
        }

    private fun effectiveFocalMm(
        cm: CameraManager,
        cameraId: String,
        roles: BackCameraRoleResolver.Roles,
        focalCropMode: FocalMode?,
        applyDigitalCrop: Boolean,
    ): Float {
        val roleMm = roleEquivalentFocalMm(cameraId, roles, focalCropMode, applyDigitalCrop)
        val native = nativeFocalLengthMm(cm, cameraId)
        if (native == null || native <= 0f) return roleMm ?: 23f
        if (roleMm == null) return native
        // Some OEM stacks report nearly identical native focal lengths for every logical id.
        if (kotlin.math.abs(native - roleMm) / roleMm > 0.35f) return native
        return roleMm
    }

    /**
     * @param applyRecordDigitalCrop When true, include [FocalMode] sensor crop on the **record**
     *   camera (preview/photo path only; HFR record keeps this false at 120 fps).
     */
    fun computeTextureCrop(
        cm: CameraManager,
        roles: BackCameraRoleResolver.Roles,
        recordCameraId: String,
        monitorCameraId: String,
        focalCropMode: FocalMode?,
        applyRecordDigitalCrop: Boolean,
    ): HfrMonitorTextureCrop {
        val fMonitor =
            effectiveFocalMm(cm, monitorCameraId, roles, focalCropMode = null, applyDigitalCrop = false)
        val fRecord =
            effectiveFocalMm(
                cm,
                recordCameraId,
                roles,
                focalCropMode,
                applyRecordDigitalCrop,
            )
        if (fMonitor <= 0f || fRecord <= 0f) return HfrMonitorTextureCrop.FULL
        return centeredSpanForLinearZoom((fRecord / fMonitor).toDouble())
    }

    /** Visible UV span on the monitor stream for [linearZoomOnMonitor] = recordFocal / monitorFocal. */
    internal fun centeredSpanForLinearZoom(linearZoomOnMonitor: Double): HfrMonitorTextureCrop {
        if (linearZoomOnMonitor <= 1.0 + 1e-3) {
            return HfrMonitorTextureCrop.FULL
        }
        return HfrMonitorTextureCrop.centeredSpan((1.0 / linearZoomOnMonitor).toFloat())
    }
}
