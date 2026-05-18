package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.util.Log

/**
 * Outcome of [DngMetadataResolver.resolveForDngSave] for [DngCreator] plus triage fields for aux-lens DNG casts.
 */
data class DngMetadataResolution(
    val characteristics: CameraCharacteristics,
    val totalResult: TotalCaptureResult,
    val sessionCameraId: String,
    val physicalChildren: Set<String>,
    val activePhysicalFromResult: String?,
    val pickedPhysicalId: String?,
    val physicalTotalResultKeys: Set<String>?,
    /** True when [characteristics] / [totalResult] are the physical pair (HAL map contained [pickedPhysicalId]). */
    val pairedWithPhysicalTotal: Boolean,
) {
    fun toDiagSummary(): String =
        buildString {
            append("session=").append(sessionCameraId)
            append(" picked=").append(pickedPhysicalId ?: "null")
            append(" pairedPhysical=").append(pairedWithPhysicalTotal)
            append(" mapKeys=")
                .append(
                    physicalTotalResultKeys
                        ?.takeIf { it.isNotEmpty() }
                        ?.sorted()
                        ?.joinToString(",")
                        ?: "null",
                )
            append(" active=").append(activePhysicalFromResult ?: "null")
            append(" children=").append(physicalChildren.sorted().joinToString(","))
        }
}

/**
 * Picks [CameraCharacteristics] + [TotalCaptureResult] for [DngCreator] on **logical** multi-camera
 * sessions where preview is pinned to a physical child ([OutputConfiguration.setPhysicalCameraId])
 * but metadata was still taken only from the logical camera id.
 *
 * **Session outputs:** [OutputConfiguration.setPhysicalCameraId] for **preview only** (output **0**)
 * is the shipped default on logical multi-camera when the HAL omits per-physical
 * [TotalCaptureResult] entries — see [PreviewEngineScreen] session create and [Camera2SessionCompat].
 * Pinning **RAW** / **JPEG** to a physical id in that situation can still deliver auxiliary pixels while
 * [DngMetadataResolver] must use **logical** metadata for [android.hardware.camera2.DngCreator],
 * reproducing the dark / green cast. Per-physical totals are used for [DngCreator] only when
 * [resolveForDngSave.allowPhysicalTotalResultPairing] is **true** (pinned-RAW USB proof).
 *
 * **Never hybrid:** if we pick a physical id but [TotalCaptureResult.getPhysicalCameraTotalResults]
 * does **not** contain that id, we **must not** pass [android.hardware.camera2.DngCreator] physical
 * [CameraCharacteristics] with the **logical** [TotalCaptureResult] — that pairs the aux sensor’s
 * matrices/black levels with logical-camera WB/AE metadata and commonly decodes as a dark / green
 * cast. In that case we fall back to logical characteristics + logical result (same pairing as a
 * single-camera logical session).
 *
 * **Per-physical totals vs unpinned RAW:** the HAL may still expose a non-empty
 * [TotalCaptureResult.getPhysicalCameraTotalResults] map while the preview session’s RAW output is
 * **not** pinned to that physical id (logical RAW stream). Using physical characteristics + that
 * physical total with **logical** RAW pixels reproduces the **same** dark / green decode. Unless
 * [resolveForDngSave.allowPhysicalTotalResultPairing] is **true** (USB-gated, pinned-RAW stacks only),
 * we ignore populated map entries and keep **logical + logical** for [DngCreator].
 */
object DngMetadataResolver {
    private const val TAG = "PNS.DngMeta"

    /**
     * Prefer [previewPhysicalCameraId] when listed under [CameraCharacteristics.getPhysicalCameraIds],
     * else [CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID] from [totalResult].
     */
    internal fun pickPhysicalIdForDng(
        physicalChildren: Set<String>,
        previewPhysicalCameraId: String?,
        activePhysicalFromResult: String?,
    ): String? {
        if (physicalChildren.isEmpty()) return null
        previewPhysicalCameraId?.takeIf { it in physicalChildren }?.let { return it }
        activePhysicalFromResult?.takeIf { it in physicalChildren }?.let { return it }
        return null
    }

