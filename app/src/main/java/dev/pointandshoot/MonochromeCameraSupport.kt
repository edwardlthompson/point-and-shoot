package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.abs

private val COLOR_CFA_PATTERNS =
    setOf(
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB,
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG,
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG,
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR,
    )

/**
 * Detect dedicated monochrome sensors, including legacy HALs that omit the MONOCHROME
 * capability but expose a non-Bayer CFA on a back, non-flash camera.
 */
fun isDedicatedMonochromeCamera(chars: CameraCharacteristics): Boolean {
    val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
    if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME)) {
        return true
    }

    val cfa = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) ?: return false
    if (cfa in COLOR_CFA_PATTERNS) return false

    val lensFacingBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    val hasRawCapability = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
    return lensFacingBack && hasRawCapability
}

fun findDedicatedMonochromeCameraId(
    context: Context,
    cameraIds: List<String>,
): String? {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val snapshots = cameraIds.mapNotNull { cameraId ->
        val chars = runCatching { cm.getCameraCharacteristics(cameraId) }.getOrNull() ?: return@mapNotNull null
        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val cfa = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        Snapshot(
            id = cameraId,
            backFacing = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK,
            hasRaw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW),
            hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            focalMm = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull(),
            activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE),
            explicitMono = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME),
            nonBayerCfa = cfa != null && cfa !in COLOR_CFA_PATTERNS,
        )
    }

    // Preferred path: explicit MONO capability or a non-Bayer CFA on a RAW-capable back camera.
    snapshots.firstOrNull { it.backFacing && it.hasRaw && (it.explicitMono || it.nonBayerCfa) }?.let { return it.id }

    // Legacy fallback: RGB+mono pairs sometimes expose only Bayer/opaque capabilities in CameraCharacteristics.
    // Detect paired rear modules with near-identical focal/array where one has flash and the other does not.
    for (mono in snapshots) {
        if (!mono.backFacing || !mono.hasRaw || mono.hasFlash) continue
        val pair =
            snapshots.firstOrNull { color ->
                color.id != mono.id &&
                    color.backFacing &&
                    color.hasRaw &&
                    color.hasFlash &&
                    sameFocal(color.focalMm, mono.focalMm) &&
                    sameActiveArray(color.activeArray, mono.activeArray)
            }
        if (pair != null) return mono.id
    }
    return null
}

private data class Snapshot(
    val id: String,
    val backFacing: Boolean,
    val hasRaw: Boolean,
    val hasFlash: Boolean,
    val focalMm: Float?,
    val activeArray: android.graphics.Rect?,
    val explicitMono: Boolean,
    val nonBayerCfa: Boolean,
)

private fun sameFocal(a: Float?, b: Float?): Boolean {
    if (a == null || b == null) return false
    return abs(a - b) <= 0.05f
}

private fun sameActiveArray(a: android.graphics.Rect?, b: android.graphics.Rect?): Boolean {
    if (a == null || b == null) return false
    val dw = abs(a.width() - b.width())
    val dh = abs(a.height() - b.height())
    return dw <= 16 && dh <= 16
}
