package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

/**
 * HAL concurrent rear+front probe and policy for Sprint **15.5** dual video.
 *
 * Many devices (e.g. CPH2655) expose independent logical camera IDs but omit
 * [CameraManager.getConcurrentCameraIds] — we still attempt dual open with capped front size/FPS.
 */
object DualVideoHalConcurrency {
    private const val TAG = DualVideoRecordingController.TAG

    data class Probe(
        val rearId: String?,
        val frontId: String?,
        val advertisedConcurrent: Boolean,
        val concurrentSets: Int,
    )

    data class ResolvedDualRear(
        /** Rear id to use for dual preview/record (may equal [requestedRearId]). */
        val rearId: String?,
        /** True when [rearId] differs from [requestedRearId] and HAL lists a concurrent set with [frontId]. */
        val switchedForConcurrentHal: Boolean,
        /** [rearId] + [frontId] appear in the same [CameraManager.getConcurrentCameraIds] set. */
        val pairAdvertisedConcurrent: Boolean,
    )

    fun probe(cm: CameraManager, rearId: String?, frontId: String?): Probe {
        val frontIdResolved = frontId
        val sets =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { cm.concurrentCameraIds }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
        val advertised =
            rearId != null &&
                frontIdResolved != null &&
                DualVideoRecordingController.canRunConcurrentRearFront(cm, rearId, frontIdResolved)
        val summary =
            sets.joinToString(prefix = "[", postfix = "]") { it.sorted().joinToString(",") }
        Log.i(
            TAG,
            "halConcurrency rear=$rearId front=$frontIdResolved advertised=$advertised " +
                "concurrentSets=${sets.size} ids=$summary",
        )
        return Probe(
            rearId = rearId,
            frontId = frontIdResolved,
            advertisedConcurrent = advertised,
            concurrentSets = sets.size,
        )
    }

    /** HAL lists this rear+front pair in [CameraManager.getConcurrentCameraIds]. */
    fun allowSimultaneousDualPreview(probe: Probe): Boolean = probe.advertisedConcurrent

    /** Open front for stacked preview/record (not only when muxer is armed). */
    fun allowFrontCameraOpen(probe: Probe, recordingArmed: Boolean, delayedPreviewTry: Boolean): Boolean =
        when {
            probe.rearId == null || probe.frontId == null -> false
            probe.advertisedConcurrent -> true
            recordingArmed -> true
            delayedPreviewTry -> true
            else -> false
        }

    /** Rear preview frames required before opening front on non-concurrent devices. */
    fun minRearFramesBeforeFrontOpen(probe: Probe): Int =
        if (probe.advertisedConcurrent) 24 else 48

    /**
     * When the user's rear id (e.g. wide **2**) is not in a concurrent set with front **1**, pick the
     * other id from the set that contains [frontId] (CPH2655: **[0, 1]**).
     */
    fun resolveRearForDual(
        cm: CameraManager,
        requestedRearId: String?,
        frontId: String?,
    ): ResolvedDualRear {
        if (requestedRearId.isNullOrBlank() || frontId.isNullOrBlank()) {
            return ResolvedDualRear(
                rearId = requestedRearId,
                switchedForConcurrentHal = false,
                pairAdvertisedConcurrent = false,
            )
        }
        if (DualVideoRecordingController.canRunConcurrentRearFront(cm, requestedRearId, frontId)) {
            return ResolvedDualRear(
                rearId = requestedRearId,
                switchedForConcurrentHal = false,
                pairAdvertisedConcurrent = true,
            )
        }
        val sets =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { cm.concurrentCameraIds }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
        val withFront = sets.firstOrNull { frontId in it } ?: emptySet()
        if (withFront.isEmpty()) {
            return ResolvedDualRear(
                rearId = requestedRearId,
                switchedForConcurrentHal = false,
                pairAdvertisedConcurrent = false,
            )
        }
        val rearInSet =
            withFront
                .filter { it != frontId }
                .firstOrNull { isBackFacing(cm, it) }
                ?: withFront.firstOrNull { it != frontId }
        if (rearInSet == null) {
            return ResolvedDualRear(
                rearId = requestedRearId,
                switchedForConcurrentHal = false,
                pairAdvertisedConcurrent = false,
            )
        }
        val switched = rearInSet != requestedRearId
        if (switched) {
            Log.i(
                TAG,
                "resolveRearForDual switch requested=$requestedRearId -> concurrent=$rearInSet front=$frontId",
            )
        }
        return ResolvedDualRear(
            rearId = rearInSet,
            switchedForConcurrentHal = switched,
            pairAdvertisedConcurrent = true,
        )
    }

    private fun isBackFacing(cm: CameraManager, id: String): Boolean {
        val facing =
            runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            }.getOrNull()
        return facing == CameraCharacteristics.LENS_FACING_BACK
    }
}