    /**
     * @param sessionCameraId [CameraDevice] id used for this session (often logical `"0"`).
     * @param logicalCharacteristics from [CameraManager.getCameraCharacteristics] for [sessionCameraId].
     */
    fun resolveForDngSave(
        cm: CameraManager,
        sessionCameraId: String,
        logicalCharacteristics: CameraCharacteristics,
        totalResult: TotalCaptureResult,
        previewPhysicalCameraId: String?,
        /**
         * When **true** and [TotalCaptureResult.getPhysicalCameraTotalResults] contains the picked
         * physical id, [DngCreator] uses **physical** [CameraCharacteristics] + that physical
         * [TotalCaptureResult]. Requires RAW surfaces pinned to the same physical id (USB proof).
         *
         * **Default false (shipped):** preview uses preview-only physical pin; RAW stays on the
         * logical stream — ignore per-physical totals even when present so metadata matches pixels.
         */
        allowPhysicalTotalResultPairing: Boolean = false,
    ): DngMetadataResolution {
        val physicalChildren =
            runCatching { logicalCharacteristics.physicalCameraIds?.toSet().orEmpty() }
                .getOrDefault(emptySet())

        val activeFromResult = totalResult.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
        val physicalTotals: Map<String, TotalCaptureResult>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { totalResult.physicalCameraTotalResults }.getOrNull()
            } else {
                null
            }
        val physicalTotalResultKeys = physicalTotals?.keys?.toSet()

        fun resolution(
            chars: CameraCharacteristics,
            result: TotalCaptureResult,
            picked: String?,
            paired: Boolean,
        ): DngMetadataResolution =
            DngMetadataResolution(
                characteristics = chars,
                totalResult = result,
                sessionCameraId = sessionCameraId,
                physicalChildren = physicalChildren,
                activePhysicalFromResult = activeFromResult,
                pickedPhysicalId = picked,
                physicalTotalResultKeys = physicalTotalResultKeys,
                pairedWithPhysicalTotal = paired,
            )

        if (physicalChildren.isEmpty()) {
            return resolution(logicalCharacteristics, totalResult, picked = null, paired = false)
        }

        val picked =
            pickPhysicalIdForDng(
                physicalChildren,
                previewPhysicalCameraId,
                activeFromResult,
            )
        if (picked == null) {
            Log.d(
                TAG,
                "dng metadata: keep logical sessionCameraId=$sessionCameraId " +
                    "(no physical pick; children=$physicalChildren activeResult=$activeFromResult)",
            )
            return resolution(logicalCharacteristics, totalResult, picked = null, paired = false)
        }

        val physicalChars =
            runCatching { cm.getCameraCharacteristics(picked) }.getOrNull()
        if (physicalChars == null) {
            Log.w(TAG, "dng metadata: getCameraCharacteristics failed id=$picked; keep logical")
            return resolution(logicalCharacteristics, totalResult, picked = picked, paired = false)
        }

        val physicalTotal = physicalTotals?.get(picked)
        return when {
            allowPhysicalTotalResultPairing && physicalTotal != null -> {
                Log.d(TAG, "dng metadata: physical id=$picked (TotalCaptureResult map ok)")
                resolution(physicalChars, physicalTotal, picked = picked, paired = true)
            }
            physicalTotal != null -> {
                Log.w(
                    TAG,
                    "dng metadata: physical id=$picked TotalCaptureResult map has entry but " +
                        "allowPhysicalTotalResultPairing=false (logical RAW stream) — " +
                        "using logical characteristics + logical TotalCaptureResult",
                )
                resolution(logicalCharacteristics, totalResult, picked = picked, paired = false)
            }
            else -> {
                Log.w(
                    TAG,
                    "dng metadata: physical id=$picked but physicalCameraTotalResults missing " +
                        "(API=${Build.VERSION.SDK_INT} mapKeys=${physicalTotals?.keys}); " +
                        "fallback logical characteristics + logical TotalCaptureResult " +
                        "(avoid physical chars + logical result hybrid)",
                )
                resolution(logicalCharacteristics, totalResult, picked = picked, paired = false)
            }
        }
    }
}
