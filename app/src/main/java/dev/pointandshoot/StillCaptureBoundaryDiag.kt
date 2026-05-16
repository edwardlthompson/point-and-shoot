package dev.pointandshoot

import android.graphics.Rect
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.util.Log
import kotlin.math.abs

/**
 * Debuggable-build diagnostics for preview→still boundary: timing around [stopRepeating], metadata
 * deltas, and a compact hypothesis hint for fleet triage (`PNS.StillBoundary`).
 *
 * See still-capture AF / softness audit plan: compare last repeating preview result vs still
 * [TotalCaptureResult], not generic Camera2 theory.
 *
 * **Interpreting `hint=` (triage gate):**
 * - `isp_mode_delta` — edge / NR / tonemap / color correction differ between last preview sample and
 *   still result; prioritize [RawStillProcessingHints] / [PreviewJpegProcessingHints] vs preview path.
 * - `eis_delta` — preview EIS vs still (still path skips EIS in [PreviewStabilization]); framing / motion.
 * - `af_state_changed`, `lens_state_changed`, `focus_distance_jump` — prioritize AF lock / precapture
 *   experiments on USB with [scripts/pns_photo_capture_verify.ps1] (not parallel with chrome UX gate).
 * - `face_detect_mode_delta` — consider mirroring [applyFaceDetectMode] on still requests if HAL drops stats.
 * - `stable_subset` — AF/lens subset stable; if output still soft, lean toward ISP, stopRepeating hitch,
 *   or display vs file pipeline rather than lens move.
 */
object StillCaptureBoundaryDiag {
    const val TAG = "PNS.StillBoundary"

