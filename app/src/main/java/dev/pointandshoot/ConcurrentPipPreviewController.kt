package dev.pointandshoot

import android.hardware.camera2.CameraManager
import android.util.Log
import dev.pointandshoot.fleet.DeviceFeatureGates

/**
 * Concurrent rear+rear PiP preview orchestrator (Milestone **20.3**).
 *
 * Preview-only inset when HAL lists a concurrent back-camera pair.
 */
object ConcurrentPipPreviewController {
    const val TAG = "PNS.PipPreview"

    /** Inset width fraction of finder (LG-style ~28%). */
    const val INSET_WIDTH_FRACTION = 0.28f

    data class ResolvedPair(
        val primaryRearId: String,
        val auxRearId: String,
        val halConcurrent: Boolean,
    )

    fun resolvePair(cm: CameraManager, selectedRearId: String?, cameraIds: List<String>): ResolvedPair? {
        val backs = cameraIds.filter { Camera2Facing.isBack(cm, it) }
        if (backs.size < 2) return null
        val sets =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                runCatching { cm.concurrentCameraIds }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
        val pair =
            DeviceFeatureGates.findRearRearConcurrentPair(backs, sets) ?: return null
        val primary = selectedRearId?.takeIf { it in backs } ?: pair.first
        val aux = if (primary == pair.first) pair.second else pair.first
        if (primary == aux) return null
        val concurrent = sets.any { primary in it && aux in it }
        return ResolvedPair(primaryRearId = primary, auxRearId = aux, halConcurrent = concurrent)
    }

    fun logActive(active: Boolean, pair: ResolvedPair?, reason: String? = null) {
        Log.i(
            TAG,
            "pipPreview=active=$active primary=${pair?.primaryRearId} aux=${pair?.auxRearId} " +
                "halConcurrent=${pair?.halConcurrent ?: false} reason=${reason ?: ""}",
        )
    }
}
