package dev.pointandshoot.fleet

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import dev.pointandshoot.Camera2Facing
import dev.pointandshoot.DualVideoRecordingController
import org.json.JSONObject

/**
 * Device-level concurrency gates for fleet matrix + catalog evaluators (Milestone **20**).
 */
object DeviceFeatureGates {
    data class Slice(
        val dualVideo: FleetDeviceMatrixStructured.FeatureGate,
        val multicamMelt: FleetDeviceMatrixStructured.FeatureGate,
        val pipPreview: FleetDeviceMatrixStructured.FeatureGate,
        val detail: JSONObject,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("dualVideo", dualVideo.toJson())
                put("multicamMelt", multicamMelt.toJson())
                put("pipPreview", pipPreview.toJson())
                put("detail", detail)
            }
    }

    fun build(cm: CameraManager): Slice {
        val ids = cm.cameraIdList.sorted()
        val frontId = Camera2Facing.frontCameraId(cm, ids)
        val backIds = ids.filter { isBackFacing(cm, it) }
        val primaryRear = backIds.firstOrNull()
        val concurrentSets =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { cm.concurrentCameraIds }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
        val dualHal =
            primaryRear != null &&
                frontId != null &&
                DualVideoRecordingController.canRunConcurrentRearFront(cm, primaryRear, frontId)
        val dualAdvertised = frontId != null && backIds.isNotEmpty()
        val dualSessionOk = dualAdvertised
        val dualAppEnabled = DualVideoRecordingController.IS_WIRED && dualSessionOk

        val maxBackConcurrent =
            concurrentSets
                .map { set -> set.count { id -> id in backIds } }
                .maxOrNull()
                ?.coerceAtLeast(1)
                ?: 1
        val meltAdvertised = maxBackConcurrent >= 2
        val meltSessionOk = meltAdvertised
        val meltAppEnabled = meltAdvertised

        val pipPair = findRearRearConcurrentPair(backIds, concurrentSets)
        val pipAdvertised = pipPair != null
        val pipSessionOk = pipAdvertised
        val pipAppEnabled = pipAdvertised

        val detail =
            JSONObject().apply {
                put("frontCameraId", frontId ?: JSONObject.NULL)
                put("primaryRearId", primaryRear ?: JSONObject.NULL)
                put("dualHalConcurrent", dualHal)
                put("concurrentSetCount", concurrentSets.size)
                put("maxConcurrentBackCameras", maxBackConcurrent)
                put("pipPrimaryRearId", pipPair?.first ?: JSONObject.NULL)
                put("pipAuxRearId", pipPair?.second ?: JSONObject.NULL)
            }

        return Slice(
            dualVideo = FleetDeviceMatrixStructured.FeatureGate(dualAdvertised, dualSessionOk, dualAppEnabled),
            multicamMelt = FleetDeviceMatrixStructured.FeatureGate(meltAdvertised, meltSessionOk, meltAppEnabled),
            pipPreview = FleetDeviceMatrixStructured.FeatureGate(pipAdvertised, pipSessionOk, pipAppEnabled),
            detail = detail,
        )
    }

    internal fun findRearRearConcurrentPair(
        backIds: List<String>,
        concurrentSets: Set<Set<String>>,
    ): Pair<String, String>? {
        if (backIds.size < 2 || concurrentSets.isEmpty()) return null
        for (set in concurrentSets) {
            val backsInSet = set.filter { it in backIds }
            if (backsInSet.size >= 2) {
                val primary = backsInSet.first()
                val aux = backsInSet.first { it != primary }
                return primary to aux
            }
        }
        return null
    }

    private fun isBackFacing(cm: CameraManager, id: String): Boolean {
        val facing =
            runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            }.getOrNull()
        return facing == CameraCharacteristics.LENS_FACING_BACK
    }
}