    data class Snapshot(
        val wallElapsedMs: Long,
        val frameNumber: Long,
        val afMode: Int?,
        val afState: Int?,
        val afRegions: String,
        val aeState: Int?,
        val aeLocked: Boolean?,
        val lensState: Int?,
        val lensFocusDistance: Float?,
        val edgeMode: Int?,
        val nrMode: Int?,
        val tonemapMode: Int?,
        val colorCorrectionMode: Int?,
        val videoStabilizationMode: Int?,
        val scalerCrop: String,
        val sensitivity: Int?,
        val exposureNs: Long?,
        val statisticsFaceDetectMode: Int?,
    ) {
        companion object {
            fun from(r: CaptureResult): Snapshot {
                val crop = r.get(CaptureResult.SCALER_CROP_REGION)
                return Snapshot(
                    wallElapsedMs = android.os.SystemClock.elapsedRealtime(),
                    frameNumber = r.frameNumber,
                    afMode = r.get(CaptureResult.CONTROL_AF_MODE),
                    afState = r.get(CaptureResult.CONTROL_AF_STATE),
                    afRegions = summarizeRegions(r.get(CaptureResult.CONTROL_AF_REGIONS)),
                    aeState = r.get(CaptureResult.CONTROL_AE_STATE),
                    aeLocked = r.get(CaptureResult.CONTROL_AE_LOCK),
                    lensState = r.get(CaptureResult.LENS_STATE),
                    lensFocusDistance = r.get(CaptureResult.LENS_FOCUS_DISTANCE),
                    edgeMode = r.get(CaptureResult.EDGE_MODE),
                    nrMode = r.get(CaptureResult.NOISE_REDUCTION_MODE),
                    tonemapMode = r.get(CaptureResult.TONEMAP_MODE),
                    colorCorrectionMode = r.get(CaptureResult.COLOR_CORRECTION_MODE),
                    videoStabilizationMode = r.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE),
                    scalerCrop = crop?.let { "${it.left},${it.top}-${it.right},${it.bottom}" } ?: "-",
                    sensitivity = r.get(CaptureResult.SENSOR_SENSITIVITY),
                    exposureNs = r.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                    statisticsFaceDetectMode = r.get(CaptureResult.STATISTICS_FACE_DETECT_MODE),
                )
            }
        }
    }

    data class Timings(
        var tStopRepeatingNs: Long = 0L,
        var tFireStillCaptureNs: Long = 0L,
        var tOnCaptureCompletedNs: Long = 0L,
        var tAfterResumeRepeatingNs: Long = 0L,
    )

    internal fun summarizeRegions(regions: Array<MeteringRectangle>?): String {
        if (regions.isNullOrEmpty()) return "-"
        val r = regions[0]
        return "${r.x},${r.y},${r.width}x${r.height},w=${r.meteringWeight}"
    }

    internal fun requestKeyLine(req: CaptureRequest): String =
        buildString {
            append("reqAfMode=").append(req.get(CaptureRequest.CONTROL_AF_MODE)?.toString() ?: "?")
            append(" reqAfTrigger=").append(req.get(CaptureRequest.CONTROL_AF_TRIGGER)?.toString() ?: "?")
            append(" reqAfLock=").append(StillCaptureAfFreeze.readRequestAfLockForDiag(req))
            append(" reqAeLock=").append(req.get(CaptureRequest.CONTROL_AE_LOCK)?.toString() ?: "?")
            append(" reqEdge=").append(req.get(CaptureRequest.EDGE_MODE)?.toString() ?: "?")
            append(" reqNr=").append(req.get(CaptureRequest.NOISE_REDUCTION_MODE)?.toString() ?: "?")
            append(" reqEis=").append(req.get(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE)?.toString() ?: "?")
        }

    internal fun hypothesisHint(preview: Snapshot?, still: Snapshot): String {
        if (preview == null) return "no_preview_snapshot"
        val hints = ArrayList<String>(6)
        if (preview.edgeMode != still.edgeMode ||
            preview.nrMode != still.nrMode ||
            preview.tonemapMode != still.tonemapMode ||
            preview.colorCorrectionMode != still.colorCorrectionMode
        ) {
            hints.add("isp_mode_delta")
        }
        if (preview.videoStabilizationMode != still.videoStabilizationMode) {
            hints.add("eis_delta")
        }
        if (preview.afState != still.afState) {
            hints.add("af_state_changed")
        }
        if (preview.lensState != still.lensState) {
            hints.add("lens_state_changed")
        }
        val pf = preview.lensFocusDistance
        val sf = still.lensFocusDistance
        if (pf != null && sf != null && abs(pf - sf) > 0.02f) {
            hints.add("focus_distance_jump")
        }
        if (preview.statisticsFaceDetectMode != still.statisticsFaceDetectMode) {
            hints.add("face_detect_mode_delta")
        }
        return if (hints.isEmpty()) "stable_subset" else hints.joinToString(",")
    }

    fun logBoundary(
        kind: String,
        label: String?,
        previewAtStop: Snapshot?,
        stillResult: TotalCaptureResult,
        stillRequest: CaptureRequest,
        timings: Timings,
    ) {
        val stillSnap = Snapshot.from(stillResult)
        val gapStopToFireMs =
            if (timings.tStopRepeatingNs > 0L && timings.tFireStillCaptureNs >= timings.tStopRepeatingNs) {
                (timings.tFireStillCaptureNs - timings.tStopRepeatingNs) / 1_000_000L
            } else {
                -1L
            }
        val gapFireToCompleteMs =
            if (timings.tFireStillCaptureNs > 0L && timings.tOnCaptureCompletedNs >= timings.tFireStillCaptureNs) {
                (timings.tOnCaptureCompletedNs - timings.tFireStillCaptureNs) / 1_000_000L
            } else {
                -1L
            }
        val gapCompleteToResumeMs =
            if (timings.tOnCaptureCompletedNs > 0L && timings.tAfterResumeRepeatingNs >= timings.tOnCaptureCompletedNs) {
                (timings.tAfterResumeRepeatingNs - timings.tOnCaptureCompletedNs) / 1_000_000L
            } else {
                -1L
            }
        val hint = hypothesisHint(previewAtStop, stillSnap)
        val pv = previewAtStop
        Log.i(
            TAG,
            buildString {
                append("stillBoundary kind=").append(kind)
                append(" label=").append(label ?: "-")
                append(" hint=").append(hint)
                append(" gapMs stopToFire=").append(gapStopToFireMs)
                append(" fireToComplete=").append(gapFireToCompleteMs)
                append(" completeToResume=").append(gapCompleteToResumeMs)
                append(" | ")
                append(requestKeyLine(stillRequest))
                append(" afFreezeDiag=").append(StillCaptureAfFreeze.consumeLastBoundaryDiag() ?: "-")
                append(" | ")
                if (pv == null) {
                    append("preview=-")
                } else {
                    append("previewFn=").append(pv.frameNumber)
                    append(" af=").append(pv.afMode).append("/").append(pv.afState)
                    append(" lens=").append(pv.lensState).append("/fd=").append(pv.lensFocusDistance)
                    append(" edge=").append(pv.edgeMode).append(" nr=").append(pv.nrMode)
                    append(" eis=").append(pv.videoStabilizationMode)
                    append(" crop=").append(pv.scalerCrop)
                }
                append(" | stillFn=").append(stillSnap.frameNumber)
                append(" af=").append(stillSnap.afMode).append("/").append(stillSnap.afState)
                append(" lens=").append(stillSnap.lensState).append("/fd=").append(stillSnap.lensFocusDistance)
                append(" edge=").append(stillSnap.edgeMode).append(" nr=").append(stillSnap.nrMode)
                append(" eis=").append(stillSnap.videoStabilizationMode)
                append(" aeLock=").append(stillSnap.aeLocked)
                append(" iso=").append(stillSnap.sensitivity)
                append(" ssNs=").append(stillSnap.exposureNs)
                append(" crop=").append(stillSnap.scalerCrop)
                append(" faceMode=").append(stillSnap.statisticsFaceDetectMode)
            },
        )
    }
}
