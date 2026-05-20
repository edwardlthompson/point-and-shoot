package dev.pointandshoot.fleet

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

/**
 * Milestone **13.5** — enumerate fleet camera ids (public + API 30+ physical children).
 */
data class FleetCameraCatalogEntry(
    val cameraId: String,
    val physicalChildIds: Set<String>,
    val isHiddenProbe: Boolean,
)

data class FleetCameraCatalogSnapshot(
    val deviceModel: String,
    val entries: List<FleetCameraCatalogEntry>,
) {
    val publicIds: List<String> = entries.filter { !it.isHiddenProbe }.map { it.cameraId }
}

object FleetCameraCatalog {
    /**
     * @param probeHiddenIds When true, probes integer ids **0..99** not in [CameraManager.getCameraIdList]
     * (debug / engineering only — slow on some devices).
     */
    fun build(
        cameraManager: CameraManager,
        deviceModel: String = Build.MODEL,
        probeHiddenIds: Boolean = false,
    ): FleetCameraCatalogSnapshot {
        val listed = cameraManager.cameraIdList.toSortedSet()
        val entries = mutableListOf<FleetCameraCatalogEntry>()
        for (id in listed.sorted()) {
            val physical =
                runCatching {
                    cameraManager.getCameraCharacteristics(id).physicalCameraIds
                }.getOrDefault(emptySet())
            entries +=
                FleetCameraCatalogEntry(
                    cameraId = id,
                    physicalChildIds = physical,
                    isHiddenProbe = false,
                )
        }
        if (probeHiddenIds) {
            for (i in 0..99) {
                val id = i.toString()
                if (id in listed) continue
                val chars =
                    runCatching { cameraManager.getCameraCharacteristics(id) }.getOrNull()
                        ?: continue
                val lensFacing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
                if (lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) continue
                entries +=
                    FleetCameraCatalogEntry(
                        cameraId = id,
                        physicalChildIds = chars.physicalCameraIds,
                        isHiddenProbe = true,
                    )
            }
        }
        return FleetCameraCatalogSnapshot(deviceModel = deviceModel, entries = entries.sortedBy { it.cameraId })
    }
}
